package com.trading.marketdata.service;

import com.trading.marketdata.model.NewsItem;
import com.trading.marketdata.scraper.ScraperException;
import com.trading.marketdata.scraper.YahooFinanceScraper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    private final YahooFinanceScraper yahooFinanceScraper;

    public NewsService(YahooFinanceScraper yahooFinanceScraper) {
        this.yahooFinanceScraper = yahooFinanceScraper;
    }

    @Cacheable(value = "news", key = "#ticker + '_' + #limit")
    public List<NewsItem> getNews(String ticker, int limit) {
        String upper = ticker.toUpperCase();
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        try {
            List<NewsItem> items = yahooFinanceScraper.fetchNews(upper, safeLimit);
            log.debug("Fetched {} news items for {}", items.size(), upper);
            return items;
        } catch (ScraperException e) {
            log.error("Failed to fetch news for {}: {}", upper, e.getMessage());
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Unexpected error fetching news for {}: {}", upper, e.getMessage());
            return new ArrayList<>();
        }
    }
}
