package com.trading.marketdata.analysis;

import com.trading.marketdata.analysis.AggressorClassifier.Config;
import com.trading.marketdata.analysis.AggressorClassifier.Quote;
import com.trading.marketdata.analysis.AggressorClassifier.Trade;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The classifier core is pure — this battery pins down every §6.2 edge case: quote-join
 * collisions at one-second resolution, classification boundaries, sweep grouping rules,
 * exclusion accounting, and the coverage arithmetic.
 */
class AggressorClassifierTest {

    private static final Config CFG = new Config(500, 100, List.of("SPREAD", "LEG"));

    /** T0 as a readable base epoch (any second-aligned instant works, the logic is relative). */
    private static final long T0 = 1_770_000_000L;

    private static Trade trade(long t, double price, long size) {
        return new Trade(t, price, size, "CBOE", null, false);
    }

    private static Trade trade(long t, double price, long size, String exchange) {
        return new Trade(t, price, size, exchange, null, false);
    }

    private static Quote quote(long t, double bid, double ask) {
        return new Quote(t, bid, ask);
    }

    private static AggressorProfile classify(List<Trade> trades, List<Quote> quotes) {
        return AggressorClassifier.classify(trades, quotes, CFG, null, false);
    }

    // -------------------------------------------------------------------------
    // Quote join
    // -------------------------------------------------------------------------

    @Test
    void tradeBeforeFirstQuoteIsUnknown() {
        AggressorProfile p = classify(
                List.of(trade(T0, 5.0, 10)),
                List.of(quote(T0 + 1, 4.0, 6.0)));
        assertEquals(10, p.unknownVolume());
        assertEquals(0, p.buyVolume());
        assertEquals(0, p.sellVolume());
    }

    @Test
    void strictlyEarlierQuoteBeatsSameSecondQuote() {
        // Earlier second: 4/6 (trade at 6 = BUY). Same second: 6/8 (trade at 6 would be SELL).
        // Intra-second order is unknowable — the earlier-second quote must win.
        AggressorProfile p = classify(
                List.of(trade(T0 + 1, 6.0, 10)),
                List.of(quote(T0, 4.0, 6.0), quote(T0 + 1, 6.0, 8.0)));
        assertEquals(10, p.buyVolume());
        assertEquals(10, p.buyStrongVolume());
    }

    @Test
    void sameSecondOnlyCandidatesUseLastOfThem() {
        // Both quotes share the trade's second → last of them (arrival order) applies:
        // 5/7 makes the 7.0 print a BUY (first quote 6/8 would have made it BUY_LEAN zone).
        AggressorProfile p = classify(
                List.of(trade(T0, 7.0, 10)),
                List.of(quote(T0, 6.0, 8.0), quote(T0, 5.0, 7.0)));
        assertEquals(10, p.buyStrongVolume());
    }

    @Test
    void crossedLockedAndOneSidedQuotesAreUnknown() {
        AggressorProfile crossed = classify(
                List.of(trade(T0 + 1, 5.0, 10)),
                List.of(quote(T0, 6.0, 4.0)));  // ask < bid
        AggressorProfile locked = classify(
                List.of(trade(T0 + 1, 5.0, 10)),
                List.of(quote(T0, 5.0, 5.0)));  // ask == bid
        AggressorProfile zeroBid = classify(
                List.of(trade(T0 + 1, 5.0, 10)),
                List.of(quote(T0, 0.0, 5.0)));  // no real bid
        assertEquals(10, crossed.unknownVolume());
        assertEquals(10, locked.unknownVolume());
        assertEquals(10, zeroBid.unknownVolume());
    }

    // -------------------------------------------------------------------------
    // Classification boundaries. Quote 100/200 (spread 100) so the threshold positions
    // are EXACT in binary floating point: (165-100)/100 == 0.65 as a double, whereas a
    // 2.00-spread quote puts 0.6499999999… against the literal and tests fp noise instead
    // of the boundary.
    // -------------------------------------------------------------------------

    private AggressorProfile classifyAtPrice(double price) {
        return classify(
                List.of(trade(T0 + 1, price, 10)),
                List.of(quote(T0, 100.0, 200.0)));
    }

