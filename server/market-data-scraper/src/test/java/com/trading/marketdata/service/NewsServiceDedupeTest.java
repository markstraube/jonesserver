package com.trading.marketdata.service;

import com.trading.marketdata.book.MarketDataBook;
import com.trading.marketdata.model.NewsItem;
import com.trading.marketdata.scraper.YahooFinanceScraper;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The merge dedupe in NewsService: the Book already collapses feed VARIANTS of one story
 * (same hash after '$' — see NewsItem.storyKey), so what reaches the service are distinct
 * articleIds. Distinct PROVIDERS (DJ vs Briefing) carrying the same story under different
 * ids are collapsed here by normalized headline — within the IBKR items and between IBKR
 * and RSS alike.
 */
class NewsServiceDedupeTest {

    private static NewsItem ibkr(String provider, String articleId, String headline) {
        return new NewsItem(headline, "ibkr:" + provider, null, Instant.now(), provider, articleId, "body");
    }

    /** fetchNews is the only method the service touches; the WebClient is never used. */
    private static YahooFinanceScraper rssStub(List<NewsItem> items) {
        return new YahooFinanceScraper(null, null) {
            @Override
            public List<NewsItem> fetchNews(String ticker, int limit) {
                return items;
            }
        };
    }

    @Test
    void sameHeadlineFromDifferentIbkrProvidersCollapses() {
        MarketDataBook book = new MarketDataBook();
        Instant now = Instant.now();
        // Different story hashes → both pass the Book's story-key dedupe; the service's
        // headline dedupe (case- and whitespace-insensitive) must collapse them.
        book.book("MU").appendNews(ibkr("DJ-N", "DJ-N$aaa", "Micron  Raises Guidance"), 25, now);
        book.book("MU").appendNews(ibkr("BRFG", "BRFG$bbb", "micron raises guidance"), 25, now);

        NewsService svc = new NewsService(rssStub(List.of()), book, new ConcurrentMapCacheManager("news"));
        assertEquals(1, svc.getNews("MU", 10).size());
    }

    @Test
    void rssDedupedAgainstIbkrAndFillsRemainder() {
        MarketDataBook book = new MarketDataBook();
        book.book("MU").appendNews(ibkr("DJ-N", "DJ-N$aaa", "Micron Raises Guidance"), 25, Instant.now());
        List<NewsItem> rss = List.of(
                new NewsItem("Micron raises guidance", "Yahoo Finance RSS", "http://x", Instant.now()),
                new NewsItem("HBM prices could double", "Yahoo Finance RSS", "http://y", Instant.now()));

        NewsService svc = new NewsService(rssStub(rss), book, new ConcurrentMapCacheManager("news"));
        List<NewsItem> out = svc.getNews("MU", 10);

        assertEquals(2, out.size());
        assertEquals("ibkr:DJ-N", out.get(0).source()); // IBKR first, RSS duplicate dropped
        assertEquals("HBM prices could double", out.get(1).headline());
    }
}
