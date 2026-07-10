package com.trading.marketdata.analysis;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Per-contract aggressor distribution of one session's trades, reconstructed from IBKR
 * historical ticks (UA scan stage 2). Attached additively to a flagged UnusualActivity
 * entry — it answers the question stage 1 cannot: WHO was aggressing behind the unusual
 * volume, across the whole day rather than the single last print.
 *
 * Volume buckets (Lee-Ready quote rule, see AggressorClassifier):
 *   buyVolume  = buyStrongVolume (printed at/through the ask) + buyLeanVolume (upper spread)
 *   sellVolume = sellStrongVolume (at/through the bid) + sellLeanVolume (lower spread)
 *   unknownVolume — midpoint zone, no usable quote, or crossed/locked quote.
 *   excluded*Volume — removed BEFORE classification (spread legs execute at negotiated
 *   package prices, unreported prints never hit the NBBO); reported separately, never
 *   silently dropped. Invariant: buy + sell + unknown + excluded == analyzed volume.
 *
 * tickCoverage: analyzed trade volume (including excluded) ÷ the contract's day volume from
 * the stage-1 scan — the honesty metric. Pagination caps and timeouts mean the window may
 * not contain every trade; a coverage of 0.6 says "this distribution describes 60% of the
 * day", and downstream analysis must weigh it accordingly.
 *
 * status: OK (complete fetch), PARTIAL (timeout/pagination stall — profile describes what
 * arrived), SKIPPED_BUDGET (stage-2 pacing budget exhausted before this candidate; all
 * other fields null — the skip is visible in the JSON instead of the profile silently
 * missing).
 *
 * oiDelta / positionInference: today's OI minus the most recent previous session's OI
 * (per-contract day memory), joined with the buy/sell dominance:
 *   buy-dominant + OI up → OPENING_BUYS,  buy-dominant + OI down → SHORT_COVER,
 *   sell-dominant + OI up → OPENING_WRITES, sell-dominant + OI down → CLOSING_SALES,
 *   otherwise MIXED (no 60% dominance) / UNKNOWN (no previous OI known).
 * Epoch caveat (documented, not hidden): exchanges publish OI once pre-open as of the
 * PREVIOUS close, so oiDelta measures the prior session's net opening/closing while the
 * dominance measures TODAY's flow. The label is a positioning thesis, not a proof.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AggressorProfile(
        String status,
        Long buyVolume,
        Long sellVolume,
        Long unknownVolume,
        Long buyStrongVolume,
        Long buyLeanVolume,
        Long sellStrongVolume,
        Long sellLeanVolume,
        Long excludedSpreadVolume,
        Long excludedUnreportedVolume,
        Double buyNotionalUsd,
        Double sellNotionalUsd,
        Double vwapBuy,
        Double vwapSell,
        Integer sweepCount,
        Long sweepVolume,
        Long largestSweepVolume,
        Integer blockCount,
        Long blockVolume,
        Long largestBlockVolume,
        Double tickCoverage,
        Instant firstTradeAt,
        Instant lastTradeAt,
        Long oiDelta,
        String positionInference
) {
    public static final String STATUS_OK = "OK";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_SKIPPED_BUDGET = "SKIPPED_BUDGET";

    /** Budget-skip marker: the candidate was flagged but stage 2 ran out of pacing budget. */
    public static AggressorProfile skippedBudget() {
        return new AggressorProfile(STATUS_SKIPPED_BUDGET,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    /** Same profile with the OI-delta join attached (records are immutable). */
    public AggressorProfile withOiJoin(Long oiDelta, String positionInference) {
        return new AggressorProfile(status,
                buyVolume, sellVolume, unknownVolume,
                buyStrongVolume, buyLeanVolume, sellStrongVolume, sellLeanVolume,
                excludedSpreadVolume, excludedUnreportedVolume,
                buyNotionalUsd, sellNotionalUsd, vwapBuy, vwapSell,
                sweepCount, sweepVolume, largestSweepVolume,
                blockCount, blockVolume, largestBlockVolume,
                tickCoverage, firstTradeAt, lastTradeAt,
                oiDelta, positionInference);
    }
}
