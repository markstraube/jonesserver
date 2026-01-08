package com.straube.jones.controller;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.straube.jones.dataprovider.yahoo.CompanyResolver;
import com.straube.jones.dataprovider.yahoo.SymbolResolver;
import com.straube.jones.dataprovider.yahoo.YahooPriceDownloader;
import com.straube.jones.dataprovider.yahoo.YahooPriceImporter;
import com.straube.jones.db.DBConnection;
import com.straube.jones.db.DayCounter;
import com.straube.jones.dto.CompanyListItem;
import com.straube.jones.dto.CompanyRequest;
import com.straube.jones.dto.CompanyResponse;
import com.straube.jones.service.IndicatorService;
import com.straube.jones.trader.collectors.IndicatorCollector;
import com.straube.jones.trader.dto.IndicatorDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/masterdata")
@Tag(name = "Masterdata API", description = "API for managing master data of companies and stocks. Provides functionality to create and update company information based on ISIN.")
public class MasterdataController
{
    private static final Logger logger = LoggerFactory.getLogger(MasterdataController.class);

    private final IndicatorCollector indicatorCollector;
    private final IndicatorService indicatorService;

    public MasterdataController(IndicatorCollector indicatorCollector, IndicatorService indicatorService)
    {
        this.indicatorCollector = indicatorCollector;
        this.indicatorService = indicatorService;
    }


