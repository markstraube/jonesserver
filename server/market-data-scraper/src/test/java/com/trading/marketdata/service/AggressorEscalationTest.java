package com.trading.marketdata.service;

import com.trading.marketdata.analysis.AggressorClassifier;
import com.trading.marketdata.analysis.AggressorProfile;
import com.trading.marketdata.book.MarketDataBook;
import com.trading.marketdata.ibkr.HistoricalRequestBudget;
import com.trading.marketdata.ibkr.IbkrDayTicks;
import com.trading.marketdata.ibkr.IbkrMarketDataService;
import com.trading.marketdata.ibkr.IbkrRequestGovernor;
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
        return new OptionsData.UnusualActivity(FAR_EXPIRY, strike, type, volume, 500L,
                2.0, null, null, null, premium, null, null, "UNKNOWN", null);
    }

    /** Stub returning one canned buy-heavy day (10 buys at the ask) per fetch, costing 3 equivalents. */
    private static final String FAR_EXPIRY = "20301220";

    private static final class StubIbkr extends IbkrMarketDataService {
        final List<String> fetched = new ArrayList<>();
        /** The slice cap each fetch received — the fair-share observable. */
        final List<Integer> sliceCaps = new ArrayList<>();
        boolean connected = true;
        /** Equivalents each fetch consumes from its slice (real fetches vary; tests tune it). */
        int consumePerFetch = 3;

        StubIbkr() { super(null, null, new IbkrRequestGovernor()); }

        @Override
        public IbkrDayTicks fetchDayTicks(String ticker, String expiry, double strike, String right,
                                          ZonedDateTime sessionStartEt, HistoricalRequestBudget budget) {
            if (!connected) return null;
            fetched.add(strike + right);
            sliceCaps.add(budget.remaining());
            // Like the real fetch: pages consume until the budget refuses; a fetch cut short
            // drains what remains (partial pages) rather than leaving equivalents unused.
            if (!budget.tryConsume(consumePerFetch)) {
                budget.tryConsume(budget.remaining());
            }
            return new IbkrDayTicks(
                    List.of(new AggressorClassifier.Trade(T0 + 1, 6.0, 10, "CBOE", null, false)),
                    List.of(new AggressorClassifier.Quote(T0, 4.0, 6.0)),
                    false, null, 3); // null coverage = complete quote stream
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
        service.aggressorQualityMinDirectionalVolume = 1;
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

        // Budget 5 under FAIR SHARE: candidate 1 is offered 5/3=1, candidate 2 gets 5/2=2 —
        // both below the viable minimum (one TRADES + one BID_ASK page = 3) → explicit
        // SKIPPED_BUDGET without wasting a request; the pool stays intact until a share
        // clears the floor, so the LAST candidate receives the full 5 and runs. Every skip
        // is visible in the JSON — the invariant this test guards — but under fair share it
        // is the sub-viable EARLY shares that skip, not the late candidates.
        List<OptionsData.UnusualActivity> out =
                fx.service().escalateAggressor("MU", flagged, new HistoricalRequestBudget(5));

        assertEquals(List.of("110.0C"), fx.ibkr().fetched);
        assertEquals(AggressorProfile.STATUS_SKIPPED_BUDGET, out.get(0).aggressorProfile().status());
        assertEquals(AggressorProfile.STATUS_SKIPPED_BUDGET, out.get(1).aggressorProfile().status());
        assertEquals(AggressorProfile.STATUS_OK, out.get(2).aggressorProfile().status());
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
    void currentSessionFlowWaitsForNextSessionOiBeforeInference() {
        Fixture fx = fixture();
        // Yesterday's memory entry for the fixture contract (FAR_EXPIRY 100.0 C): OI 400.
        // Today's UA entry says 500. Key must match the fixture's expiry — a 0DTE expiry
        // here would route into the EXPIRES_TODAY branch and never consult the memory.
        var cache = fx.caches().getCache("oiContractDayMemory");
        String yesterday = java.time.LocalDate.now(java.time.ZoneId.of("America/New_York")).minusDays(1)
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        cache.put("MU:" + FAR_EXPIRY + ":100.0:C:" + yesterday, 400L);

        List<OptionsData.UnusualActivity> out = fx.service().escalateAggressor("MU",
                List.of(ua(100, "CALL", 5_000_000.0, 1000)), new HistoricalRequestBudget(20));

        AggressorProfile p = out.get(0).aggressorProfile();
        assertEquals(100L, p.oiDelta()); // 500 today - 400 previous session
        assertEquals("PENDING_NEXT_SESSION_OI", p.positionInference());
        assertEquals("NOT_AVAILABLE_INTRADAY", p.positionInferenceConfidence());
        assertTrue(p.positionInferenceReason().contains("next-session published OI"));
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

    private static OptionsData.UnusualActivity ua(String expiry, double strike, String type,
                                                  Double premium, long volume) {
        return new OptionsData.UnusualActivity(expiry, strike, type, volume, 500L,
                2.0, null, null, null, premium, null, null, "UNKNOWN", null);
    }

    // -------------------------------------------------------------------------
    // Fair-share budget + EXPIRES_TODAY
    // -------------------------------------------------------------------------

    @Test
    void fairShareStopsOneCandidateFromStarvingTheRest() {
        // The live failure mode this guards against: budget 20, first candidate would
        // consume everything, three SKIPPED_BUDGET. With fair share, candidate 1 is capped
        // at 20/4 = 5; the rest keep their turn.
        Fixture fx = fixture();
        fx.ibkr().consumePerFetch = 100; // a whale: eats every equivalent its slice allows
        List<OptionsData.UnusualActivity> flagged = List.of(
                ua(100, "CALL", 9_000_000.0, 12_000),
                ua(105, "CALL", 7_000_000.0, 8_000),
                ua(110, "CALL", 5_000_000.0, 6_000),
                ua(115, "CALL", 3_000_000.0, 4_000));

        List<OptionsData.UnusualActivity> out =
                fx.service().escalateAggressor("MU", flagged, new HistoricalRequestBudget(20));

        // All four were fetched — nobody starved…
        assertEquals(4, fx.ibkr().fetched.size());
        // …because each got exactly the equal split of what remained: 20/4, 15/3, 10/2, 5/1.
        assertEquals(List.of(5, 5, 5, 5), fx.ibkr().sliceCaps);
        assertTrue(out.stream().allMatch(u -> u.aggressorProfile() != null));
        assertTrue(out.stream().noneMatch(
                u -> AggressorProfile.STATUS_SKIPPED_BUDGET.equals(u.aggressorProfile().status())));
    }

    @Test
    void unusedShareFlowsBackToLaterCandidates() {
        // Slices cap, they do not reserve: frugal candidates grow the share of those after
        // them. consumePerFetch=3 → shares 20/4=5(use 3), 17/3=5(3), 14/2=7(3), 11/1=11(3).
        Fixture fx = fixture();
        fx.ibkr().consumePerFetch = 3;
        List<OptionsData.UnusualActivity> flagged = List.of(
                ua(100, "CALL", 9_000_000.0, 1000),
                ua(105, "CALL", 7_000_000.0, 1000),
                ua(110, "CALL", 5_000_000.0, 1000),
                ua(115, "CALL", 3_000_000.0, 1000));

        fx.service().escalateAggressor("MU", flagged, new HistoricalRequestBudget(20));

        assertEquals(List.of(5, 5, 7, 11), fx.ibkr().sliceCaps);
    }

    @Test
    void subViableShareSkipsInsteadOfSendingUselessFragments() {
        // Budget 5, four candidates: candidate 1 gets 5/4=1 < 3 (min viable: one TRADES page
        // + one BID_ASK page) → skip; the pool stays intact until a share clears the floor.
        // Shares: 5/4=1 skip, 5/3=1 skip, 5/2=2 skip, 5/1=5 → the LAST candidate runs.
        Fixture fx = fixture();
        List<OptionsData.UnusualActivity> flagged = List.of(
                ua(100, "CALL", 9_000_000.0, 1000),
                ua(105, "CALL", 7_000_000.0, 1000),
                ua(110, "CALL", 5_000_000.0, 1000),
                ua(115, "CALL", 3_000_000.0, 1000));

        List<OptionsData.UnusualActivity> out =
                fx.service().escalateAggressor("MU", flagged, new HistoricalRequestBudget(5));

        assertEquals(List.of("115.0C"), fx.ibkr().fetched);
        long skipped = out.stream().filter(u -> u.aggressorProfile() != null
                && AggressorProfile.STATUS_SKIPPED_BUDGET.equals(u.aggressorProfile().status())).count();
        assertEquals(3, skipped);
    }

    @Test
    void contractsExpiringTodayGetStructuralLabelNotUnknown() {
        Fixture fx = fixture();
        String today = java.time.LocalDate.now(java.time.ZoneId.of("America/New_York"))
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        List<OptionsData.UnusualActivity> flagged = List.of(
                ua(today, 100, "CALL", 9_000_000.0, 1000),  // 0DTE
                ua(105, "CALL", 7_000_000.0, 1000));        // far expiry

        List<OptionsData.UnusualActivity> out =
                fx.service().escalateAggressor("MU", flagged, new HistoricalRequestBudget(20));

        assertEquals(AggressorProfile.INFERENCE_EXPIRES_TODAY, out.get(0).aggressorProfile().positionInference());
        assertNull(out.get(0).aggressorProfile().oiDelta());
        // The far expiry keeps the normal join path (UNKNOWN here: no previous-session OI in the cache)
        assertEquals("UNKNOWN", out.get(1).aggressorProfile().positionInference());
    }
}
