package com.trading.marketdata.service;

import com.trading.marketdata.book.MarketDataBook;
import com.trading.marketdata.book.TickerBook;
import com.trading.marketdata.model.DataQuality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotQualityServiceTest {

    /** Hand-rolled stubs instead of Mockito: the inline mock maker cannot instrument
     *  classes on this JDK, and the two collaborators are trivial to fake. */
    static class FixedMarketState extends MarketStateService {
        volatile MarketState state = MarketState.REGULAR;
        FixedMarketState() { super(null, null); }
        @Override public MarketState getMarketState() { return state; }
    }

    static class FixedAuctionWindow extends AuctionService {
        FixedAuctionWindow() { super(null); }
        @Override boolean isInsideAuctionWindow() { return false; }
    }

    private MarketDataBook book;
    private FixedMarketState marketState;
    private SnapshotQualityService service;

    @BeforeEach
    void setUp() {
        book = new MarketDataBook();
        marketState = new FixedMarketState();
        service = new SnapshotQualityService(book, marketState, new FixedAuctionWindow());
        ReflectionTestUtils.setField(service, "quoteStaleSeconds", 120L);
        ReflectionTestUtils.setField(service, "metricsStaleSeconds", 600L);
        ReflectionTestUtils.setField(service, "auctionMaxAgeSeconds", 180L);
        ReflectionTestUtils.setField(service, "scanStaleSeconds", 1800L);
    }

    @Test
    void nonBookTickerHasNoQuality() {
        assertNull(service.forTicker("AAPL"));
    }

    @Test
    void freshQuoteStaleIvIsFlaggedPerSection() {
        // The phase-4 verification scenario: fresh quote, deliberately stale IV — the
        // snapshot must carry a quality block with IV flagged stale instead of being
        // blocked wholesale.
        TickerBook mu = book.book("MU");
        Instant now = Instant.now();
        mu.last().update(940.0, now);
        mu.volume().update(1_000_000L, now);
        mu.impliedVolatility().update(0.55, now.minusSeconds(3600)); // stale during REGULAR

        DataQuality q = service.forTicker("MU");
        assertFalse(q.connectionLost());
        assertFalse(q.quote().stale(), "fresh quote must not be stale");
        assertTrue(q.optionsMetrics().stale(), "hour-old IV during REGULAR is stale");
        assertEquals(3600L, q.optionsMetrics().ageSeconds());
        assertTrue(q.uaScan().stale(), "never-scanned section is stale");
        assertNull(q.uaScan().ageSeconds());
    }

    @Test
    void silenceOutsideRegularIsNotStale() {
        marketState.state = MarketStateService.MarketState.PRE;
        TickerBook mu = book.book("MU");
        mu.last().update(940.0, Instant.now().minusSeconds(7200));
        mu.volume().update(1_000_000L, Instant.now().minusSeconds(7200));

        DataQuality q = service.forTicker("MU");
        assertFalse(q.quote().stale(), "pre-market silence is stillness, not failure");
    }

    @Test
    void notSubscribedGroupIsFlaggedInQuality() {
        TickerBook spy = book.book("SPY");
        spy.last().update(560.0, Instant.now());
        spy.volume().update(1_000L, Instant.now());
        spy.markNotSubscribedGenericTick(225); // error 10090: ARCA/Auction:225

        DataQuality q = service.forTicker("SPY");
        assertEquals(Boolean.TRUE, q.auction().notSubscribed());
        assertNull(q.quote().notSubscribed(), "default-tick section is unaffected");
        assertFalse(q.quote().stale());
    }

    @Test
    void connectionLossMakesEverythingStaleButAgesFreeze() {
        TickerBook mu = book.book("MU");
        Instant t = Instant.now().minusSeconds(5);
        mu.last().update(940.0, t);
        book.invalidateAll("test");

        DataQuality q = service.forTicker("MU");
        assertTrue(q.connectionLost());
        assertTrue(q.quote().stale());
        assertTrue(q.quote().invalidated());
        assertEquals(5L, q.quote().ageSeconds(), "ages freeze at the last tick, they don't reset");
    }
}