    @Test
    void classificationBoundaries() {
        assertEquals(10, classifyAtPrice(200.0).buyStrongVolume());  // exactly at ask → BUY
        assertEquals(10, classifyAtPrice(250.0).buyStrongVolume());  // through the ask → BUY
        assertEquals(10, classifyAtPrice(100.0).sellStrongVolume()); // exactly at bid → SELL
        assertEquals(10, classifyAtPrice(90.0).sellStrongVolume());  // through the bid → SELL
        assertEquals(10, classifyAtPrice(165.0).buyLeanVolume());    // pos = 0.65 exactly → BUY_LEAN
        assertEquals(10, classifyAtPrice(135.0).sellLeanVolume());   // pos = 0.35 exactly → SELL_LEAN
        assertEquals(10, classifyAtPrice(150.0).unknownVolume());    // midpoint zone → UNKNOWN
        assertEquals(10, classifyAtPrice(164.0).unknownVolume());    // just below 0.65 → UNKNOWN
        assertEquals(10, classifyAtPrice(136.0).unknownVolume());    // just above 0.35 → UNKNOWN
    }

    @Test
    void leanBucketsAggregateIntoDirectionalTotals() {
        AggressorProfile p = classify(
                List.of(trade(T0 + 1, 6.0, 10),   // BUY
                        trade(T0 + 1, 5.4, 20),   // BUY_LEAN (pos 0.7)
                        trade(T0 + 1, 4.0, 30),   // SELL
                        trade(T0 + 1, 4.6, 40)),  // SELL_LEAN (pos 0.3)
                List.of(quote(T0, 4.0, 6.0)));
        assertEquals(30, p.buyVolume());
        assertEquals(10, p.buyStrongVolume());
        assertEquals(20, p.buyLeanVolume());
        assertEquals(70, p.sellVolume());
        assertEquals(30, p.sellStrongVolume());
        assertEquals(40, p.sellLeanVolume());
    }

    // -------------------------------------------------------------------------
    // Exclusions
    // -------------------------------------------------------------------------

    @Test
    void spreadLegsAreExcludedNotClassified() {
        AggressorProfile p = classify(
                List.of(new Trade(T0 + 1, 6.0, 25, "CBOE", "Spread leg of combo", false),
                        trade(T0 + 1, 6.0, 10)),
                List.of(quote(T0, 4.0, 6.0)));
        assertEquals(25, p.excludedSpreadVolume());
        assertEquals(10, p.buyVolume()); // only the clean print classified
        assertEquals(0, p.unknownVolume());
    }

    @Test
    void unreportedPrintsAreExcludedSeparately() {
        AggressorProfile p = classify(
                List.of(new Trade(T0 + 1, 6.0, 15, "CBOE", null, true),
                        trade(T0 + 1, 6.0, 10)),
                List.of(quote(T0, 4.0, 6.0)));
        assertEquals(15, p.excludedUnreportedVolume());
        assertEquals(0, p.excludedSpreadVolume());
        assertEquals(10, p.buyVolume());
    }

    @Test
    void spreadLegThatIsAlsoUnreportedCountsOnceAsSpread() {
        AggressorProfile p = classify(
                List.of(new Trade(T0 + 1, 6.0, 25, "CBOE", "LEG", true)),
                List.of(quote(T0, 4.0, 6.0)));
        assertEquals(25, p.excludedSpreadVolume());
        assertEquals(0, p.excludedUnreportedVolume());
    }

    @Test
    void volumeConservationInvariant() {
        // §7 phase-2 verification rule: buy + sell + unknown + excluded == analyzed volume.
        List<Trade> trades = List.of(
                trade(T0 + 1, 6.0, 10),                                   // BUY
                trade(T0 + 1, 4.0, 20),                                   // SELL
                trade(T0 + 1, 5.0, 30),                                   // UNKNOWN (mid)
                new Trade(T0 + 1, 5.0, 40, "CBOE", "SPREAD", false),      // excluded spread
                new Trade(T0 + 1, 5.0, 50, "CBOE", null, true));          // excluded unreported
        AggressorProfile p = AggressorClassifier.classify(trades, List.of(quote(T0, 4.0, 6.0)),
                CFG, 300L, false);
        long analyzed = p.buyVolume() + p.sellVolume() + p.unknownVolume()
                + p.excludedSpreadVolume() + p.excludedUnreportedVolume();
        assertEquals(150, analyzed);
        assertEquals(150 / 300.0, p.tickCoverage(), 1e-9);
    }

