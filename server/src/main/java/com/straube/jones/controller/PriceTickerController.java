package com.straube.jones.controller;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.straube.jones.dataprovider.eurorates.CurrencyDB;
import com.straube.jones.dataprovider.yahoo.SymbolResolver;
import com.straube.jones.db.DayCounter;
import com.straube.jones.dto.PriceTickerErrorResponse;
import com.straube.jones.dto.PriceTickerResponse;
import com.straube.jones.service.PriceTickerService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST Controller for Price Ticker API.
 * Retrieves current stock price information from Yahoo Finance by scraping HTML pages.
 */
@RestController
@RequestMapping("/api/price-ticker")
@Tag(name = "Price Ticker API", description = "Retrieves current stock price information by scraping Yahoo Finance HTML pages")
public class PriceTickerController
{
    private final PriceTickerService service;
    
    /**
     * Creates a new controller with the default service.
     */
    public PriceTickerController()
    {
        this.service = new PriceTickerService();
    }
    
    /**
     * Creates a new controller with a custom service.
     * 
     * @param service The service to use
     */
    public PriceTickerController(PriceTickerService service)
    {
        this.service = service;
    }
    
    /**
     * Retrieves current price ticker information for a stock by ISIN.
     * 
     * GET /api/price-ticker?isin={ISIN}
     * 
     * @param isin The ISIN of the stock to retrieve price information for
     * @return ResponseEntity with PriceTickerResponse (200) or error response
     */
    @GetMapping("/tradegate")
    @Operation(
        summary = "Get current stock price by ISIN or Yahoo symbol from Tradegate",
        description = "Retrieves current stock price information from Tradegate by scraping the HTML page. " 
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Price information successfully retrieved",
            content = @Content(schema = @Schema(implementation = PriceTickerResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid or missing ISIN or Symbol parameter",
            content = @Content(schema = @Schema(implementation = PriceTickerErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "ISIN or Symbol not found or cannot be resolved to ISIN",
            content = @Content(schema = @Schema(implementation = PriceTickerErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "502",
            description = "Tradegate page not reachable",
            content = @Content(schema = @Schema(implementation = PriceTickerErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "503",
            description = "HTML structure changed or price information not found",
            content = @Content(schema = @Schema(implementation = PriceTickerErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Unexpected error during processing",
            content = @Content(schema = @Schema(implementation = PriceTickerErrorResponse.class))
        )
    })
    public ResponseEntity<?> getPriceByCode(
        @Parameter(description = "ISIN or Yahoo symbol of the stock (e.g., US0378331005 or AAPL for Apple)", required = true)
        @RequestParam(required = true) String code)
    {
        try
        {
            // Validate ISIN parameter
            if (code == null || code.trim().isEmpty())
            {
                return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(PriceTickerErrorResponse.create(
                        "INVALID_ISIN",
                        "Missing or invalid ISIN parameter",
                        "The 'isin' query parameter is required and cannot be empty"
                    ));
            }
            
            // Get price information
            String isin = SymbolResolver.resolveIsin(code);
            PriceTickerResponse response = service.getPriceByIsinFromTradegate(isin.trim());
            return ResponseEntity.ok(response);
        }
        catch (IllegalArgumentException e)
        {
            // Handle ISIN not found or invalid
            if (e.getMessage().contains("not found") || 
                e.getMessage().contains("not available"))
            {
                return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(PriceTickerErrorResponse.create(
                        "ISIN_NOT_FOUND",
                        "ISIN not resolvable to Yahoo symbol",
                        e.getMessage()
                    ));
            }
            else
            {
                return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(PriceTickerErrorResponse.create(
                        "INVALID_REQUEST",
                        "Invalid request parameters",
                        e.getMessage()
                    ));
            }
        }
        catch (java.net.UnknownHostException | java.net.ConnectException e)
        {
            // Handle network connectivity issues
            return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(PriceTickerErrorResponse.create(
                    "TRADEGATE_UNREACHABLE",
                    "Tradegate page not reachable",
                    "Unable to connect to Tradegate: " + e.getMessage()
                ));
        }
        catch (IOException e)
        {
            // Check if it's a parsing issue (HTML structure changed)
            if (e.getMessage().contains("No price information found") ||
                e.getMessage().contains("parse") ||
                e.getMessage().contains("structure"))
            {
                return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(PriceTickerErrorResponse.create(
                        "HTML_STRUCTURE_CHANGED",
                        "HTML structure changed or price block not found",
                        e.getMessage()
                    ));
            }
            else
            {
                // General Yahoo Finance fetch error
                return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(PriceTickerErrorResponse.create(
                        "YAHOO_FETCH_ERROR",
                        "Failed to fetch data from Yahoo Finance",
                        e.getMessage()
                    ));
            }
        }
        catch (Exception e)
        {
            // Handle unexpected errors
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(PriceTickerErrorResponse.create(
                    "INTERNAL_ERROR",
                    "Unexpected error during processing",
                    e.getMessage()
                ));
        }
    }
    
    /**
     * Converts a currency value to Euro using exchange rates.
     * 
     * GET /api/price-ticker/asEuro?currency={CURRENCY}&value={VALUE}
     * 
     * This endpoint converts a given currency value to Euro using the exchange rates 
     * from the previous trading day. The conversion uses historical exchange rate data 
     * from the CurrencyDB, which contains daily rates for various currencies.
     * 
     * The method automatically handles weekends and missing data by looking back 
     * to the most recent available trading day.
     * 
     * @param currency The ISO currency code (e.g., "USD", "GBP", "GBp", "JPY")
     * @param value The numeric value to convert to Euro
     * @return ResponseEntity with the Euro value rounded to 2 decimal places (200) or error response
     */
    @GetMapping("/asEuro")
    @Operation(
        summary = "Convert currency value to Euro",
        description = "Converts a given currency value to Euro using the exchange rates from the previous trading day. " +
                      "The conversion automatically handles weekends and missing data by looking back to the most recent available trading day. " +
                      "Supported currencies include USD, GBP, GBp, JPY, CHF, and many others available in the CurrencyDB. " +
                      "The result is rounded to 2 decimal places for precision."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Currency successfully converted to Euro. Returns a Double value rounded to 2 decimal places.",
            content = @Content(schema = @Schema(implementation = Double.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid or missing parameters (currency or value)",
            content = @Content(schema = @Schema(implementation = PriceTickerErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Unexpected error during conversion",
            content = @Content(schema = @Schema(implementation = PriceTickerErrorResponse.class))
        )
    })
    public ResponseEntity<?> convertToEuro(
        @Parameter(
            description = "ISO currency code (e.g., 'USD', 'GBP', 'GBp', 'JPY', 'CHF'). " +
                         "Use 'GBp' for British Pence. The currency code is case-insensitive.",
            required = true,
            example = "USD"
        )
        @RequestParam(required = true) String currency,
        
        @Parameter(
            description = "The numeric value to convert to Euro. Must be a valid number (can include decimals).",
            required = true,
            example = "100.50"
        )
        @RequestParam(required = true) Double value)
    {
        try
        {
            // Validate currency parameter
            if (currency == null || currency.trim().isEmpty())
            {
                return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(PriceTickerErrorResponse.create(
                        "INVALID_CURRENCY",
                        "Missing or invalid currency parameter",
                        "The 'currency' query parameter is required and cannot be empty"
                    ));
            }
            
            // Validate value parameter
            if (value == null || value.isNaN() || value.isInfinite())
            {
                return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(PriceTickerErrorResponse.create(
                        "INVALID_VALUE",
                        "Missing or invalid value parameter",
                        "The 'value' query parameter is required and must be a valid number"
                    ));
            }
            
            // Convert to Euro using yesterday's exchange rate
            Double euroValue = CurrencyDB.getAsEuro(currency.trim(), value, DayCounter.yesterday());
            
            // Round to 2 decimal places
            Double roundedValue = Math.round(euroValue * 100.0) / 100.0;
            
            return ResponseEntity.ok(roundedValue);
        }
        catch (Exception e)
        {
            // Handle unexpected errors
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(PriceTickerErrorResponse.create(
                    "CONVERSION_ERROR",
                    "Error during currency conversion",
                    e.getMessage()
                ));
        }
    }
}
