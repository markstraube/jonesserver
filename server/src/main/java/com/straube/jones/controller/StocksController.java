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
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.straube.jones.dto.OHLCResponse;
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
     * <p>When {@code timestamp} is <em>omitted</em>, the endpoint automatically determines
     * the most recent calendar day for which intraday data is present in the table for the
     * requested ISIN and returns that day's data.
     *
     * <p><b>Column renaming:</b> {@code cStueck} is exposed as {@code volume}.
     *
     * <p><b>Optional reduction:</b> When the {@code reduce} parameter is provided, raw
     * snapshots are grouped into minute-aligned OHLC candles.  Bucket boundaries are
     * aligned to the full hour (e.g. for {@code 10M}: 7:00–7:09:59, 7:10–7:19:59, …).
     * Within each bucket:
     * <ul>
     *   <li>{@code open} / {@code close} / {@code high} / {@code low} reflect the price
     *       development of {@code cLast} across all snapshots in the bucket.</li>
     *   <li>{@code bid}, {@code ask}, {@code avg} are arithmetic means.</li>
     *   <li>{@code volume} is {@code cStueck_newest − cStueck_oldest} (increment).</li>
     *   <li>{@code delta} is the {@code cDelta} of the newest snapshot.</li>
     *   <li>{@code timestamp} is the start of the bucket (oldest snapshot's timestamp).</li>
     * </ul>
     *
     * <p><b>Response structure:</b>
     * <pre>
     * {
     *   "isin": "US0378331005",
     *   "date": "2026-03-15",
     *   "reduce": "10M",         // absent when not requested
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
     *     "delta":            1.50,     // cDelta  of last record
     *     "buckets":          57        // number of data points in the data array
     *   },
     *   "data": [
     *     { "timestamp": ..., "open": ..., "close": ..., "high": ..., "low": ...,
     *       "bid": ..., "ask": ..., "avg": ..., "volume": ..., "delta": ... },
     *     ...
     *   ]
     * }
     * </pre>
     *
     * @param code      ISIN (e.g. {@code US0378331005}) or Yahoo Finance symbol
     *                  (e.g. {@code AAPL}).  Symbols are resolved to ISIN via
     *                  {@link SymbolResolver#resolveIsin(String)}.
     * @param timestamp Optional Unix time in milliseconds used only to identify the
     *                  calendar day (exact time within the day is irrelevant).
     *                  When omitted, the endpoint determines the most recent day for
     *                  which data is available in {@code tTradegateIntraday} for the
     *                  requested ISIN and returns that day's data.
     * @param reduce    Optional time-bucket size for OHLC candle aggregation.  Supported
     *                  values: {@code 1M} (1 minute), {@code 5M} (5 minutes), {@code 10M} (10 minutes),
     *                  {@code 30M} (30 minutes), {@code 60M} (60 minutes).
     *                  Bucket boundaries are aligned to the full hour.
     *                  Omit to receive raw snapshot data.
     * @return {@code 200 OK} with an {@link IntradayResponse} on success;
     *         {@code 400 Bad Request} for missing/invalid parameters;
     *         {@code 404 Not Found} when the code cannot be resolved to an ISIN or
     *         no intraday data exists for the ISIN;
     *         {@code 500 Internal Server Error} on unexpected failures.
     */
    @GetMapping(path = "/intraday", produces = "application/json")
    @Operation(
        summary = "Get intraday price data for a single trading day",
        description = "Returns all price snapshots stored in tTradegateIntraday for the calendar day "
                    + "identified by the optional **timestamp** parameter (Europe/Berlin timezone, 24 h window). "
                    + "**When timestamp is omitted**, the endpoint automatically determines the most recent day "
                    + "for which intraday data is available in the database for the requested ISIN and returns that day's data. "
                    + "The optional **reduce** parameter aggregates raw snapshots into minute-aligned OHLC candles. "
                    + "Supported bucket sizes: **1M** (1 min), **2M** (2 min), **3M** (3 min), **5M** (5 min), **10M** (10 min), **30M** (30 min), **60M** (60 min). "
                    + "Bucket boundaries are aligned to the full hour (e.g. 10M: 7:00–7:09:59, 7:10–7:19:59, …). "
                    + "Each candle has open/close/high/low from cLast, averaged bid/ask/avg, "
                    + "volume as cStueck increment within the bucket, and the latest delta. "
                    + "The response **header** contains pre-calculated day statistics "
                    + "(open, close, high/low with timestamps, previous-close, cumulative volume, "
                    + "executions, delta) plus the total number of data points (**buckets**)."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200",
                     description = "Intraday data successfully retrieved",
                     content = @Content(schema = @Schema(implementation = IntradayResponse.class))),
        @ApiResponse(responseCode = "400",
                     description = "Missing or invalid parameter (code or reduce value)",
                     content = @Content(schema = @Schema(implementation = PriceTickerErrorResponse.class))),
        @ApiResponse(responseCode = "404",
                     description = "Code cannot be resolved to a known ISIN, or no intraday data available for the ISIN",
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
                               + "Only the calendar date portion is used; the exact time is irrelevant. "
                               + "When omitted, the most recent day with available data for the ISIN is used.",
                   required = false, schema = @Schema(type = "integer", format = "int64"))
        @RequestParam(required = false) Long timestamp,

        @Parameter(description = "Optional OHLC candle aggregation bucket size. "
                               + "Supported values: 1M (1 minute), 10M (10 minutes), "
                               + "30M (30 minutes), 60M (60 minutes). "
                               + "Bucket boundaries are aligned to the full hour. "
                               + "When set, each data point is an OHLC candle aggregated from raw snapshots.",
                   required = false, example = "10M")
        @RequestParam(required = false) String reduce)
    {
        // ---- 1. Validate & parse the 'reduce' parameter ----------------------
        int bucketMinutes = 0;
        if (reduce != null && !reduce.trim().isEmpty())
        {
            bucketMinutes = parseReduceMinutes(reduce.trim());
            if (bucketMinutes < 0)
            {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                     .body(PriceTickerErrorResponse.create(
                                         "INVALID_REDUCE",
                                         "Invalid reduce parameter",
                                         "Supported values: 1M, 10M, 30M, 60M"));
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

        final LocalDate date;
        if (timestamp != null)
        {
            date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate();
        }
        else
        {
            // Determine the most recent day that has data for this ISIN
            LocalDate lastDay = resolveLastAvailableDay(isin);
            if (lastDay == null)
            {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                     .body(PriceTickerErrorResponse.create(
                                         "NO_DATA",
                                         "No intraday data available for ISIN",
                                         "tTradegateIntraday contains no records for: " + isin));
            }
            date = lastDay;
        }

        long dayStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli();
        long dayEndMs   = dayStartMs + 24L * 60 * 60 * 1000;

        // ---- 4. Query tTradegateIntraday -------------------------------------
        String sql = "SELECT cBid, cAsk, cDelta, cStueck, cAvg, cExecutions, "
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

                    // --- build raw data point (open=close=high=low=cLast) ---
                    IntradayResponse.IntradayDataPoint dp = new IntradayResponse.IntradayDataPoint();
                    dp.setTimestamp(tsMs);
                    dp.setOpen(cLast);
                    dp.setClose(cLast);
                    dp.setHigh(cLast);
                    dp.setLow(cLast);
                    dp.setBid(rs.getBigDecimal("cBid"));
                    dp.setAsk(rs.getBigDecimal("cAsk"));
                    dp.setAvg(rs.getBigDecimal("cAvg"));
                    dp.setVolume(rs.getLong("cStueck"));
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
            (bucketMinutes > 0 && !rawPoints.isEmpty())
                ? reduceIntradayPoints(rawPoints, bucketMinutes, zone)
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
        header.setBuckets(dataPoints.size());

        // ---- 7. Assemble and return response ---------------------------------
        IntradayResponse response = new IntradayResponse();
        response.setIsin(isin);
        response.setDate(date.toString());
        response.setReduce(bucketMinutes > 0 ? reduce.trim() : null);
        response.setHeader(header);
        response.setData(dataPoints);

        return ResponseEntity.ok(response);
    }


    /**
     * Liefert den aktuellen (laufenden) OHLC-Kerze-Datensatz für den Minuten-Bucket, der
     * die aktuelle Serverzeit ({@code Europe/Berlin}) enthält.
     *
     * <p>Die Bucket-Grenzen werden aus der aktuellen Uhrzeit abgeleitet.  Der Bucket-Start
     * ergibt sich durch Rückrechnung auf den letzten vollen Bucket-Beginn, wobei die
     * Bucket-Grenzen zur vollen Stunde aligniert sind:
     * <pre>
     * bucketKey   = floor(minuteOfDay / bucketMinutes) × bucketMinutes
     * bucketStart = Mitternacht (CET) + bucketKey Minuten
     * bucketEnd   = bucketStart + bucketMinutes  (exklusiv)
     * </pre>
     *
     * <p><b>Beispiel</b> (bucket={@code 5M}, aktuelle Zeit {@code 14:02:23 CET}):
     * <ul>
     *   <li>Bucket-Start: {@code 14:00:00.000 CET}</li>
     *   <li>Bucket-Ende:  {@code 14:04:59.999 CET} (inklusiv; exklusiv: {@code 14:05:00.000 CET})</li>
     * </ul>
     *
     * <p>Alle Snapshots in {@code tTradegateIntraday} innerhalb dieser Grenzen werden zu
     * einem einzigen OHLC-Datensatz aggregiert:
     * <ul>
     *   <li>{@code open}   – {@code cLast} des frühesten Snapshots im Bucket.</li>
     *   <li>{@code close}  – {@code cLast} des letzten Snapshots im Bucket.</li>
     *   <li>{@code high}   – Maximum von {@code cLast} über alle Snapshots.</li>
     *   <li>{@code low}    – Minimum von {@code cLast} über alle Snapshots.</li>
     *   <li>{@code bid}, {@code ask}, {@code avg} – arithmetische Mittelwerte.</li>
     *   <li>{@code volume} – {@code cStueck_newest − cStueck_oldest} (Zuwachs im Bucket).</li>
     *   <li>{@code delta}  – {@code cDelta} des neuesten Snapshots.</li>
     *   <li>{@code timestamp} – Bucket-Start in Millisekunden seit Epoch.</li>
     * </ul>
     *
     * <p><b>Response-Struktur:</b>
     * <pre>
     * {
     *   "isin":         "US0378331005",
     *   "date":         "2026-03-19",
     *   "bucket":       "5M",
     *   "bucket-start": 1742389200000,
     *   "bucket-end":   1742389499999,
     *   "candle": {
     *     "timestamp": 1742389200000,
     *     "open":      150.25,
     *     "close":     151.00,
     *     "high":      151.50,
     *     "low":       150.10,
     *     "bid":       150.95,
     *     "ask":       151.05,
     *     "avg":       150.80,
     *     "volume":    12500,
     *     "delta":     0.52
     *   }
     * }
     * </pre>
     *
     * @param code   ISIN (z.B. {@code US0378331005}) oder Yahoo-Finance-Symbol (z.B. {@code AAPL}).
     *               Symbole werden über {@link SymbolResolver#resolveIsin(String)} in ISINs aufgelöst.
     * @param bucket Minuten-Bucket-Größe.  Erlaubte Werte: {@code 1M} (1 Minute), {@code 2M} (2 Minuten), {@code 3M} (3 Minuten),
     *               {@code 5M} (5 Minuten), {@code 10M} (10 Minuten), {@code 30M} (30 Minuten), {@code 60M} (60 Minuten).
     *               Bucket-Grenzen sind zur vollen Stunde aligniert.
     * @return {@code 200 OK} mit einem einzelnen OHLC-Kerze-Datensatz auf Erfolg;
     *         {@code 400 Bad Request} bei fehlendem oder ungültigem Parameter;
     *         {@code 404 Not Found} wenn der Code nicht zu einer ISIN aufgelöst werden kann
     *         oder keine Daten im aktuellen Bucket vorhanden sind;
     *         {@code 500 Internal Server Error} bei unerwarteten Fehlern.
     */
    @GetMapping(path = "/intraday/current-candle", produces = "application/json")
    @Operation(
        summary = "Aktuellen OHLC-Kerze-Datensatz abrufen",
        description = "Liefert den aktuellen (laufenden) OHLC-Datensatz für den Minuten-Bucket, der die "
                    + "aktuelle Serverzeit (Europe/Berlin) enthält. "
                    + "Die Bucket-Grenzen werden durch Rückrechnung auf den letzten vollen Bucket-Beginn "
                    + "ermittelt, der zur vollen Stunde aligniert ist. "
                    + "Beispiel: aktuelle Zeit 14:02:23, bucket=5M → Bucket 14:00–14:04:59. "
                    + "Alle Snapshots im Bucket werden zu einem OHLC-Datensatz aggregiert: "
                    + "open/close/high/low aus cLast, bid/ask/avg als arithmetische Mittelwerte, "
                    + "volume als cStueck-Zuwachs (newest − oldest), delta aus dem letzten Snapshot."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200",
                     description = "OHLC-Kerze für den aktuellen Bucket erfolgreich ermittelt",
                     content = @Content(schema = @Schema(implementation = OHLCResponse.class))),
        @ApiResponse(responseCode = "400",
                     description = "Fehlender oder ungültiger Parameter (code oder bucket)",
                     content = @Content(schema = @Schema(implementation = PriceTickerErrorResponse.class))),
        @ApiResponse(responseCode = "404",
                     description = "Code kann nicht zu einer ISIN aufgelöst werden oder keine Daten im aktuellen Bucket",
                     content = @Content(schema = @Schema(implementation = PriceTickerErrorResponse.class))),
        @ApiResponse(responseCode = "500",
                     description = "Unerwarteter Fehler während der Verarbeitung",
                     content = @Content(schema = @Schema(implementation = PriceTickerErrorResponse.class)))
    })
    public ResponseEntity<?> getCurrentCandle(
        @Parameter(description = "ISIN (z.B. US0378331005) oder Yahoo-Finance-Symbol (z.B. AAPL)",
                   required = true, example = "US0378331005")
        @RequestParam(required = true) String code,

        @Parameter(description = "Minuten-Bucket-Größe für die OHLC-Aggregation. "
                               + "Erlaubte Werte: 1M (1 Minute), 5M (5 Minuten), 10M (10 Minuten). "
                               + "Bucket-Grenzen sind zur vollen Stunde aligniert.",
                   required = true, example = "5M")
        @RequestParam(required = true) String bucket)
    {
        // ---- 1. 'code' validieren --------------------------------------------
        if (code == null || code.trim().isEmpty())
        {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                 .body(PriceTickerErrorResponse.create(
                                     "INVALID_CODE",
                                     "Missing or empty code parameter",
                                     "The 'code' query parameter is required"));
        }

        // ---- 2. 'bucket' validieren und in Minuten umrechnen ----------------
        if (bucket == null || bucket.trim().isEmpty())
        {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                 .body(PriceTickerErrorResponse.create(
                                     "INVALID_BUCKET",
                                     "Missing or empty bucket parameter",
                                     "Supported values: 1M, 5M, 10M"));
        }
        int bucketMinutes;
        switch (bucket.trim())
        {
            case "1M":  bucketMinutes = 1;  break;
            case "2M":  bucketMinutes = 2;  break;
            case "3M":  bucketMinutes = 3;  break;
            case "5M":  bucketMinutes = 5;  break;
            case "10M": bucketMinutes = 10; break;
            case "30M": bucketMinutes = 30; break;
            case "60M": bucketMinutes = 60; break;
            default:
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                     .body(PriceTickerErrorResponse.create(
                                         "INVALID_BUCKET",
                                         "Invalid bucket parameter: " + bucket,
                                         "Supported values: 1M, 2M, 3M, 5M, 10M, 30M, 60M"));
        }

        // ---- 3. Symbol zu ISIN auflösen -------------------------------------
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

        // ---- 4. Bucket-Grenzen aus aktueller Uhrzeit berechnen --------------
        //
        // Bucket-Grenzen sind zur vollen Stunde aligniert:
        //   bucketKey   = floor(minuteOfDay / bucketMinutes) * bucketMinutes
        //   bucketStart = Mitternacht CET + bucketKey Minuten
        //   bucketEnd   = bucketStart + bucketMinutes (exklusiv)
        //
        // Beispiel: aktuelle Zeit 14:02:23, bucket=5M
        //   minuteOfDay = 14*60 + 2 = 842
        //   bucketKey   = (842/5)*5 = 840  →  14:00
        //   bucketStart = heute 14:00:00.000 CET
        //   bucketEnd   = heute 14:05:00.000 CET (exklusiv)
        ZoneId zone = ZoneId.of("Europe/Berlin");
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalDate today   = now.toLocalDate();

        int minuteOfDay = now.getHour() * 60 + now.getMinute();
        int bucketKey   = (minuteOfDay / bucketMinutes) * bucketMinutes;

        long bucketStartMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
                             + (long) bucketKey * 60 * 1000;
        long bucketEndMs   = bucketStartMs + (long) bucketMinutes * 60 * 1000;

        // ---- 5. Datenbank abfragen ------------------------------------------
        String sql = "SELECT cBid, cAsk, cDelta, cStueck, cAvg, cLast, cTimestamp "
                   + "FROM tTradegateIntraday "
                   + "WHERE cIsin = ? AND cTimestamp >= ? AND cTimestamp < ? "
                   + "ORDER BY cTimestamp ASC";

        List<IntradayResponse.IntradayDataPoint> snapshots = new ArrayList<>();
        try (Connection conn = DBConnection.getStocksConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, isin);
            ps.setTimestamp(2, new Timestamp(bucketStartMs));
            ps.setTimestamp(3, new Timestamp(bucketEndMs));

            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next())
                {
                    BigDecimal cLast = rs.getBigDecimal("cLast");
                    long tsMs = rs.getTimestamp("cTimestamp").getTime();

                    IntradayResponse.IntradayDataPoint dp = new IntradayResponse.IntradayDataPoint();
                    dp.setTimestamp(tsMs);
                    dp.setOpen(cLast);
                    dp.setClose(cLast);
                    dp.setHigh(cLast);
                    dp.setLow(cLast);
                    dp.setBid(rs.getBigDecimal("cBid"));
                    dp.setAsk(rs.getBigDecimal("cAsk"));
                    dp.setAvg(rs.getBigDecimal("cAvg"));
                    dp.setVolume(rs.getLong("cStueck"));
                    dp.setDelta(rs.getBigDecimal("cDelta"));
                    snapshots.add(dp);
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

        if (snapshots.isEmpty())
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                 .body(PriceTickerErrorResponse.create(
                                     "NO_DATA",
                                     "No intraday data available for the current bucket",
                                     String.format("No data found for ISIN %s in bucket [%s, %s)",
                                                   isin,
                                                   new Timestamp(bucketStartMs),
                                                   new Timestamp(bucketEndMs))));
        }

        // ---- 6. Snapshots zum OHLC-Datensatz aggregieren --------------------
        IntradayResponse.IntradayDataPoint first = snapshots.get(0);
        IntradayResponse.IntradayDataPoint last  = snapshots.get(snapshots.size() - 1);

        IntradayResponse.IntradayDataPoint candle = new IntradayResponse.IntradayDataPoint();
        candle.setTimestamp(bucketStartMs);
        candle.setOpen(snapshots.stream()
            .map(IntradayResponse.IntradayDataPoint::getOpen)
            .filter(v -> v != null).findFirst().orElse(null));
        candle.setClose(snapshots.stream()
            .map(IntradayResponse.IntradayDataPoint::getClose)
            .filter(v -> v != null).reduce((a, b) -> b).orElse(null));
        candle.setHigh(snapshots.stream()
            .map(IntradayResponse.IntradayDataPoint::getHigh)
            .filter(v -> v != null).max(BigDecimal::compareTo).orElse(null));
        candle.setLow(snapshots.stream()
            .map(IntradayResponse.IntradayDataPoint::getLow)
            .filter(v -> v != null).min(BigDecimal::compareTo).orElse(null));
        candle.setBid(avgBigDecimal(snapshots.stream()
            .map(IntradayResponse.IntradayDataPoint::getBid).collect(Collectors.toList())));
        candle.setAsk(avgBigDecimal(snapshots.stream()
            .map(IntradayResponse.IntradayDataPoint::getAsk).collect(Collectors.toList())));
        candle.setAvg(avgBigDecimal(snapshots.stream()
            .map(IntradayResponse.IntradayDataPoint::getAvg).collect(Collectors.toList())));

        // volume = Zuwachs: cStueck_newest − cStueck_oldest
        Long firstVol = first.getVolume();
        Long lastVol  = last.getVolume();
        candle.setVolume(firstVol != null && lastVol != null ? lastVol - firstVol : null);
        candle.setDelta(last.getDelta());

        // ---- 7. Antwort zusammenstellen -------------------------------------
        OHLCResponse result = new OHLCResponse();
        result.setIsin(isin);
        result.setDate(today.toString());
        result.setBucket(bucket.trim());
        result.setBucketStart(bucketStartMs);
        result.setBucketEnd(bucketEndMs - 1);   // inklusiv letzte Millisekunde
        result.setCandle(candle);

        return ResponseEntity.ok(result);
    }


    /**
     * Queries {@code tTradegateIntraday} for the most recent calendar day (Europe/Berlin)
     * that contains at least one record for the given ISIN.
     *
     * @param isin the ISIN to look up
     * @return the most recent {@link LocalDate} with data, or {@code null} if no data exists
     */
    private LocalDate resolveLastAvailableDay(String isin)
    {
        String sql = "SELECT MAX(cTimestamp) FROM tTradegateIntraday WHERE cIsin = ?";
        try (Connection conn = DBConnection.getStocksConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, isin);
            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    Timestamp maxTs = rs.getTimestamp(1);
                    if (maxTs != null)
                    {
                        return maxTs.toInstant()
                                   .atZone(ZoneId.of("Europe/Berlin"))
                                   .toLocalDate();
                    }
                }
            }
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * Parses a reduce parameter string into the corresponding bucket duration in minutes.
     *
     * @param reduce the reduce token (e.g. {@code "10M"})
     * @return positive number of minutes for valid tokens, {@code -1} for unknown tokens
     */
    private int parseReduceMinutes(String reduce)
    {
        switch (reduce)
        {
            case "1M":  return 1;
            case "2M":  return 2;
            case "3M":  return 3;
            case "5M":  return 5;
            case "10M": return 10;
            case "30M": return 30;
            case "60M": return 60;
            default:    return -1;
        }
    }


    /**
     * Aggregates raw intraday snapshots into minute-aligned OHLC candles.
     *
     * <p>Bucket boundaries are aligned to the full hour: for bucket width {@code N} minutes,
     * the buckets within each hour are {@code [H:00, H:N), [H:N, H:2N), …}.
     * The bucket key is computed as {@code floor(minuteOfDay / N) * N} where
     * {@code minuteOfDay = hour * 60 + minute} in the {@code Europe/Berlin} timezone.
     *
     * <p>Aggregation rules per bucket:
     * <ul>
     *   <li>{@code timestamp} – oldest snapshot's timestamp (bucket start).</li>
     *   <li>{@code open}  – {@code cLast} of the first (oldest) snapshot.</li>
     *   <li>{@code close} – {@code cLast} of the last (newest) snapshot.</li>
     *   <li>{@code high}  – maximum {@code cLast}.</li>
     *   <li>{@code low}   – minimum {@code cLast}.</li>
     *   <li>{@code bid}, {@code ask}, {@code avg} – arithmetic mean.</li>
     *   <li>{@code volume} – {@code cStueck_newest − cStueck_oldest} (increment).</li>
     *   <li>{@code delta} – {@code cDelta} of the newest snapshot.</li>
     * </ul>
     *
     * @param points        raw data ordered by timestamp ascending
     * @param bucketMinutes bucket width in minutes (1, 10, 30, or 60)
     * @param zone          timezone used for local-time bucket alignment
     * @return aggregated OHLC candles in chronological order
     */
    private List<IntradayResponse.IntradayDataPoint> reduceIntradayPoints(
        List<IntradayResponse.IntradayDataPoint> points, int bucketMinutes, ZoneId zone)
    {
        // LinkedHashMap preserves insertion order = chronological bucket order
        Map<Integer, List<IntradayResponse.IntradayDataPoint>> buckets = new LinkedHashMap<>();
        for (IntradayResponse.IntradayDataPoint dp : points)
        {
            java.time.LocalTime lt = Instant.ofEpochMilli(dp.getTimestamp())
                                            .atZone(zone).toLocalTime();
            int minuteOfDay = lt.getHour() * 60 + lt.getMinute();
            int bucketKey   = (minuteOfDay / bucketMinutes) * bucketMinutes;
            buckets.computeIfAbsent(bucketKey, k -> new ArrayList<>()).add(dp);
        }

        List<IntradayResponse.IntradayDataPoint> result = new ArrayList<>();
        for (List<IntradayResponse.IntradayDataPoint> group : buckets.values())
        {
            IntradayResponse.IntradayDataPoint first = group.get(0);
            IntradayResponse.IntradayDataPoint last  = group.get(group.size() - 1);

            IntradayResponse.IntradayDataPoint agg = new IntradayResponse.IntradayDataPoint();

            agg.setTimestamp(first.getTimestamp());   // bucket start

            // open: first non-null cLast in the bucket (scan forward)
            agg.setOpen(group.stream()
                .map(IntradayResponse.IntradayDataPoint::getOpen)
                .filter(v -> v != null)
                .findFirst().orElse(null));

            // close: last non-null cLast in the bucket (scan backward)
            agg.setClose(group.stream()
                .map(IntradayResponse.IntradayDataPoint::getClose)
                .filter(v -> v != null)
                .reduce((a, b) -> b).orElse(null));

            BigDecimal maxHigh = group.stream()
                .map(IntradayResponse.IntradayDataPoint::getHigh)
                .filter(v -> v != null)
                .max(BigDecimal::compareTo).orElse(null);
            agg.setHigh(maxHigh);

            BigDecimal minLow = group.stream()
                .map(IntradayResponse.IntradayDataPoint::getLow)
                .filter(v -> v != null)
                .min(BigDecimal::compareTo).orElse(null);
            agg.setLow(minLow);

            agg.setBid(avgBigDecimal(group.stream()
                .map(IntradayResponse.IntradayDataPoint::getBid).collect(Collectors.toList())));
            agg.setAsk(avgBigDecimal(group.stream()
                .map(IntradayResponse.IntradayDataPoint::getAsk).collect(Collectors.toList())));
            agg.setAvg(avgBigDecimal(group.stream()
                .map(IntradayResponse.IntradayDataPoint::getAvg).collect(Collectors.toList())));

            // volume = increment: newest cStueck - oldest cStueck
            Long firstVol = first.getVolume();
            Long lastVol  = last.getVolume();
            agg.setVolume(firstVol != null && lastVol != null ? lastVol - firstVol : null);

            agg.setDelta(last.getDelta());            // newest cDelta

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
