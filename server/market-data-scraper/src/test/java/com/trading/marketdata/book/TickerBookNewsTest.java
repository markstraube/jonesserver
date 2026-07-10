package com.trading.marketdata.book;

import com.trading.marketdata.model.NewsItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TickerBookNewsTest {

    private static NewsItem item(String articleId, String headline) {
        return new NewsItem(headline, "ibkr:BRFG", null, Instant.now(), "BRFG", articleId, null);
    }

    @Test
    void appendKeepsNewestFirstAndDedupesByArticleId() {
        TickerBook tb = new TickerBook("MU");
        Instant now = Instant.now();
        tb.appendNews(item("a1", "first"), 25, now);
        tb.appendNews(item("a2", "second"), 25, now);
        // Re-tick of a1 (reconnect replays): must not duplicate, the re-ticked copy wins the top slot
        tb.appendNews(item("a1", "first again"), 25, now);

        List<NewsItem> news = tb.news().value();
        assertEquals(2, news.size());
        assertEquals("a1", news.get(0).articleId());
        assertEquals("first again", news.get(0).headline());
        assertEquals("a2", news.get(1).articleId());
    }

    @Test
    void appendEnforcesCap() {
        TickerBook tb = new TickerBook("MU");
        Instant now = Instant.now();
        for (int i = 0; i < 10; i++) {
            tb.appendNews(item("a" + i, "h" + i), 3, now);
        }
        List<NewsItem> news = tb.news().value();
        assertEquals(3, news.size());
        assertEquals("a9", news.get(0).articleId()); // newest first
        assertEquals("a7", news.get(2).articleId()); // oldest surviving
    }

    @Test
    void attachArticleTextHitsMatchingItemOnly() {
        TickerBook tb = new TickerBook("MU");
        Instant now = Instant.now();
        tb.appendNews(item("a1", "first"), 25, now);
        tb.appendNews(item("a2", "second"), 25, now);

        assertTrue(tb.attachArticleText("a1", "the body", now));
        List<NewsItem> news = tb.news().value();
        assertEquals("the body", news.get(1).fullText());
        assertNull(news.get(0).fullText());

        // Rotated-out or unknown article: a miss, not an error
        assertFalse(tb.attachArticleText("gone", "x", now));
        // Already-attached body is not overwritten (fullText == null guard)
        assertFalse(tb.attachArticleText("a1", "other body", now));
        assertEquals("the body", tb.news().value().get(1).fullText());
    }

    @Test
    void appendMovesTimestampsAndClearsFlags() {
        TickerBook tb = new TickerBook("MU");
        tb.news().markNotSubscribed();
        assertTrue(tb.news().get().notSubscribed());

        tb.appendNews(item("a1", "h"), 25, Instant.now());
        assertFalse(tb.news().get().notSubscribed()); // an arriving tick proves the opposite
        assertNotNull(tb.news().get().lastSeenAt());
        assertNotNull(tb.news().get().lastChangedAt());
    }

    @Test
    void genericTick292MapsToNewsGroup() {
        TickerBook tb = new TickerBook("SPY");
        assertTrue(tb.markNotSubscribedGenericTick(292));
        assertTrue(tb.news().get().notSubscribed());
    }
}