    @Operation(summary = "Create or Update Company Master Data", description = "**Use Case:** Ensures that a company exists in the master data and its price data is initialized. **Logic:** Checks if the ISIN exists in `tSymbols`. If not, resolves the symbol via Yahoo Finance, downloads historical prices, and imports them. Then calculates technical indicators for the imported prices and stores them in the database. Finally, updates or creates the company record in `tCompany` based on the downloaded metadata. **Returns:** The updated company master data.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Company data successfully created or updated", content = @Content(schema = @Schema(implementation = CompanyResponse.class))),
                           @ApiResponse(responseCode = "500", description = "Internal server error, e.g., if symbol resolution fails or database error occurs")})
    @PostMapping("/company")
    public CompanyResponse company(@RequestBody
    CompanyRequest request)
        throws Exception
    {
        String isin = request.getIsin();
        String symbol = SymbolResolver.resolveCode(isin);

        // Download and import price data
        YahooPriceDownloader.fetchPrices(400, symbol, isin);
        YahooPriceImporter.uploadPriceData();

        // Calculate and save indicators for the imported prices
        long endDay = DayCounter.now();
        long startDay = endDay - 400; // Calculate indicators for the last 400 days (matching the download period)

        List<IndicatorDto> indicators = indicatorCollector.collect(symbol, startDay, endDay);
        indicatorService.upsertIndicators(indicators);

        logger.info("Calculated and saved {} indicators for symbol: {}", indicators.size(), symbol);

        // Return the current record from tCompany
        return getCompany(symbol);
    }


    /**
     * Retrieves a list of all companies from the database.
     * 
     * <p><b>Use Case:</b> This endpoint is used to get an overview of all companies 
     * available in the system. It returns a simplified list containing only the 
     * company name and symbol for efficient data transfer and display purposes.</p>
     * 
     * <p><b>Logic:</b></p>
     * <ul>
     *   <li>Queries the tCompany table for all companies</li>
     *   <li>Extracts only the cSymbol and cLongName fields</li>
     *   <li>Orders results alphabetically by company name (cLongName)</li>
     *   <li>Returns a list of CompanyListItem objects</li>
     * </ul>
     * 
     * <p><b>Returns:</b> A list of all companies with their symbols and full names.
     * Each item contains:</p>
     * <ul>
     *   <li><b>cSymbol</b>: The stock ticker symbol (e.g., "AAPL", "MSFT")</li>
     *   <li><b>cLongName</b>: The full legal company name (e.g., "Apple Inc.", "Microsoft Corporation")</li>
     * </ul>
     * 
     * <p><b>Example Response:</b></p>
     * <pre>
     * [
     *   {
     *     "cSymbol": "AAPL",
     *     "cLongName": "Apple Inc."
     *   },
     *   {
     *     "cSymbol": "MSFT",
     *     "cLongName": "Microsoft Corporation"
     *   }
     * ]
     * </pre>
     * 
     * @return List of CompanyListItem objects containing symbol and name pairs
     * @throws SQLException if database access fails
     */
    @Operation(summary = "Get List of All Companies", description = "Retrieves a simplified list of all companies in the database, containing only the stock symbol and full company name. The list is ordered alphabetically by company name. This endpoint is optimized for dropdown lists, autocomplete features, and overview displays where only basic company identification is needed.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Successfully retrieved the list of companies. Returns an array of company items, each containing the stock symbol (cSymbol) and full company name (cLongName). The list is sorted alphabetically by company name.", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = CompanyListItem.class)))),
                           @ApiResponse(responseCode = "500", description = "Internal server error occurred while accessing the database or processing the request.")})
    @GetMapping("/company/list")
    public List<CompanyListItem> getCompanyList()
        throws SQLException
    {
        List<CompanyListItem> companies = new ArrayList<>();
        String sql = "SELECT cSymbol, cLongName FROM tCompany ORDER BY cLongName";

        try (Connection conn = DBConnection.getStocksConnection();
                        PreparedStatement ps = conn.prepareStatement(sql);
                        ResultSet rs = ps.executeQuery())
        {

            while (rs.next())
            {
                CompanyListItem item = new CompanyListItem(rs.getString("cSymbol"),
                                                           rs.getString("cLongName"));
                companies.add(item);
            }
        }

        return companies;
    }


    /**
     * Retrieves detailed information for a specific company by its symbol.
     * 
     * <p><b>Use Case:</b> This endpoint is used to get complete master data for 
     * a specific company. It's typically called after selecting a company from the 
     * list, or when detailed company information is needed for analysis, reporting, 
     * or display purposes.</p>
     * 
     * <p><b>Logic:</b></p>
     * <ul>
     *   <li>Queries the tCompany table for the company with the given symbol</li>
     *   <li>Returns all available fields including trading metadata, exchange information, and timestamps</li>
     *   <li>Returns null if no company with the given symbol exists</li>
     * </ul>
     * 
     * <p><b>Returns:</b> A CompanyResponse object containing complete company information:</p>
     * <ul>
     *   <li><b>cId</b>: Unique identifier (UUID)</li>
     *   <li><b>cSymbol</b>: Stock ticker symbol</li>
     *   <li><b>cIsin</b>: International Securities Identification Number</li>
     *   <li><b>cShortName</b>: Abbreviated company name</li>
     *   <li><b>cLongName</b>: Full legal company name</li>
     *   <li><b>cCurrency</b>: Trading currency (e.g., "USD", "EUR")</li>
     *   <li><b>cInstrumentType</b>: Type of security (e.g., "EQUITY")</li>
     *   <li><b>cFirstTradeDate</b>: Date of first recorded trade</li>
     *   <li><b>cExchangeName</b>: Exchange abbreviation (e.g., "NasdaqGS")</li>
     *   <li><b>cFullExchangeName</b>: Full exchange name</li>
     *   <li><b>cExchangeTimezoneName</b>: Exchange timezone (e.g., "America/New_York")</li>
     *   <li><b>cTimezone</b>: Timezone abbreviation (e.g., "EST")</li>
     *   <li><b>cHasPrePostMarketData</b>: Whether pre/post-market data is available</li>
     *   <li><b>cPriceHint</b>: Decimal places for price formatting</li>
     *   <li><b>cDataGranularity</b>: Data resolution (e.g., "1d")</li>
     *   <li><b>cCreated</b>: Record creation timestamp</li>
     *   <li><b>cUpdated</b>: Last update timestamp</li>
     * </ul>
     * 
     * <p><b>Example Response:</b></p>
     * <pre>
     * {
     *   "cId": "550e8400-e29b-41d4-a716-446655440000",
     *   "cSymbol": "AAPL",
     *   "cIsin": "US0378331005",
     *   "cShortName": "Apple Inc.",
     *   "cLongName": "Apple Inc.",
     *   "cCurrency": "USD",
     *   "cInstrumentType": "EQUITY",
     *   "cFirstTradeDate": "1980-12-12",
     *   "cExchangeName": "NasdaqGS",
     *   "cFullExchangeName": "Nasdaq Global Select",
     *   "cExchangeTimezoneName": "America/New_York",
     *   "cTimezone": "EST",
     *   "cHasPrePostMarketData": true,
     *   "cPriceHint": 2,
     *   "cDataGranularity": "1d",
     *   "cCreated": "2023-01-01T12:00:00Z",
     *   "cUpdated": "2023-01-02T12:00:00Z"
     * }
     * </pre>
     * 
     * @param symbol The stock symbol (ticker) to look up
     * @return CompanyResponse object with complete company data, or null if not found
     * @throws SQLException if database access fails
     */
    @Operation(summary = "Get Company Details by Symbol", description = "Retrieves complete master data for a specific company identified by its stock symbol. Returns all available information including identifiers, names, exchange details, trading metadata, and timestamps. This endpoint provides comprehensive company information needed for detailed views, analysis, and trading operations.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Successfully retrieved company details. Returns a complete CompanyResponse object with all available fields populated from the tCompany table. If no company with the given symbol exists, returns null.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CompanyResponse.class))),
                           @ApiResponse(responseCode = "404", description = "Company with the specified symbol was not found in the database."),
                           @ApiResponse(responseCode = "500", description = "Internal server error occurred while accessing the database or processing the request.")})
    @GetMapping("/company/{symbol}")
    public CompanyResponse getCompanyBySymbol(@Parameter(description = "The stock ticker symbol of the company to retrieve. This is case-sensitive and should match the symbol stored in the database.", example = "AAPL", required = true)
    @PathVariable
    String symbol)
        throws SQLException
    {
        return getCompany(symbol);
    }


    private CompanyResponse getCompany(String symbol)
        throws SQLException
    {
        String sql = "SELECT cId, cSymbol, cIsin, cShortName, cLongName, cCurrency, cInstrumentType, "
                        + "cFirstTradeDate, cExchangeName, cFullExchangeName, cExchangeTimezoneName, cTimezone, "
                        + "cHasPrePostMarketData, cPriceHint, cDataGranularity, cCreated, cUpdated "
                        + "FROM tCompany WHERE cSymbol = ?";
        try (Connection conn = DBConnection.getStocksConnection();
                        PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, symbol);
            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    CompanyResponse response = new CompanyResponse();
                    response.setcId(rs.getString("cId"));
                    response.setcSymbol(rs.getString("cSymbol"));
                    response.setcIsin(rs.getString("cIsin"));
                    response.setcShortName(rs.getString("cShortName"));
                    response.setcLongName(rs.getString("cLongName"));
                    response.setcCurrency(rs.getString("cCurrency"));
                    response.setcInstrumentType(rs.getString("cInstrumentType"));
                    response.setcFirstTradeDate(rs.getDate("cFirstTradeDate"));
                    response.setcExchangeName(rs.getString("cExchangeName"));
                    response.setcFullExchangeName(rs.getString("cFullExchangeName"));
                    response.setcExchangeTimezoneName(rs.getString("cExchangeTimezoneName"));
                    response.setcTimezone(rs.getString("cTimezone"));
                    response.setcHasPrePostMarketData(rs.getBoolean("cHasPrePostMarketData"));
                    response.setcPriceHint(rs.getInt("cPriceHint"));
                    response.setcDataGranularity(rs.getString("cDataGranularity"));
                    response.setcCreated(rs.getTimestamp("cCreated"));
                    response.setcUpdated(rs.getTimestamp("cUpdated"));
                    return response;
                }
            }
        }
        return null;
    }


    @Operation(summary = "Resolve Companies from ISINs", description = "Accepts a list of ISIN strings and resolves each to its corresponding company. "
                    + "If the symbol is not found in the local database, it attempts to resolve it via an external provider (Yahoo Finance), "
                    + "stores it, and then looks up for the company. The retrieved company details are not stored to database.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "A list of CompanyResponse objects containing company details for the resolved ISINs. "
                    + "If resolution fails for a specific ISIN, it may be omitted from the list.", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = CompanyResponse.class))))})
    @PostMapping("/resolve")
    public List<CompanyResponse> resolveSymbol(@RequestBody
    List<String> isins)
    {
        List<CompanyResponse> result = new ArrayList<>();
        if (isins != null)
        {
            for (String isin : isins)
            {
                CompanyResponse response = CompanyResolver.resolve(isin);
                if (response != null)
                {
                    result.add(response);
                }
            }
        }
        return result;
    }
}
