package com.trading.marketdata.service;

import com.trading.marketdata.analysis.AggressorClassifier;
import com.trading.marketdata.analysis.AggressorProfile;
import com.trading.marketdata.book.MarketDataBook;
import com.trading.marketdata.ibkr.HistoricalRequestBudget;
import com.trading.marketdata.ibkr.IbkrDayTicks;
import com.trading.marketdata.ibkr.IbkrMarketDataService;
import com.trading.marketdata.model.OptionsData;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage-2 escalation wiring: candidate selection by premium notional, per-cycle budget
 * accounting with explicit SKIPPED_BUDGET markers, order preservation, and the
 * positionInference join. The tick fetch is stubbed (no Mockito — plain subclass, the
 * ibkr constructor args are unused by the stub).
 */
class AggressorEscalationTest {

    private static final long T0 = 1_770_000_000L;

    private static OptionsData.UnusualActivity ua(double strike, String type, Double premium, long volume) {
        return new OptionsData.UnusualActivity("20260710", strike, type, volume, 500L,
                2.0, null, null, null, premium, null, null, "UNKNOWN", null);
    }

    /** Stub returning one canned buy-heavy day (10 buys at the ask) per fetch, costing 3 equivalents. */
    private static final class StubIbkr extends IbkrMarketDataService {
        final List<String> fetched = new ArrayList<>();
        boolean connected = true;

        StubIbkr() { super(null, null); }

        @Override
        public IbkrDayTicks fetchDayTicks(String ticker, String expiry, double strike, String right,
                                          ZonedDateTime sessionStartEt, HistoricalRequestBudget budget) {
            if (!connected) return null;
            fetched.add(strike + right);
            // Like the real fetch: pages consume until the budget refuses; a fetch cut short
            // drains what remains (partial pages) rather than leaving equivalents unused.
            if (!budget.tryConsume(3)) {
                budget.tryConsume(budget.remaining());
            }
            return new IbkrDayTicks(
                    List.of(new AggressorClassifier.Trade(T0 + 1, 6.0, 10, "CBOE", null, false)),
                    List.of(new AggressorClassifier.Quote(T0, 4.0, 6.0)),
                    false, 3);
        }
    }

    private record Fixture(OptionActivityService service, StubIbkr ibkr, ConcurrentMapCacheManager caches) {}

    private static Fixture fixture() {
        StubIbkr ibkr = new StubIbkr();
        ConcurrentMapCacheManager caches = new ConcurrentMapCacheManager("oiContractDayMemory");
        OptionActivityService service = new OptionActivityService(
                ibkr, null, caches, null, new MarketDataBook(), null);
        service.aggressorEnabled = true;
        service.aggressorMaxCandidates = 4;
        service.aggressorMaxRequests = 20;
        service.aggressorSweepWindowMs = 500;
        service.aggressorBlockMinContracts = 100;
        service.aggressorSpreadMarkers = List.of("SLAN");
        service.aggressorSessionStart = "09:30";
        return new Fixture(service, ibkr, caches);
    }

    @Test
    void disabledStagePassesListThroughUntouched() {
        Fixture fx = fixture();
        fx.service().aggressorEnabled = false;
        List<OptionsData.UnusualActivity> flagged = List.of(ua(100, "CALL", 5_000_000.0, 1000));

        List<OptionsData.UnusualActivity> out =
                fx.service().escalateAggressor("MU", flagged, new HistoricalRequestBudget(20));

        assertSame(flagged, out); // bit-for-bit: the very same list instance
        assertTrue(fx.ibkr().fetched.isEmpty());
    }

    @Test
    void candidatesRankByPremiumNotionalWithNullsLast() {
        Fixture fx = fixture();
        fx.service().aggressorMaxCandidates = 2;
        List<OptionsData.UnusualActivity> flagged = List.of(
                ua(100, "CALL", 1_000_000.0, 1000),   // rank 3 — beyond the candidate cap
                ua(105, "CALL", null, 1000),          // no premium → ranks last
                ua(110, "PUT", 9_000_000.0, 1000),    // rank 1
                ua(115, "CALL", 2_000_000.0, 1000));  // rank 2

        List<OptionsData.UnusualActivity> out =
                fx.service().escalateAggressor("MU", flagged, new HistoricalRequestBudget(20));

        assertEquals(List.of("110.0P", "115.0C"), fx.ibkr().fetched);
        // Original order preserved; only the two candidates carry profiles
        assertNull(out.get(0).aggressorProfile());
        assertNull(out.get(1).aggressorProfile());
        assertNotNull(out.get(2).aggressorProfile());
        assertNotNull(out.get(3).aggressorProfile());
        assertEquals(10, out.get(2).aggressorProfile().buyVolume());
    }

