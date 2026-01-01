package com.straube.jones.controller;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import com.straube.jones.dataprovider.userprefs.UserPrefsRepo;
import com.straube.jones.db.DBConnection;
import com.straube.jones.dto.LastSharePriceResponse;
import com.straube.jones.dto.OnVistaReportResponse;
import com.straube.jones.dto.ServiceInfoResponse;
import com.straube.jones.dto.ShareSearchResponse;
import com.straube.jones.dto.TableDataResponse;
import com.straube.jones.dto.TablePriceDataResponse;
import com.straube.jones.dto.UserPrefsResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(value = "/api")
@Tag(name = "Stocks API", description = "Comprehensive stock market data API for financial analysis, portfolio management, and investment decision support. Provides real-time and historical stock data, technical analysis tools, and user preference management.")
public class StocksController
{
	private static final String DATA_ROOT_FOLDER = System.getProperty(	"data.root",
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
	@ApiResponses(value = {	@ApiResponse(responseCode = "200", description = "Financial report data successfully retrieved"),
							@ApiResponse(responseCode = "404", description = "Report not found - invalid short URL or data not available")})
	@GetMapping(path = "/onvista/aktien/kennzahlen/{short_url}", produces = "application/json")
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
				final Element e0 = doc0	.select("#__next > div.ov-content > div > section > div.col.col-12.inner-spacing--medium-top.ov-snapshot-tabs > div > section > div.col.grid.col--sm-4.col--md-8.col--lg-9.col--xl-9 > div:nth-child(2) > div > div > p")
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
	@ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Time-series stock data successfully retrieved in tabular format")})
	@GetMapping(path = "/stock/data", produces = "application/json")
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
	@ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Time-series stock price data successfully retrieved in tabular format")})
	@GetMapping(path = "/stock/prices", produces = "application/json")
	public TablePriceDataResponse getPriceData(@Parameter(description = "List of Yahoo symbol codes or ISIN codes (International Securities Identification Numbers, e.g., ['US0378331005', 'DE0007164600'])")
	@RequestParam
	List<String> codes,
										@Parameter(description = "Start timestamp in milliseconds (Unix epoch time). Defaults to 1 months ago for sufficient historical data.", schema = @Schema(type = "integer", format = "int64"))
										@RequestParam(value = "start_time", required = false)
										Long start,
										@Parameter(description = "End timestamp in milliseconds (Unix epoch time). Defaults to current time for sufficient historical data.", schema = @Schema(type = "integer", format = "int64"))
										@RequestParam(value = "end_time", required = false)
										Long end,
										@Parameter(description = "Data type identifier (0=price data, 1=percentage development since start time). Defaults to 0 (price data).")
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
		return PricePointLoader.loadPrices(codes, start, end, type);
	}


	@Operation(summary = "Get Single Stock Item", description = "**Use Case:** Retrieve detailed information for a specific stock by ISIN. Returns a single StockItem with comprehensive metadata. **When to use:** For displaying stock details, getting stock information for a known ISIN, or retrieving metadata for a specific security. **Performance:** Fast single-record lookup optimized for detail views.")
	@ApiResponses(value = {	@ApiResponse(responseCode = "200", description = "Stock item successfully retrieved"),
							@ApiResponse(responseCode = "404", description = "Stock not found for the given ISIN")})
	@GetMapping(path = "/stock/item", produces = "application/json")
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
	@ApiResponse(responseCode = "200", description = "Complete stock catalog with metadata organized by categories")
	@GetMapping(path = "/stock_items", produces = "application/json")
	public Map<String, List<StockItem>> getStockItems()
	{
		return StocksLoader.load();
	}


	@Operation(summary = "Get Last Share Price", description = "**Use Case:** Real-time price monitoring and current valuation. Retrieves the most recent known price for a specific stock. **When to use:** For current portfolio valuation, price alerts, real-time trading decisions, or displaying current market values. **Data source:** Uses the latest available price data from stock history. **Performance:** Fast lookup optimized for single stock queries.")
	@ApiResponses(value = {	@ApiResponse(responseCode = "200", description = "Last share price successfully retrieved"),
							@ApiResponse(responseCode = "404", description = "No price data found for the given ISIN"),
							@ApiResponse(responseCode = "500", description = "Internal server error during price lookup")})
	@GetMapping(path = "/stock/price/last", produces = "application/json")
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

			return LastSharePriceResponse.success(	isin,
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


	@Operation(
		summary = "Search Stocks by Name", 
		description = "**Use Case:** Stock discovery through fuzzy name matching. Finds stocks using tolerant similarity search that ignores special characters, whitespaces, and various dash types. **When to use:** For stock lookup when exact names are unknown, user input validation, or building search suggestions. **Algorithm:** Normalized string comparison with similarity scoring. **Results:** Multiple matches possible, sorted by relevance score."
	)
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = "Search completed successfully, results include similarity scores"),
		@ApiResponse(responseCode = "500", description = "Search operation failed - check system availability")
	})
	@GetMapping(path = "/stock/search/name", produces = "application/json")
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
			{
				return ShareSearchResponse.success(name, new ArrayList<>());
			}
			
			// Find matching stocks with similarity scores
			List<ShareSearchResponse.StockMatch> matches = stockList.stream()
				.filter(stock -> stock.getName() != null && !stock.getName().trim().isEmpty())
				.map(stock -> {
					String normalizedStockName = normalizeString(stock.getName());
					double similarity = calculateSimilarity(normalizedQuery, normalizedStockName);
					return new ShareSearchResponse.StockMatch(stock, similarity);
				})
				.filter(match -> match.getSimilarityScore() > 0.3) // Only include matches with > 30% similarity
				.sorted(Comparator.comparing(ShareSearchResponse.StockMatch::getSimilarityScore).reversed())
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
	 * Normalizes a string for tolerant comparison by removing special characters,
	 * whitespaces, and various types of dashes, then converting to lowercase.
	 */
	private String normalizeString(String input)
	{
		if (input == null)
		{
			return "";
		}
		
		// Convert to lowercase and remove all special characters, whitespaces, and dashes
		return input.toLowerCase()
				.replaceAll("[\\s\\-\\u2010\\u2011\\u2012\\u2013\\u2014\\u2015\\u2212]", "") // Remove spaces and all dash types
				.replaceAll("[^a-z0-9]", ""); // Remove all non-alphanumeric characters
	}

	/**
	 * Calculates similarity between two normalized strings using a combination of
	 * exact match, contains check, and Levenshtein-like distance.
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
			for (int i = 0; i < Math.min(query.length(), target.length()); i++)
			{
				if (query.charAt(i) == target.charAt(i))
				{
					commonChars++;
				}
				else
				{
					break; // Stop at first non-matching character from start
				}
			}
			
			// Check for common substrings
			for (int len = 3; len <= Math.min(query.length(), target.length()); len++)
			{
				for (int i = 0; i <= query.length() - len; i++)
				{
					String substring = query.substring(i, i + len);
					if (target.contains(substring))
					{
						commonChars = Math.max(commonChars, len);
					}
				}
			}
			
			double similarity = (double) commonChars / maxLength;
			return similarity > 0.3 ? similarity : 0.0;
		}
		
		return 0.0; // No meaningful similarity
	}


	@Operation(summary = "Save User Preferences", description = "**Use Case:** Persistent user customization and personalization. Stores user-specific settings for different application areas. **When to use:** To save custom filters, stock selections, display preferences, or any user configuration that should persist across sessions. **Special topics:** 'filter' for search/display filters, or custom topic names for stock groupings. **Data format:** JSON string containing preference data.")
	@ApiResponses(value = {	@ApiResponse(responseCode = "200", description = "Preferences successfully saved with confirmation"),
							@ApiResponse(responseCode = "500", description = "Save operation failed - check data format or system availability")})
	@PostMapping(path = "/prefs/{topic}", produces = "application/json")
	public UserPrefsResponse setUserPref(@Parameter(description = "Preference category (use 'filter' for global filters, or custom names for stock groups)")
	@PathVariable
	String topic,
											@Parameter(description = "JSON string containing user preferences data structure. Format depends on topic - filters use filter criteria objects, stock groups use arrays of ISINs.")
											@RequestBody
											String userPrefs)
	{
		try
		{
			if ("filter".equals(topic))
			{
				UserPrefsRepo.saveFilter(userPrefs);
			}
			else
			{
				UserPrefsRepo.saveStocks(topic, userPrefs);
			}
			return UserPrefsResponse.success(topic, userPrefs);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return UserPrefsResponse.error(topic, "Failed to save preferences: " + e.getMessage());
		}
	}


	@Operation(summary = "Load User Preferences", description = "**Use Case:** Retrieve stored user customizations and restore application state. **When to use:** On application startup, when switching contexts, or when user wants to apply saved settings. **Return format:** JSON string containing saved preference data for the specified topic. **Common topics:** 'filter' for global display filters, or custom topic names for specific stock groupings/watchlists.")
	@ApiResponses(value = {	@ApiResponse(responseCode = "200", description = "Preferences successfully loaded as JSON string"),
							@ApiResponse(responseCode = "500", description = "Load operation failed - topic may not exist or system error")})
	@GetMapping(path = "/prefs/{topic}", produces = "application/json")
	public UserPrefsResponse getUserPrefs(@Parameter(description = "Preference category to retrieve (use 'filter' for global filters, or custom names for stock groups)")
	@PathVariable
	String topic)
	{
		try
		{
			String preferences;
			if ("filter".equals(topic))
			{
				preferences = UserPrefsRepo.getFilter();
			}
			else
			{
				preferences = UserPrefsRepo.getStocks(topic);
			}
			return UserPrefsResponse.success(topic, preferences);
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return UserPrefsResponse.error(topic, "Failed to load preferences: " + e.getMessage());
		}
	}


	@Operation(summary = "Generate Stock Chart Image", description = "**Use Case:** Visual chart generation for reports, dashboards, or quick visual analysis. Returns pre-generated PNG chart images for specific stocks and timeframes. **When to use:** For embedding charts in documents, creating visual reports, thumbnail generation, or when lightweight image-based charts are preferred over interactive charts. **Customization:** Configurable dimensions and time periods. **Performance:** Fast delivery of cached chart images.")
	@ApiResponses(value = {	@ApiResponse(responseCode = "200", description = "Chart image successfully generated and returned as PNG", content = @Content(mediaType = "image/png")),
							@ApiResponse(responseCode = "404", description = "Chart not available - invalid ISIN or image not generated yet"),
							@ApiResponse(responseCode = "500", description = "Image generation or file system error")})
	@GetMapping(path = "/stock/image", produces = MediaType.IMAGE_PNG_VALUE)
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
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Stock successfully added or updated"),
			@ApiResponse(responseCode = "500", description = "Database error")
	})
	@PostMapping(path = "/stock/add", consumes = "application/json", produces = "application/json")
	public ResponseEntity<String> addStock(
			@Parameter(description = "StockItem object containing stock details", required = true, schema = @Schema(implementation = StockItem.class))
			@RequestBody StockItem stockItem)
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
					updatePs.setDouble(3, stockItem.getCapitalization() != null ? stockItem.getCapitalization() : 0.0);
					updatePs.setString(4, stockItem.getIndustry());
					updatePs.setDouble(5, parseDoubleSafe(stockItem.getPerf4()));
					updatePs.setDouble(6, parseDoubleSafe(stockItem.getPerf26()));
					updatePs.setDouble(7, parseDoubleSafe(stockItem.getPerf52()));
					updatePs.setDouble(8, parseDoubleSafe(stockItem.getLast()));
					updatePs.setString(9, stockItem.getCurrency());
					updatePs.setDouble(10, stockItem.getDividendYield() != null ? stockItem.getDividendYield() : 0.0);
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
					insertPs.setDouble(4, stockItem.getCapitalization() != null ? stockItem.getCapitalization() : 0.0);
					insertPs.setString(5, stockItem.getIndustry());
					insertPs.setDouble(6, parseDoubleSafe(stockItem.getPerf4()));
					insertPs.setDouble(7, parseDoubleSafe(stockItem.getPerf26()));
					insertPs.setDouble(8, parseDoubleSafe(stockItem.getPerf52()));
					insertPs.setDouble(9, parseDoubleSafe(stockItem.getLast()));
					insertPs.setString(10, stockItem.getCurrency());
					insertPs.setDouble(11, stockItem.getDividendYield() != null ? stockItem.getDividendYield() : 0.0);
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


	private double parseDoubleSafe(String value)
	{
		if (value == null || value.trim().isEmpty())
		{
			return 0.0;
		}
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