    // -------------------------------------------------------------------------
    // Sweeps
    // -------------------------------------------------------------------------

    @Test
    void sameSecondMultiExchangeSameDirectionRunIsOneSweep() {
        AggressorProfile p = classify(
                List.of(trade(T0 + 1, 6.0, 10, "CBOE"),
                        trade(T0 + 1, 6.0, 20, "ISE"),
                        trade(T0 + 1, 5.4, 30, "PHLX")), // LEAN counts toward its direction
                List.of(quote(T0, 4.0, 6.0)));
        assertEquals(1, p.sweepCount());
        assertEquals(60, p.sweepVolume());
        assertEquals(60, p.largestSweepVolume());
    }

    @Test
    void singleExchangeRunIsNotASweep() {
        AggressorProfile p = classify(
                List.of(trade(T0 + 1, 6.0, 10, "CBOE"),
                        trade(T0 + 1, 6.0, 20, "CBOE"),
                        trade(T0 + 1, 6.0, 30, "CBOE")),
                List.of(quote(T0, 4.0, 6.0)));
        assertEquals(0, p.sweepCount());
    }

    @Test
    void mixedDirectionRunIsNotASweep() {
        AggressorProfile p = classify(
                List.of(trade(T0 + 1, 6.0, 10, "CBOE"),
                        trade(T0 + 1, 4.0, 20, "ISE"),   // SELL breaks the buy run
                        trade(T0 + 1, 6.0, 30, "PHLX")),
                List.of(quote(T0, 4.0, 6.0)));
        assertEquals(0, p.sweepCount());
    }

    @Test
    void printsOutsideTheWindowDoNotJoinTheRun() {
        // Window 500ms at 1s resolution → only same-second prints group. The third print
        // one second later must not complete the run; on its own (2 prints) it's no sweep.
        AggressorProfile p = classify(
                List.of(trade(T0 + 1, 6.0, 10, "CBOE"),
                        trade(T0 + 1, 6.0, 20, "ISE"),
                        trade(T0 + 2, 6.0, 30, "PHLX")),
                List.of(quote(T0, 4.0, 6.0)));
        assertEquals(0, p.sweepCount());
    }

    @Test
    void unknownPrintBreaksARun() {
        AggressorProfile p = classify(
                List.of(trade(T0 + 1, 6.0, 10, "CBOE"),
                        trade(T0 + 1, 5.0, 5, "ISE"),    // midpoint → UNKNOWN, breaks the run
                        trade(T0 + 1, 6.0, 20, "PHLX"),
                        trade(T0 + 1, 6.0, 30, "AMEX")),
                List.of(quote(T0, 4.0, 6.0)));
        assertEquals(0, p.sweepCount()); // neither fragment reaches 3 prints
    }

    @Test
    void twoSeparateSweepsAreCountedSeparately() {
        AggressorProfile p = classify(
                List.of(trade(T0 + 1, 6.0, 10, "CBOE"),
                        trade(T0 + 1, 6.0, 20, "ISE"),
                        trade(T0 + 1, 6.0, 30, "PHLX"),   // sweep 1: 60 contracts, BUY
                        trade(T0 + 5, 4.0, 40, "CBOE"),
                        trade(T0 + 5, 4.0, 50, "ISE"),
                        trade(T0 + 5, 4.6, 60, "PHLX")),  // sweep 2: 150 contracts, SELL
                List.of(quote(T0, 4.0, 6.0)));
        assertEquals(2, p.sweepCount());
        assertEquals(210, p.sweepVolume());
        assertEquals(150, p.largestSweepVolume());
    }