    @Test
    void exhaustedBudgetMarksRemainingCandidatesSkipped() {
        Fixture fx = fixture();
        List<OptionsData.UnusualActivity> flagged = List.of(
                ua(100, "CALL", 9_000_000.0, 1000),
                ua(105, "CALL", 8_000_000.0, 1000),
                ua(110, "CALL", 7_000_000.0, 1000));

        // Budget 5: candidate 1 consumes 3, candidate 2 consumes the last 2-ish (stub takes
        // what it can), candidate 3 sees remaining <= 0 → SKIPPED_BUDGET without a fetch.
        List<OptionsData.UnusualActivity> out =
                fx.service().escalateAggressor("MU", flagged, new HistoricalRequestBudget(5));

        assertEquals(2, fx.ibkr().fetched.size());
        assertEquals(AggressorProfile.STATUS_OK, out.get(0).aggressorProfile().status());
        assertEquals(AggressorProfile.STATUS_OK, out.get(1).aggressorProfile().status());
        assertEquals(AggressorProfile.STATUS_SKIPPED_BUDGET, out.get(2).aggressorProfile().status());
    }

    @Test
    void notConnectedLeavesProfileHonestlyAbsent() {
        Fixture fx = fixture();
        fx.ibkr().connected = false;
        List<OptionsData.UnusualActivity> out = fx.service().escalateAggressor("MU",
                List.of(ua(100, "CALL", 5_000_000.0, 1000)), new HistoricalRequestBudget(20));
        assertNull(out.get(0).aggressorProfile());
    }

    @Test
    void oiDeltaJoinsAgainstPreviousSessionMemory() {
        Fixture fx = fixture();
        // Yesterday's memory entry for MU 20260710 100.0 C: OI 400. Today's UA entry says 500.
        var cache = fx.caches().getCache("oiContractDayMemory");
        String yesterday = java.time.LocalDate.now(java.time.ZoneId.of("America/New_York")).minusDays(1)
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        cache.put("MU:20260710:100.0:C:" + yesterday, 400L);

        List<OptionsData.UnusualActivity> out = fx.service().escalateAggressor("MU",
                List.of(ua(100, "CALL", 5_000_000.0, 1000)), new HistoricalRequestBudget(20));

        AggressorProfile p = out.get(0).aggressorProfile();
        assertEquals(100L, p.oiDelta()); // 500 today - 400 previous session
        assertEquals("OPENING_BUYS", p.positionInference()); // stub day is 100% buys, OI up
    }

    @Test
    void noMemoryMeansUnknownInference() {
        Fixture fx = fixture();
        List<OptionsData.UnusualActivity> out = fx.service().escalateAggressor("MU",
                List.of(ua(100, "CALL", 5_000_000.0, 1000)), new HistoricalRequestBudget(20));
        AggressorProfile p = out.get(0).aggressorProfile();
        assertNull(p.oiDelta());
        assertEquals("UNKNOWN", p.positionInference());
    }

    // -------------------------------------------------------------------------
    // positionInference truth table (pure function)
    // -------------------------------------------------------------------------

    @Test
    void positionInferenceTruthTable() {
        assertEquals("OPENING_BUYS",   AggressorClassifier.positionInference(80, 20, 500L));
        assertEquals("SHORT_COVER",    AggressorClassifier.positionInference(80, 20, -500L));
        assertEquals("OPENING_WRITES", AggressorClassifier.positionInference(20, 80, 500L));
        assertEquals("CLOSING_SALES",  AggressorClassifier.positionInference(20, 80, -500L));
        assertEquals("MIXED",          AggressorClassifier.positionInference(50, 50, 500L));  // no 60% dominance
        assertEquals("MIXED",          AggressorClassifier.positionInference(80, 20, 0L));    // dominant, flat OI
        assertEquals("UNKNOWN",        AggressorClassifier.positionInference(80, 20, null));  // no previous OI
        assertEquals("UNKNOWN",        AggressorClassifier.positionInference(0, 0, 500L));    // no directional flow
        assertEquals("OPENING_BUYS",   AggressorClassifier.positionInference(60, 40, 1L));    // exactly 60% = dominant
    }
}
