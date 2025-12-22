package com.straube.jones.controller;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    @GetMapping
    @Operation(
        summary = "Get current stock price by ISIN",
        description = "Retrieves current stock price information from Yahoo Finance by scraping the HTML page. " +
                     "Returns regular market price and extended hours (pre-market/after-market) prices if available."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Price information successfully retrieved",
            content = @Content(schema = @Schema(implementation = PriceTickerResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid or missing ISIN parameter",
            content = @Content(schema = @Schema(implementation = PriceTickerErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "ISIN not found or cannot be resolved to Yahoo symbol",
            content = @Content(schema = @Schema(implementation = PriceTickerErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "502",
            description = "Yahoo Finance page not reachable",
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
    public ResponseEntity<?> getPriceByIsin(
        @Parameter(description = "ISIN of the stock (e.g., US0378331005 for Apple)", required = true)
        @RequestParam(required = true) String isin)
    {
        try
        {
            // Validate ISIN parameter
            if (isin == null || isin.trim().isEmpty())
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
            PriceTickerResponse response = service.getPriceByIsin(isin.trim());
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
                    "YAHOO_UNREACHABLE",
                    "Yahoo Finance page not reachable",
                    "Unable to connect to Yahoo Finance: " + e.getMessage()
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
}
