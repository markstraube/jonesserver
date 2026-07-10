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
    void appendKeepsNewestFirstAndDropsReplayedStory() {
        TickerBook tb = new TickerBook("MU");
        Instant now = Instant.now();
        assertTrue(tb.appendNews(item("a1", "first"), 25, now));
        assertTrue(tb.appendNews(item("a2", "second"), 25, now));
        // Re-tick of a1 (reconnect replays): dropped — the FIRST arrival stays (it may
        // already carry the async-attached body, which a replacement would lose)
        assertFalse(tb.appendNews(item("a1", "first again"), 25, now));

        List<NewsItem> news = tb.news().value();
        assertEquals(2, news.size());
        assertEquals("a2", news.get(0).articleId());
        assertEquals("a1", news.get(1).articleId());
        assertEquals("first", news.get(1).headline());
    }

    @Test
    void appendDedupesFeedVariantsByStoryKey() {
        // Observed live: DJ delivers one story once per feed variant — same hash after '$',
        // different provider prefix. Only the first variant may enter the Book.
        TickerBook tb = new TickerBook("MU");
        Instant now = Instant.now();
        assertTrue(tb.appendNews(item("DJ-RTG$1ede2dbc", "barrons story"), 25, now));
        assertFalse(tb.appendNews(item("DJ-RTE$1ede2dbc", "barrons story"), 25, now));
        assertFalse(tb.appendNews(item("DJ-N$1ede2dbc", "barrons story"), 25, now));
        // Same provider, different story hash: NOT a duplicate
        assertTrue(tb.appendNews(item("DJ-RTG$99999999", "other story"), 25, now));

        assertEquals(2, tb.news().value().size());
    }

    @Test
    void duplicateStoryStillRefreshesLastSeen() {
        TickerBook tb = new TickerBook("MU");
        Instant t0 = Instant.parse("2026-07-10T14:00:00Z");
        Instant t1 = t0.plusSeconds(30);
        tb.appendNews(item("DJ-RTG$abc", "story"), 25, t0);
        tb.appendNews(item("DJ-N$abc", "story"), 25, t1);

        // The duplicate is a real tick on the news line: seen moves, changed doesn't
        assertEquals(t1, tb.news().get().lastSeenAt());
        assertEquals(t0, tb.news().get().lastChangedAt());
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
    void attachArticleTextMatchesAcrossFeedVariants() {
        // The body fetched for one feed variant must reach the stored item even when the
        // stored item carries a different variant's articleId (same story hash).
        TickerBook tb = new TickerBook("MU");
        Instant now = Instant.now();
        tb.appendNews(item("DJ-RTG$1ede2dbc", "barrons story"), 25, now);

        assertTrue(tb.attachArticleText("DJ-N$1ede2dbc", "the body", now));
        assertEquals("the body", tb.news().value().get(0).fullText());
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
