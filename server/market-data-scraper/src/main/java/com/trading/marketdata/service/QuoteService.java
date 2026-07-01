package com.trading.marketdata.service;

import com.trading.marketdata.ibkr.IbkrMarketDataService;
import com.trading.marketdata.model.QuoteData;
import com.trading.marketdata.scraper.ScraperException;
import com.trading.marketdata.scraper.YahooFinanceScraper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

    private final YahooFinanceScraper yahooFinanceScraper;
    private final IbkrMarketDataService ibkrService;

    public QuoteService(YahooFinanceScraper yahooFinanceScraper,
                        IbkrMarketDataService ibkrService) {
        this.yahooFinanceScraper = yahooFinanceScraper;
        this.ibkrService = ibkrService;
    }

    /**
     * Quote priority:
     *   1. IBKR (realtime Bid/Ask, Last — exchange data, no scraping)
     *   2. Yahoo Finance (fallback if IBKR not connected or times out)
     */
    @Cacheable(value = "quotes", key = "#ticker")
    public QuoteData getQuote(String ticker) {
        String upper = ticker.toUpperCase();

        // --- Primary: IBKR ---
        try {
            QuoteData ibkrData = ibkrService.fetchQuote(upper);
            if (ibkrData != null && ibkrData.dataAvailable()) {
                log.debug("IBKR quote for {}: price={}", upper, ibkrData.price());
                return ibkrData;
            }
        } catch (Exception e) {
            log.warn("IBKR quote failed for {}, falling back to Yahoo: {}", upper, e.getMessage());
        }

        // --- Fallback: Yahoo Finance ---
        try {
            QuoteData data = yahooFinanceScraper.fetchQuote(upper);
            log.debug("Yahoo quote for {}: price={}", upper, data.price());
            return data;
        } catch (ScraperException e) {
            log.error("All quote sources failed for {}: {}", upper, e.getMessage());
            return QuoteData.empty(upper, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error fetching quote for {}: {}", upper, e.getMessage());
            return QuoteData.empty(upper, "Unexpected error: " + e.getMessage());
        }
    }
}
