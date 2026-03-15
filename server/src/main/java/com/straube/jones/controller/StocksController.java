package com.straube.jones.controller;


import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.straube.jones.dataprovider.stocks.PricePointLoader;
import com.straube.jones.dataprovider.stocks.StockItem;
import com.straube.jones.dataprovider.stocks.StockPointLoader;
import com.straube.jones.dataprovider.stocks.StocksLoader;
import com.straube.jones.dataprovider.yahoo.SymbolResolver;
import com.straube.jones.db.DBConnection;
import com.straube.jones.dto.IntradayResponse;
import com.straube.jones.dto.LastSharePriceResponse;
import com.straube.jones.dto.OnVistaReportResponse;
import com.straube.jones.dto.PriceTickerErrorResponse;
import com.straube.jones.dto.ServiceInfoResponse;
import com.straube.jones.dto.ShareSearchResponse;
import com.straube.jones.dto.TableDataResponse;
import com.straube.jones.dto.TablePriceDataResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(value = "/api/stocks")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Stocks API", description = "Comprehensive stock market data API for financial analysis, portfolio management, and investment decision support. Provides real-time and historical stock data, technical analysis tools, and user preference management.")
public class StocksController
{
    private static final String DATA_ROOT_FOLDER = System.getProperty("data.root",
                                                                      "/home/mark/Software/data");
    private static final String FUNDAMENTALS_ROOT_FOLDER = DATA_ROOT_FOLDER + "/onVista/fundamentals/cache/";
    static
    {
        new File(FUNDAMENTALS_ROOT_FOLDER).mkdirs();
    }

    @Operation(summary = "Get Service Information", description = "**Use Case:** Health check and service discovery. Returns metadata about the stocks API service including version, status, and available features. **When to use:** To verify service availability, check API version compatibility, or during system monitoring and diagnostics.")
    @ApiResponse(responseCode = "200", description = "Service metadata and health status")
    @GetMapping(value = "/", produces = "application/json")
    public ServiceInfoResponse index()
    {
        return new ServiceInfoResponse();
    }


