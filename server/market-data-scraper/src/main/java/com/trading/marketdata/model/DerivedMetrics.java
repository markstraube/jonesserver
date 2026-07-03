package com.trading.marketdata.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Deterministic, mechanically computed features derived from the raw snapshot — arithmetic
 * belongs in Java, interpretation belongs to the consumer (typically an LLM). Deliberately
 * contains NO judgements ("bullish"/"bearish"): numbers only. A wrong pre-computed verdict
 * is worse than none, same reasoning that removed the news sentiment classifier.
 *
 * Delta fields compare against the most recent persisted snapshot of the same ticker and are
 * null when no history exists (persistence disabled, first run, or DB unreachable).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DerivedMetrics(
        // --- Intraday position (from quote) ---
        Double prevClose,          // price - change: the reference behind changePct
        Double pctFromOpen,        // where price sits vs. today's open, in %
        Double pctFromHigh,        // distance below session high, in % (<= 0)
        Double pctFromLow,         // distance above session low, in % (>= 0)
        Double rangePct,           // (high - low) / prevClose, in %: the day's realized span

        // --- Options window aggregates (from oiProfile) ---
        Long oiCallTotal,          // sum of call OI across the visible strike window
        Long oiPutTotal,           // sum of put OI across the visible strike window
        Double oiPutCallRatio,     // put/call OI within the window (stock: positioning)

        // --- Unusual activity aggregates (strike-referenced notional, USD) ---
        Long uaCallVolume,
        Long uaPutVolume,
        Double uaCallNotionalUsd,  // volume * strike * 100 summed over flagged calls
        Double uaPutNotionalUsd,

        // --- Deltas vs. previous persisted snapshot (same ticker) ---
        Double priceDeltaPct,      // price change since previous snapshot, in %
        Long volumeDelta,          // additional shares traded since previous snapshot
        Double oiPutCallRatioDelta,
        Long minutesSincePrevious,
        Instant previousSnapshotAt
) {}
