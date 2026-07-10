package com.trading.marketdata.service;

import com.trading.marketdata.book.MarketDataBook;
import com.trading.marketdata.book.TickerBook;
import com.trading.marketdata.model.NewsItem;
import com.trading.marketdata.scraper.ScraperException;
import com.trading.marketdata.scraper.YahooFinanceScraper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * News priority mirrors the QuoteService pattern:
 *   1. MarketDataBook — IBKR news from the permanent streams (generic tick 292), contract-
 *      bound by IBKR itself and carrying the article BODY (fullText) fetched over the same
 *      Gateway socket. Never cached: the Book is fresher than any cache could be.
 *   2. Yahoo RSS — fills the remainder up to the limit and is the only source for non-Book
 *      tickers. Headline+link only (bodies unreachable: paywalls/bot-blocking), cached 60s
 *      via the existing "news" cache, manually, so Book reads never pass through it.
 *
 * Merged output is IBKR items first (they are strictly more valuable: contract-bound and
 * with body), then RSS items deduped against them by normalized headline — the same story
 * usually appears in both worlds with cosmetically different titles, so the dedupe is
 * fuzzy-lite (lowercase, collapsed whitespace) rather than exact.
 */
@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    private final YahooFinanceScraper yahooFinanceScraper;
    private final MarketDataBook book;
    private final CacheManager cacheManager;

    public NewsService(YahooFinanceScraper yahooFinanceScraper,
                       MarketDataBook book,
                       CacheManager cacheManager) {
        this.yahooFinanceScraper = yahooFinanceScraper;
        this.book = book;
        this.cacheManager = cacheManager;
    }

    public List<NewsItem> getNews(String ticker, int limit) {
        String upper = ticker.toUpperCase();
        int safeLimit = Math.min(Math.max(limit, 1), 50);

        List<NewsItem> merged = new ArrayList<>(safeLimit);
        Set<String> seenHeadlines = new HashSet<>();

        TickerBook tb = book.find(upper);
        List<NewsItem> ibkr = tb != null ? tb.news().value() : null;
        if (ibkr != null) {
            for (NewsItem item : ibkr) {
                if (merged.size() >= safeLimit) break;
                merged.add(item);
                seenHeadlines.add(normalize(item.headline()));
            }
        }

        if (merged.size() < safeLimit) {
            for (NewsItem item : fetchRssCached(upper, safeLimit)) {
                if (merged.size() >= safeLimit) break;
                if (seenHeadlines.add(normalize(item.headline()))) {
                    merged.add(item);
                }
            }
        }

        log.debug("News for {}: {} ibkr + {} total after RSS merge",
                upper, ibkr == null ? 0 : ibkr.size(), merged.size());
        return merged;
    }

    private List<NewsItem> fetchRssCached(String upper, int limit) {
        String key = upper + "_" + limit;
        Cache cache = cacheManager.getCache("news");
        if (cache != null) {
            @SuppressWarnings("unchecked")
            List<NewsItem> cached = cache.get(key, List.class);
            if (cached != null) return cached;
        }
        try {
            List<NewsItem> items = yahooFinanceScraper.fetchNews(upper, limit);
            if (cache != null && items != null && !items.isEmpty()) {
                cache.put(key, items);
            }
            return items != null ? items : List.of();
        } catch (ScraperException e) {
            log.error("Failed to fetch RSS news for {}: {}", upper, e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.error("Unexpected error fetching RSS news for {}: {}", upper, e.getMessage());
            return List.of();
        }
    }

    private static String normalize(String headline) {
        return headline == null ? "" : headline.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