    @Operation(summary = "Retrieve OnVista Financial Report", description = "**Use Case:** Access detailed fundamental analysis and financial metrics from OnVista platform. Returns comprehensive company financials, ratios, and analysis data. **When to use:** For fundamental stock analysis, company valuation, financial health assessment, or when detailed financial metrics are needed for investment decisions. **Input:** OnVista short URL identifier (e.g., 'xyz-123' format).")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Financial report data successfully retrieved", content = @Content(schema = @Schema(implementation = OnVistaReportResponse.class))),
                           @ApiResponse(responseCode = "404", description = "Report not found - invalid short URL or data not available", content = @Content(schema = @Schema(implementation = OnVistaReportResponse.class)))})
    @GetMapping(path = "/fundamentals/{short_url}", produces = "application/json")
    public OnVistaReportResponse getOnVistaReport(@Parameter(description = "OnVista short URL identifier (format: alphanumeric-number, e.g., 'apple-123')")
    @PathVariable("short_url")
    String shortUrl)
    {
        String[] segs = shortUrl.split("-");
        File htmlFile = new File(FUNDAMENTALS_ROOT_FOLDER, segs[segs.length - 1] + ".html");
        if (htmlFile.exists())
        {
            try
            {
                String html = new String(Files.readAllBytes(htmlFile.toPath()));
                final Document doc0 = Jsoup.parse(html, "UTF-8");
                final Element e0 = doc0.select("#__next > div.ov-content > div > section > div.col.col-12.inner-spacing--medium-top.ov-snapshot-tabs > div > section > div.col.grid.col--sm-4.col--md-8.col--lg-9.col--xl-9 > div:nth-child(2) > div > div > p")
                                       .first();
                if (e0 != null)
                { return OnVistaReportResponse.found(shortUrl, e0.text()); }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        return OnVistaReportResponse.notFound(shortUrl);
    }


    @Operation(summary = "Get Historical Stock Data", description = "**Use Case:** Technical analysis and quantitative research. Retrieves time-series price data for multiple stocks simultaneously. **When to use:** For portfolio analysis, backtesting strategies, correlation analysis, or building custom charts. **Input:** One or more ISIN codes. **Output:** Structured time-series data with timestamps and values. **Performance:** Optimized for bulk data retrieval and analysis workflows. **Default timeframe:** 6 months.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Time-series stock data successfully retrieved in tabular format", content = @Content(schema = @Schema(implementation = TableDataResponse.class)))})
    @GetMapping(path = "/data", produces = "application/json")
    public TableDataResponse getRawData(@Parameter(description = "List of ISIN codes (International Securities Identification Numbers, e.g., ['US0378331005', 'DE0007164600'])")
    @RequestParam
    List<String> isin,
                                        @Parameter(description = "Start timestamp in milliseconds (Unix epoch time). Defaults to 1 months ago for sufficient historical data.", schema = @Schema(type = "integer", format = "int64"))
                                        @RequestParam(value = "start_time", required = false)
                                        Long start,
                                        @Parameter(description = "Data type identifier (0=price data, 1=percentage development since start time). Defaults to 0 (price data).")
                                        @RequestParam(required = false)
                                        Integer type)
    {
        if (start == null)
        {
            start = System.currentTimeMillis() - 1 * 30 * 24 * 60 * 60 * 1000L; // ~1 Month back
        }
        if (type == null)
        {
            type = 0;
        }

        // TableData direkt laden und zurückgeben
        return StockPointLoader.loadRaw(isin, start, type);
    }


    @Operation(summary = "Get Historical Daily Stock Price Data and Volume", description = "**Use Case:** Technical analysis and quantitative research. Retrieves time-series price data for multiple stocks simultaneously. **When to use:** For portfolio analysis, backtesting strategies, correlation analysis, or building custom charts. **Input:** One or more ISIN codes. **Output:** Structured time-series data with timestamps and values. **Performance:** Optimized for bulk data retrieval and analysis workflows. **Default timeframe:** 6 months.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Time-series stock price data successfully retrieved in tabular format", content = @Content(schema = @Schema(implementation = TablePriceDataResponse.class)))})
    @GetMapping(path = "/prices", produces = "application/json")
    public TablePriceDataResponse getPriceData(@Parameter(description = "List of Yahoo symbol codes or ISIN codes (International Securities Identification Numbers, e.g., ['US0378331005', 'DE0007164600'])")
    @RequestParam
    List<String> codes,
                                               @Parameter(description = "Start timestamp in milliseconds (Unix epoch time). Defaults to 1 months ago for sufficient historical data.", schema = @Schema(type = "integer", format = "int64"))
                                               @RequestParam(value = "start_time", required = false)
                                               Long start,
                                               @Parameter(description = "End timestamp in milliseconds (Unix epoch time). Defaults to current time for sufficient historical data.", schema = @Schema(type = "integer", format = "int64"))
                                               @RequestParam(value = "end_time", required = false)
                                               Long end,
                                               @Parameter(description = "Data type identifier (0=price data, 1=percentage development since start time, 2=percentage development since previous price). Defaults to 0 (price data).")
                                               @RequestParam(required = false)
                                               Integer type)
    {
        if (start == null)
        {
            start = System.currentTimeMillis() - 1 * 30 * 24 * 60 * 60 * 1000L; // ~1 Month back
        }
        if (end == null)
        {
            end = System.currentTimeMillis(); // Current time
        }
        if (type == null)
        {
            type = 0;
        }

        // TableData direkt laden und zurückgeben
        return PricePointLoader.loadPrices(codes, start, end, type, true);
    }


    @Operation(summary = "Get Single Stock Item", description = "**Use Case:** Retrieve detailed information for a specific stock by ISIN. Returns a single StockItem with comprehensive metadata. **When to use:** For displaying stock details, getting stock information for a known ISIN, or retrieving metadata for a specific security. **Performance:** Fast single-record lookup optimized for detail views.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Stock item successfully retrieved", content = @Content(schema = @Schema(implementation = StockItem.class))),
                           @ApiResponse(responseCode = "404", description = "Stock not found for the given ISIN")})
    @GetMapping(path = "/item", produces = "application/json")
    public ResponseEntity<StockItem> getStockItem(@Parameter(description = "ISIN code of the stock to retrieve (e.g., 'US0378331005' for Apple)")
    @RequestParam
    String isin)
    {
        List<String> isinList = List.of(isin);
        Map<String, List<StockItem>> result = StocksLoader.load(isinList);
        List<StockItem> items = result.get("stockItems");

        if (items != null && !items.isEmpty())
        {
            return ResponseEntity.ok(items.get(0));
        }
        else
        {
            return ResponseEntity.notFound().build();
        }
    }


    @Operation(summary = "Get Stock Universe Catalog", description = "**Use Case:** Stock discovery and universe definition. Returns comprehensive catalog of all available stocks with metadata. **When to use:** For portfolio construction, stock screening, building watchlists, or discovering investment opportunities. **Data structure:** Organized by categories with detailed stock information including symbols, names, sectors, and identifiers. **Performance note:** Large dataset - cache results when possible.")
    @ApiResponse(responseCode = "200", description = "Complete stock catalog with metadata organized by categories", content = @Content(schema = @Schema(implementation = Map.class)))
    @GetMapping(path = "/items", produces = "application/json")
    public Map<String, List<StockItem>> getStockItems()
    {
        return StocksLoader.load();
    }


    @Operation(summary = "Get Last Share Price", description = "**Use Case:** Real-time price monitoring and current valuation. Retrieves the most recent known price for a specific stock. **When to use:** For current portfolio valuation, price alerts, real-time trading decisions, or displaying current market values. **Data source:** Uses the latest available price data from stock history. **Performance:** Fast lookup optimized for single stock queries.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Last share price successfully retrieved", content = @Content(schema = @Schema(implementation = LastSharePriceResponse.class))),
                           @ApiResponse(responseCode = "404", description = "No price data found for the given ISIN", content = @Content(schema = @Schema(implementation = LastSharePriceResponse.class))),
                           @ApiResponse(responseCode = "500", description = "Internal server error during price lookup", content = @Content(schema = @Schema(implementation = LastSharePriceResponse.class)))})
    @GetMapping(path = "/price/last", produces = "application/json")
    public LastSharePriceResponse getLastSharePrice(@Parameter(description = "ISIN code of the stock to get the last price for")
    @RequestParam
    String isin)
    {
        try
        {
            // Get the latest available data for this ISIN (last 30 days should be sufficient)
            long startTime = System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L; // 30 days back
            List<String> isinList = List.of(isin);

            TableDataResponse data = StockPointLoader.loadRaw(isinList, startTime, 0); // type 0 = price data

            if (data.getRows().isEmpty())
            { return LastSharePriceResponse.notFound(isin); }

            // Find the row with the latest timestamp
            TableDataResponse.TableRow latestRow = null;
            long latestTimestamp = 0;

            for (TableDataResponse.TableRow row : data.getRows())
            {
                if (row.getDateLong() != null && row.getDateLong() > latestTimestamp)
                {
                    latestTimestamp = row.getDateLong();
                    latestRow = row;
                }
            }

            if (latestRow == null || latestRow.getValue() == null)
            { return LastSharePriceResponse.notFound(isin); }

            return LastSharePriceResponse.success(isin,
                                                  latestRow.getValue(),
                                                  latestRow.getDateLong(),
                                                  latestRow.getDate());
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return LastSharePriceResponse.error(isin,
                                                "Failed to retrieve last share price: " + e.getMessage());
        }
    }


    @Operation(summary = "Search Stocks by Name", description = "**Use Case:** Stock discovery through fuzzy name matching. Finds stocks using tolerant similarity search that ignores special characters, whitespaces, and various dash types. **When to use:** For stock lookup when exact names are unknown, user input validation, or building search suggestions. **Algorithm:** Normalized string comparison with similarity scoring. **Results:** Multiple matches possible, sorted by relevance score.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Search completed successfully, results include similarity scores", content = @Content(schema = @Schema(implementation = ShareSearchResponse.class))),
                           @ApiResponse(responseCode = "500", description = "Search operation failed - check system availability", content = @Content(schema = @Schema(implementation = ShareSearchResponse.class)))})
    @GetMapping(path = "/search/name", produces = "application/json")
    public ShareSearchResponse getShareForName(@Parameter(description = "Stock name or partial name to search for (tolerant matching - ignores special characters, spaces, dashes)")
    @RequestParam
    String name)
    {
        try
        {
            // Get all available stocks
            Map<String, List<StockItem>> allStocks = StocksLoader.load();
            List<StockItem> stockList = new ArrayList<>();

            // Flatten all stock categories into one list
            for (List<StockItem> stocks : allStocks.values())
            {
                stockList.addAll(stocks);
            }

            // Normalize search query
            String normalizedQuery = normalizeString(name);

            if (normalizedQuery.isEmpty())
            { return ShareSearchResponse.success(name, new ArrayList<>()); }

            // Find matching stocks with similarity scores
            List<ShareSearchResponse.StockMatch> matches = stockList.stream()
                                                                    .filter(stock -> stock.getName() != null
                                                                                    && !stock.getName()
                                                                                             .trim()
                                                                                             .isEmpty())
                                                                    .map(stock -> {
                                                                        String normalizedStockName = normalizeString(stock.getName());
                                                                        double similarity = calculateSimilarity(normalizedQuery,
                                                                                                                normalizedStockName);
                                                                        return new ShareSearchResponse.StockMatch(stock,
                                                                                                                  similarity);
                                                                    })
                                                                    .filter(match -> match.getSimilarityScore() > 0.3) // Only
                                                                                                                       // include
                                                                                                                       // matches
                                                                                                                       // with
                                                                                                                       // >
                                                                                                                       // 30%
                                                                                                                       // similarity
                                                                    .sorted(Comparator.comparing(ShareSearchResponse.StockMatch::getSimilarityScore)
                                                                                      .reversed())
                                                                    .limit(20) // Limit to top 20 matches
                                                                    .collect(Collectors.toList());

            return ShareSearchResponse.success(name, matches);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return ShareSearchResponse.error(name, "Failed to search stocks: " + e.getMessage());
        }
    }


    /**
     * Normalizes a string for tolerant comparison by removing special characters, whitespaces, and various
     * types of dashes, then converting to lowercase.
     */
    private String normalizeString(String input)
    {
        if (input == null)
        { return ""; }

        // Convert to lowercase and remove all special characters, whitespaces, and dashes
        return input.toLowerCase()
                    .replaceAll("[\\s\\-\\u2010\\u2011\\u2012\\u2013\\u2014\\u2015\\u2212]", "") // Remove
                                                                                                 // spaces and
                                                                                                 // all dash
                                                                                                 // types
                    .replaceAll("[^a-z0-9]", ""); // Remove all non-alphanumeric characters
    }


    /**
     * Calculates similarity between two normalized strings using a combination of exact match, contains
     * check, and Levenshtein-like distance.
     */
    private double calculateSimilarity(String query, String target)
    {
        if (query.equals(target))
        {
            return 1.0; // Exact match
        }

        if (target.contains(query))
        {
            return 0.9; // Query is contained in target
        }

        if (query.contains(target))
        {
            return 0.8; // Target is contained in query
        }

        // Check for partial matches (at least 3 characters)
        if (query.length() >= 3 && target.length() >= 3)
        {
            int maxLength = Math.max(query.length(), target.length());
            int commonChars = 0;

            // Count common characters
            for (int i = 0; i < Math.min(query.length(), target.length()); i++ )
            {
                if (query.charAt(i) == target.charAt(i))
                {
                    commonChars++ ;
                }
                else
                {
                    break; // Stop at first non-matching character from start
                }
            }

            // Check for common substrings
            for (int len = 3; len <= Math.min(query.length(), target.length()); len++ )
            {
                for (int i = 0; i <= query.length() - len; i++ )
                {
                    String substring = query.substring(i, i + len);
                    if (target.contains(substring))
                    {
                        commonChars = Math.max(commonChars, len);
                    }
                }
            }

            double similarity = (double)commonChars / maxLength;
            return similarity > 0.3 ? similarity : 0.0;
        }

        return 0.0; // No meaningful similarity
    }


    @Operation(summary = "Generate Stock Chart Image", description = "**Use Case:** Visual chart generation for reports, dashboards, or quick visual analysis. Returns pre-generated PNG chart images for specific stocks and timeframes. **When to use:** For embedding charts in documents, creating visual reports, thumbnail generation, or when lightweight image-based charts are preferred over interactive charts. **Customization:** Configurable dimensions and time periods. **Performance:** Fast delivery of cached chart images.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Chart image successfully generated and returned as PNG", content = @Content(mediaType = "image/png")),
                           @ApiResponse(responseCode = "404", description = "Chart not available - invalid ISIN or image not generated yet"),
                           @ApiResponse(responseCode = "500", description = "Image generation or file system error")})
    @GetMapping(path = "/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getStockImage(@Parameter(description = "ISIN code of the stock for chart generation")
    @RequestParam
    String isin,
                                                @Parameter(description = "Start timestamp in milliseconds for chart time range. Optional - uses reasonable default if not provided.", schema = @Schema(type = "integer", format = "int64"))
                                                @RequestParam(value = "start_time", required = false)
                                                Long start,
                                                @Parameter(description = "End timestamp in milliseconds for chart time range. Optional - uses current time if not provided.", schema = @Schema(type = "integer", format = "int64"))
                                                @RequestParam(value = "end_time", required = false)
                                                Long end,
                                                @Parameter(description = "Chart image width in pixels. Default: 64px. Affects image file size and detail level.")
                                                @RequestParam(required = false, defaultValue = "64")
                                                Integer width,
                                                @Parameter(description = "Chart image height in pixels. Default: 48px. Affects image file size and detail level.")
                                                @RequestParam(required = false, defaultValue = "48")
                                                Integer height,
                                                @Parameter(description = "Time period path for chart data ('365'=1 year, '28'=1 month, '1M'=1 month, '1Y'=1 year). Determines data granularity and time span.")
                                                @RequestParam(required = false, defaultValue = "365")
                                                String path)
    {
        try
        {
            String dir = String.format("%s/%sx%s/%s.png", path, width, height, isin);
            File imageFile = new File(DATA_ROOT_FOLDER, dir);

            if (!imageFile.exists())
            { return ResponseEntity.notFound().build(); }
            byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(imageBytes);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }


    @Operation(summary = "Add or Update Stock", description = "**Use Case:** Add a new stock to the database or update an existing one. **When to use:** When you have new stock data to persist. **Input:** A StockItem object in the request body. **Output:** Success message.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Stock successfully added or updated", content = @Content(schema = @Schema(type = "string", example = "Stock updated successfully"))),
                           @ApiResponse(responseCode = "500", description = "Database error", content = @Content(schema = @Schema(type = "string", example = "Database error: ...")))})
    @PostMapping(path = "/add", consumes = "application/json", produces = "application/json")
    @PreAuthorize("hasAuthority('PORTFOLIO_EXECUTE_ADD') or hasAuthority('PORTFOLIO_CREATE')")
    public ResponseEntity<String> addStock(@Parameter(description = "StockItem object containing stock details", required = true, schema = @Schema(implementation = StockItem.class))
    @RequestBody
    StockItem stockItem)
    {

        String checkQuery = "SELECT count(*) FROM tOnVista WHERE cIsin = ?";
        String insertQuery = "INSERT INTO tOnVista (cIsin, cName, cCountryCode, cMarketCapitalization, cBranch, cPerf4Weeks, cPerf6Months, cPerf1Year, cLast, cCurrency, cDividendYield, cTurnover) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String updateQuery = "UPDATE tOnVista SET cName=?, cCountryCode=?, cMarketCapitalization=?, cBranch=?, cPerf4Weeks=?, cPerf6Months=?, cPerf1Year=?, cLast=?, cCurrency=?, cDividendYield=?, cTurnover=? WHERE cIsin=?";

        try (Connection connection = DBConnection.getStocksConnection())
        {
            boolean exists = false;
            try (PreparedStatement checkPs = connection.prepareStatement(checkQuery))
            {
                checkPs.setString(1, stockItem.getISIN());
                try (ResultSet rs = checkPs.executeQuery())
                {
                    if (rs.next() && rs.getInt(1) > 0)
                    {
                        exists = true;
                    }
                }
            }

            if (exists)
            {
                try (PreparedStatement updatePs = connection.prepareStatement(updateQuery))
                {
                    updatePs.setString(1, stockItem.getName());
                    updatePs.setString(2, stockItem.getCountryCode());
                    updatePs.setDouble(3,
                                       stockItem.getCapitalization() != null ? stockItem.getCapitalization()
                                                       : 0.0);
                    updatePs.setString(4, stockItem.getIndustry());
                    updatePs.setDouble(5, parseDoubleSafe(stockItem.getPerf4()));
                    updatePs.setDouble(6, parseDoubleSafe(stockItem.getPerf26()));
                    updatePs.setDouble(7, parseDoubleSafe(stockItem.getPerf52()));
                    updatePs.setDouble(8, parseDoubleSafe(stockItem.getLast()));
                    updatePs.setString(9, stockItem.getCurrency());
                    updatePs.setDouble(10,
                                       stockItem.getDividendYield() != null ? stockItem.getDividendYield()
                                                       : 0.0);
                    updatePs.setDouble(11, stockItem.getTurnover() != null ? stockItem.getTurnover() : 0.0);
                    updatePs.setString(12, stockItem.getISIN());
                    updatePs.executeUpdate();
                }
                connection.commit();
                return ResponseEntity.ok("Stock updated successfully");
            }
            else
            {
                try (PreparedStatement insertPs = connection.prepareStatement(insertQuery))
                {
                    insertPs.setString(1, stockItem.getISIN());
                    insertPs.setString(2, stockItem.getName());
                    insertPs.setString(3, stockItem.getCountryCode());
                    insertPs.setDouble(4,
                                       stockItem.getCapitalization() != null ? stockItem.getCapitalization()
                                                       : 0.0);
                    insertPs.setString(5, stockItem.getIndustry());
                    insertPs.setDouble(6, parseDoubleSafe(stockItem.getPerf4()));
                    insertPs.setDouble(7, parseDoubleSafe(stockItem.getPerf26()));
                    insertPs.setDouble(8, parseDoubleSafe(stockItem.getPerf52()));
                    insertPs.setDouble(9, parseDoubleSafe(stockItem.getLast()));
                    insertPs.setString(10, stockItem.getCurrency());
                    insertPs.setDouble(11,
                                       stockItem.getDividendYield() != null ? stockItem.getDividendYield()
                                                       : 0.0);
                    insertPs.setDouble(12, stockItem.getTurnover() != null ? stockItem.getTurnover() : 0.0);
                    insertPs.executeUpdate();
                }
                connection.commit();
                return ResponseEntity.ok("Stock added successfully");
            }
        }
        catch (SQLException e)
        {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Database error: " + e.getMessage());
        }
    }


    /**
     * Retrieves intraday price data for a stock from the {@code tTradegateIntraday} table.
     *
     * <p>The endpoint returns all price snapshots recorded for the calendar day that
     * corresponds to the supplied {@code timestamp} (evaluated in Central European time,
     * i.e. {@code Europe/Berlin}).  The day window covers exactly 24 hours, from midnight
     * to midnight local time.
     *
     * <p><b>Column renaming:</b> {@code cStueck} is exposed as {@code volume},
     * {@code cUmsatz} as {@code revenue}.
     *
     * <p><b>Optional reduction:</b> When the {@code reduce} parameter is provided, multiple
     * raw data points that fall within the same time bucket are aggregated into one:
     * <ul>
     *   <li>All numeric price / value fields become the arithmetic mean of the bucket.</li>
     *   <li>The {@code timestamp} of the aggregated point is the arithmetic mean of the
     *       contained timestamps, rounded to the nearest second.</li>
     * </ul>
     *
     * <p><b>Response structure:</b>
     * <pre>
     * {
     *   "isin": "US0378331005",
     *   "date": "2026-03-15",
     *   "reduce": "1M",          // absent when not requested
     *   "header": {
     *     "open":             150.25,   // first cLast of the day
     *     "close":            152.50,   // last  cLast of the day
     *     "high":             153.00,   // max   cLast of the day
     *     "high-timestamp":   ...,      // timestamp of max cLast
     *     "low":              149.50,   // min   cLast of the day
     *     "low-timestamp":    ...,      // timestamp of min cLast
     *     "previous-close":   151.00,   // cClose of last record (previous day close)
     *     "volume":           5000000,  // cStueck of last record (cumulative)
     *     "executions":       12500,    // cExecutions of last record (cumulative)
     *     "delta":            1.50      // cDelta  of last record
     *   },
     *   "data": [
     *     { "timestamp": ..., "last": ..., "bid": ..., "ask": ...,
     *       "avg": ..., "volume": ..., "revenue": ..., "delta": ... },
     *     ...
     *   ]
     * }
     * </pre>
     *
     * @param code      ISIN (e.g. {@code US0378331005}) or Yahoo Finance symbol
     *                  (e.g. {@code AAPL}).  Symbols are resolved to ISIN via
     *                  {@link SymbolResolver#resolveIsin(String)}.
     * @param timestamp Unix time in milliseconds.  Used only to identify the calendar day;
     *                  the exact time within the day is irrelevant.
     * @param reduce    Optional time-bucket size for data aggregation.  Supported values:
     *                  {@code 1S} (1 second), {@code 10S} (10 seconds), {@code 30S}
     *                  (30 seconds), {@code 1M} (1 minute), {@code 5M} (5 minutes),
     *                  {@code 10M} (10 minutes).  Omit to receive raw data.
     * @return {@code 200 OK} with an {@link IntradayResponse} on success;
     *         {@code 400 Bad Request} for missing/invalid parameters;
     *         {@code 404 Not Found} when the code cannot be resolved to an ISIN;
     *         {@code 500 Internal Server Error} on unexpected failures.
     */
    @GetMapping(path = "/intraday", produces = "application/json")
    @Operation(
        summary = "Get intraday price data for a single trading day",
        description = "Returns all price snapshots stored in tTradegateIntraday for the calendar day "
                    + "identified by the supplied timestamp (Europe/Berlin timezone, 24 h window). "
                    + "The optional **reduce** parameter aggregates raw snapshots into fixed-size time buckets "
                    + "(arithmetic mean per bucket, timestamp rounded to nearest second). "
                    + "The response contains a **header** with pre-calculated day statistics "
                    + "(open, close, high/low with timestamps, previous-close, cumulative volume, "
                    + "executions, delta) and a chronologically ordered **data** array suitable "
                    + "for direct use in chart libraries."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200",
                     description = "Intraday data successfully retrieved",
                     content = @Content(schema = @Schema(implementation = IntradayResponse.class))),
        @ApiResponse(responseCode = "400",
                     description = "Missing or invalid parameter (code, timestamp, or reduce value)",
                     content = @Content(schema = @Schema(implementation = PriceTickerErrorResponse.class))),
        @ApiResponse(responseCode = "404",
                     description = "Code cannot be resolved to a known ISIN",
                     content = @Content(schema = @Schema(implementation = PriceTickerErrorResponse.class))),
        @ApiResponse(responseCode = "500",
                     description = "Unexpected error during processing",
                     content = @Content(schema = @Schema(implementation = PriceTickerErrorResponse.class)))
    })
    public ResponseEntity<?> getIntraday(
        @Parameter(description = "ISIN (e.g. US0378331005) or Yahoo Finance symbol (e.g. AAPL)",
                   required = true, example = "US0378331005")
        @RequestParam(required = true) String code,

        @Parameter(description = "Unix timestamp in milliseconds identifying the trading day. "
                               + "Only the calendar date portion is used; the exact time is irrelevant.",
                   required = true, schema = @Schema(type = "integer", format = "int64"))
        @RequestParam(required = true) Long timestamp,

        @Parameter(description = "Optional aggregation bucket size. "
                               + "Supported values: 1S, 10S, 30S, 1M, 5M, 10M "
                               + "(S = seconds, M = minutes). "
                               + "When set, raw snapshots within each bucket are averaged and the "
                               + "bucket's timestamp is the rounded mean of its contained timestamps.",
                   required = false, example = "1M")
        @RequestParam(required = false) String reduce)
    {
        // ---- 1. Validate & parse the 'reduce' parameter ----------------------
        int bucketSeconds = 0;
        if (reduce != null && !reduce.trim().isEmpty())
        {
            bucketSeconds = parseReduceSeconds(reduce.trim());
            if (bucketSeconds < 0)
            {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                     .body(PriceTickerErrorResponse.create(
                                         "INVALID_REDUCE",
                                         "Invalid reduce parameter",
                                         "Supported values: 1S, 10S, 30S, 1M, 5M, 10M"));
            }
        }

        // ---- 2. Validate 'code' and resolve to ISIN --------------------------
        if (code == null || code.trim().isEmpty())
        {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                 .body(PriceTickerErrorResponse.create(
                                     "INVALID_CODE",
                                     "Missing or empty code parameter",
                                     "The 'code' query parameter is required"));
        }

        String isin;
        try
        {
            isin = SymbolResolver.resolveIsin(code.trim());
        }
        catch (IllegalArgumentException e)
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                 .body(PriceTickerErrorResponse.create(
                                     "ISIN_NOT_FOUND",
                                     "Code cannot be resolved to a known ISIN",
                                     e.getMessage()));
        }
        if (isin == null || isin.trim().isEmpty())
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                 .body(PriceTickerErrorResponse.create(
                                     "ISIN_NOT_FOUND",
                                     "Code cannot be resolved to a known ISIN",
                                     "No ISIN found for: " + code));
        }
        isin = isin.trim();

        // ---- 3. Compute day boundaries in Europe/Berlin local time -----------
        ZoneId zone = ZoneId.of("Europe/Berlin");
        LocalDate date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate();
        long dayStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli();
        long dayEndMs   = dayStartMs + 24L * 60 * 60 * 1000;

        // ---- 4. Query tTradegateIntraday -------------------------------------
        String sql = "SELECT cBid, cAsk, cDelta, cStueck, cUmsatz, cAvg, cExecutions, "
                   + "cLast, cClose, cTimestamp "
                   + "FROM tTradegateIntraday "
                   + "WHERE cIsin = ? AND cTimestamp >= ? AND cTimestamp < ? "
                   + "ORDER BY cTimestamp ASC";

        List<IntradayResponse.IntradayDataPoint> rawPoints = new ArrayList<>();

        // Header accumulation variables
        BigDecimal firstLast    = null;
        BigDecimal lastLast     = null;
        BigDecimal maxLast      = null;
        long       maxLastTs    = 0L;
        BigDecimal minLast      = null;
        long       minLastTs    = 0L;
        BigDecimal lastClose    = null;
        Long       lastVolume   = null;
        Integer    lastExec     = null;
        BigDecimal lastDelta    = null;

        try (Connection conn = DBConnection.getStocksConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, isin);
            ps.setTimestamp(2, new Timestamp(dayStartMs));
            ps.setTimestamp(3, new Timestamp(dayEndMs));

            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next())
                {
                    BigDecimal cLast  = rs.getBigDecimal("cLast");
                    long       tsMs   = rs.getTimestamp("cTimestamp").getTime();

                    // --- accumulate header statistics ---
                    if (firstLast == null)
                        firstLast = cLast;
                    lastLast  = cLast;
                    lastClose = rs.getBigDecimal("cClose");
                    lastVolume = rs.getLong("cStueck");
                    lastExec  = rs.getInt("cExecutions");
                    lastDelta = rs.getBigDecimal("cDelta");

                    if (cLast != null)
                    {
                        if (maxLast == null || cLast.compareTo(maxLast) > 0)
                        {
                            maxLast  = cLast;
                            maxLastTs = tsMs;
                        }
                        if (minLast == null || cLast.compareTo(minLast) < 0)
                        {
                            minLast  = cLast;
                            minLastTs = tsMs;
                        }
                    }

                    // --- build raw data point ---
                    IntradayResponse.IntradayDataPoint dp = new IntradayResponse.IntradayDataPoint();
                    dp.setTimestamp(tsMs);
                    dp.setLast(cLast);
                    dp.setBid(rs.getBigDecimal("cBid"));
                    dp.setAsk(rs.getBigDecimal("cAsk"));
                    dp.setAvg(rs.getBigDecimal("cAvg"));
                    dp.setVolume(rs.getLong("cStueck"));
                    dp.setRevenue(rs.getBigDecimal("cUmsatz"));
                    dp.setDelta(rs.getBigDecimal("cDelta"));
                    rawPoints.add(dp);
                }
            }
        }
        catch (SQLException e)
        {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(PriceTickerErrorResponse.create(
                                     "DB_ERROR",
                                     "Database error while reading intraday data",
                                     e.getMessage()));
        }

        // ---- 5. Optionally reduce / aggregate data points --------------------
        List<IntradayResponse.IntradayDataPoint> dataPoints =
            (bucketSeconds > 0 && !rawPoints.isEmpty())
                ? reduceIntradayPoints(rawPoints, bucketSeconds)
                : rawPoints;

        // ---- 6. Build header -------------------------------------------------
        IntradayResponse.IntradayHeader header = new IntradayResponse.IntradayHeader();
        header.setOpen(firstLast);
        header.setClose(lastLast);
        header.setHigh(maxLast);
        header.setHighTimestamp(maxLast != null ? maxLastTs : null);
        header.setLow(minLast);
        header.setLowTimestamp(minLast != null ? minLastTs : null);
        header.setPreviousClose(lastClose);
        header.setVolume(lastVolume);
        header.setExecutions(lastExec);
        header.setDelta(lastDelta);

        // ---- 7. Assemble and return response ---------------------------------
        IntradayResponse response = new IntradayResponse();
        response.setIsin(isin);
        response.setDate(date.toString());
        response.setReduce(bucketSeconds > 0 ? reduce.trim() : null);
        response.setHeader(header);
        response.setData(dataPoints);

        return ResponseEntity.ok(response);
    }


    /**
     * Parses a reduce parameter string into the corresponding bucket duration in seconds.
     *
     * @param reduce the reduce token (e.g. {@code "1M"})
     * @return positive number of seconds for valid tokens, {@code -1} for unknown tokens
     */
    private int parseReduceSeconds(String reduce)
    {
        switch (reduce)
        {
            case "1S":  return 1;
            case "10S": return 10;
            case "30S": return 30;
            case "1M":  return 60;
            case "5M":  return 300;
            case "10M": return 600;
            default:    return -1;
        }
    }


    /**
     * Aggregates a list of raw intraday data points into fixed-size time buckets.
     *
     * <p>Each bucket contains all points whose timestamp falls in the same
     * {@code [k * bucketMs, (k+1) * bucketMs)} interval.  The aggregated point for each
     * bucket has:
     * <ul>
     *   <li>{@code timestamp} = arithmetic mean of contained timestamps, rounded to the
     *       nearest second.</li>
     *   <li>All other numeric fields = arithmetic mean of the bucket's values.</li>
     * </ul>
     *
     * @param points        raw data ordered by timestamp (ascending)
     * @param bucketSeconds bucket width in seconds
     * @return aggregated list in chronological order
     */
    private List<IntradayResponse.IntradayDataPoint> reduceIntradayPoints(
        List<IntradayResponse.IntradayDataPoint> points, int bucketSeconds)
    {
        long bucketMs = bucketSeconds * 1000L;

        // LinkedHashMap preserves insertion order, which equals chronological bucket order
        // because the input is already sorted by timestamp.
        Map<Long, List<IntradayResponse.IntradayDataPoint>> buckets = new LinkedHashMap<>();
        for (IntradayResponse.IntradayDataPoint dp : points)
        {
            long bucketKey = (dp.getTimestamp() / bucketMs) * bucketMs;
            buckets.computeIfAbsent(bucketKey, k -> new ArrayList<>()).add(dp);
        }

        List<IntradayResponse.IntradayDataPoint> result = new ArrayList<>();
        for (List<IntradayResponse.IntradayDataPoint> group : buckets.values())
        {
            // Average timestamp → rounded to nearest second
            double avgTsD = group.stream()
                                 .mapToLong(IntradayResponse.IntradayDataPoint::getTimestamp)
                                 .average()
                                 .orElse(0);
            long avgTs = Math.round(avgTsD / 1000.0) * 1000L;

            IntradayResponse.IntradayDataPoint agg = new IntradayResponse.IntradayDataPoint();
            agg.setTimestamp(avgTs);
            agg.setLast(avgBigDecimal(group.stream()
                .map(IntradayResponse.IntradayDataPoint::getLast).collect(Collectors.toList())));
            agg.setBid(avgBigDecimal(group.stream()
                .map(IntradayResponse.IntradayDataPoint::getBid).collect(Collectors.toList())));
            agg.setAsk(avgBigDecimal(group.stream()
                .map(IntradayResponse.IntradayDataPoint::getAsk).collect(Collectors.toList())));
            agg.setAvg(avgBigDecimal(group.stream()
                .map(IntradayResponse.IntradayDataPoint::getAvg).collect(Collectors.toList())));
            agg.setRevenue(avgBigDecimal(group.stream()
                .map(IntradayResponse.IntradayDataPoint::getRevenue).collect(Collectors.toList())));
            agg.setDelta(avgBigDecimal(group.stream()
                .map(IntradayResponse.IntradayDataPoint::getDelta).collect(Collectors.toList())));

            // cStueck is a Long; round the average to the nearest whole number
            OptionalDouble avgVol = group.stream()
                .mapToLong(IntradayResponse.IntradayDataPoint::getVolume).average();
            agg.setVolume(avgVol.isPresent() ? Math.round(avgVol.getAsDouble()) : null);

            result.add(agg);
        }
        return result;
    }


    /**
     * Computes the arithmetic mean of a list of {@link BigDecimal} values, ignoring
     * {@code null} entries.  Returns {@code null} when the list is empty or contains only
     * {@code null} values.
     *
     * @param values list of values (may contain {@code null})
     * @return mean rounded to 6 decimal places, or {@code null}
     */
    private BigDecimal avgBigDecimal(List<BigDecimal> values)
    {
        List<BigDecimal> nonNull = values.stream()
                                         .filter(v -> v != null)
                                         .collect(Collectors.toList());
        if (nonNull.isEmpty())
            return null;
        BigDecimal sum = nonNull.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(nonNull.size()), 6, RoundingMode.HALF_UP);
    }


    private double parseDoubleSafe(String value)
    {
        if (value == null || value.trim().isEmpty())
        { return 0.0; }
        try
        {
            return Double.parseDouble(value.replace(",", "."));
        }
        catch (NumberFormatException e)
        {
            return 0.0;
        }
    }
}
