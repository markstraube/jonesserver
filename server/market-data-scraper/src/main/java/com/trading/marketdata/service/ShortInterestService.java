package com.trading.marketdata.service;

import com.trading.marketdata.model.ShortData;
import com.trading.marketdata.scraper.FinvizScraper;
import com.trading.marketdata.scraper.ScraperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class ShortInterestService {

    private static final Logger log = LoggerFactory.getLogger(ShortInterestService.class);

    private final FinvizScraper finvizScraper;

    public ShortInterestService(FinvizScraper finvizScraper) {
        this.finvizScraper = finvizScraper;
    }

    @Cacheable(value = "short", key = "#ticker")
    public ShortData getShortData(String ticker) {
        String upper = ticker.toUpperCase();
        try {
            ShortData data = finvizScraper.fetchShortData(upper);
            log.debug("Fetched short data for {}: shortFloat={}%", upper, data.shortFloat());
            return data;
        } catch (ScraperException e) {
            log.error("Failed to fetch short data for {}: {}", upper, e.getMessage());
            return ShortData.empty(upper, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error fetching short data for {}: {}", upper, e.getMessage());
            return ShortData.empty(upper, "Unexpected error: " + e.getMessage());
        }
    }
}