    // -------------------------------------------------------------------------
    // Blocks
    // -------------------------------------------------------------------------

    @Test
    void blocksCountRegardlessOfClassificationBucket() {
        AggressorProfile p = classify(
                List.of(trade(T0 + 1, 5.0, 150),   // midpoint UNKNOWN, still a block
                        trade(T0 + 1, 6.0, 100),   // BUY, exactly at the floor
                        trade(T0 + 1, 6.0, 99)),   // below the floor
                List.of(quote(T0, 4.0, 6.0)));
        assertEquals(2, p.blockCount());
        assertEquals(250, p.blockVolume());
        assertEquals(150, p.largestBlockVolume());
    }

    @Test
    void excludedPrintsAreNotBlocks() {
        AggressorProfile p = classify(
                List.of(new Trade(T0 + 1, 5.0, 500, "CBOE", "SPREAD", false)),
                List.of(quote(T0, 4.0, 6.0)));
        assertEquals(0, p.blockCount());
        assertEquals(500, p.excludedSpreadVolume());
    }

    // -------------------------------------------------------------------------
    // Aggregates: notional, VWAP, window, coverage, status
    // -------------------------------------------------------------------------

    @Test
    void notionalAndVwapArithmetic() {
        AggressorProfile p = classify(
                List.of(trade(T0 + 1, 6.0, 10),    // BUY:  60 price*size
                        trade(T0 + 1, 5.4, 30),    // BUY_LEAN: 162
                        trade(T0 + 2, 4.0, 20)),   // SELL: 80
                List.of(quote(T0, 4.0, 6.0)));
        assertEquals((60 + 162) * 100, p.buyNotionalUsd(), 1e-9);
        assertEquals(80 * 100, p.sellNotionalUsd(), 1e-9);
        assertEquals((60 + 162) / 40.0, p.vwapBuy(), 1e-9);
        assertEquals(4.0, p.vwapSell(), 1e-9);
    }

    @Test
    void windowTimestampsSpanAllFetchedTrades() {
        AggressorProfile p = classify(
                List.of(trade(T0 + 10, 6.0, 10),
                        new Trade(T0 + 99, 5.0, 40, "CBOE", "SPREAD", false), // excluded, still in window
                        trade(T0 + 50, 4.0, 20)),
                List.of(quote(T0, 4.0, 6.0)));
        assertEquals(Instant.ofEpochSecond(T0 + 10), p.firstTradeAt());
        assertEquals(Instant.ofEpochSecond(T0 + 99), p.lastTradeAt());
    }

    @Test
    void coverageNullWithoutDayVolumeAndCappedMathOtherwise() {
        AggressorProfile noDayVolume = AggressorClassifier.classify(
                List.of(trade(T0 + 1, 6.0, 10)), List.of(quote(T0, 4.0, 6.0)), CFG, null, false);
        assertNull(noDayVolume.tickCoverage());

        AggressorProfile half = AggressorClassifier.classify(
                List.of(trade(T0 + 1, 6.0, 10)), List.of(quote(T0, 4.0, 6.0)), CFG, 20L, false);
        assertEquals(0.5, half.tickCoverage(), 1e-9);
    }

    @Test
    void partialFlagAndEmptyInputs() {
        AggressorProfile p = AggressorClassifier.classify(List.of(), List.of(), CFG, 100L, true);
        assertEquals(AggressorProfile.STATUS_PARTIAL, p.status());
        assertEquals(0, p.buyVolume());
        assertNull(p.firstTradeAt());
        assertNull(p.vwapBuy());
        assertEquals(0.0, p.tickCoverage(), 1e-9);

        AggressorProfile ok = AggressorClassifier.classify(List.of(), List.of(), CFG, null, false);
        assertEquals(AggressorProfile.STATUS_OK, ok.status());
    }

    @Test
    void skippedBudgetProfileCarriesOnlyTheStatus() {
        AggressorProfile p = AggressorProfile.skippedBudget();
        assertEquals(AggressorProfile.STATUS_SKIPPED_BUDGET, p.status());
        assertNull(p.buyVolume());
        assertNull(p.tickCoverage());
    }
}
