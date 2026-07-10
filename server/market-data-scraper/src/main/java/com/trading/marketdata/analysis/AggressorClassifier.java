package com.trading.marketdata.analysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure trade-by-trade aggressor classification (Lee-Ready quote rule adapted for options).
 * No IBKR types in here: the fetch layer converts HistoricalTickLast/BidAsk to the internal
 * {@link Trade}/{@link Quote} records at the boundary. Everything below is deterministic
 * functions of its inputs — exhaustively unit-tested, no clock, no I/O.
 *
 * Timestamp resolution is ONE SECOND (IBKR historical ticks deliver epoch seconds), so
 * several trades and several quote updates can share a timestamp. The quote join handles
 * that explicitly — see {@link #prevailingQuote}.
 */
public final class AggressorClassifier {

    private AggressorClassifier() {}

    /** One trade print. size in contracts; specialConditions/exchange as delivered (may be
     *  null/blank); unreported = TickAttribLast.unreported() (off-exchange/late print). */
    public record Trade(long epochSeconds, double price, long size,
                        String exchange, String specialConditions, boolean unreported) {}

    /** One NBBO update. */
    public record Quote(long epochSeconds, double bid, double ask) {}

    /**
     * Tunables that are genuinely configuration. The classification thresholds themselves
     * (0.35/0.65, the midpoint zone) are fixed by spec and deliberately NOT configurable —
     * a threshold nobody can quietly change is a threshold whose numbers stay comparable
     * across days.
     */
    public record Config(long sweepWindowMs, long blockMinContracts, List<String> spreadMarkers) {}

    // Spread position thresholds (fixed by spec): >= BUY_LEAN_MIN of the spread → buyer-
    // leaning, <= SELL_LEAN_MAX → seller-leaning, between → midpoint zone = UNKNOWN.
    // The midpoint zone is deliberately NOT tick-tested: midpoint prints in options are
    // disproportionately spread legs and price-improvement fills — a tick-test fallback
    // would add noise, not signal.
    static final double BUY_LEAN_MIN = 0.65;
    static final double SELL_LEAN_MAX = 0.35;

    enum Side {
        BUY, BUY_LEAN, SELL, SELL_LEAN, UNKNOWN;

        boolean isBuy()  { return this == BUY || this == BUY_LEAN; }
        boolean isSell() { return this == SELL || this == SELL_LEAN; }

        /** Directional grouping for sweeps: LEAN counts toward its direction. */
        Integer direction() {
            if (isBuy()) return 1;
            if (isSell()) return -1;
            return null; // UNKNOWN has no direction and breaks sweep runs
        }
    }

    /**
     * Classifies one session's trades against the quote stream and aggregates the profile.
     *
     * @param contractDayVolume the contract's day volume from the stage-1 scan (tick 8) —
     *                          denominator of tickCoverage; null/0 → coverage null
     * @param partial           the fetch layer's honesty flag (timeout / pagination stall /
     *                          budget cap mid-fetch) — propagated to status
     */
    public static AggressorProfile classify(List<Trade> trades, List<Quote> quotes, Config cfg,
                                            Long contractDayVolume, boolean partial) {
        List<Trade> byTime = new ArrayList<>(trades == null ? List.of() : trades);
        byTime.sort(Comparator.comparingLong(Trade::epochSeconds)); // stable: same-second arrival order kept
        List<Quote> quotesByTime = new ArrayList<>(quotes == null ? List.of() : quotes);
        quotesByTime.sort(Comparator.comparingLong(Quote::epochSeconds));

        long buyStrong = 0, buyLean = 0, sellStrong = 0, sellLean = 0, unknown = 0;
        long excludedSpread = 0, excludedUnreported = 0;
        double buyNotional = 0, sellNotional = 0;
        double buyPriceVolume = 0, sellPriceVolume = 0; // Σ price*size for VWAP
        long blockCount = 0, blockVolume = 0, largestBlock = 0;

        List<Side> sides = new ArrayList<>(byTime.size()); // parallel to byTime; null = excluded
        long firstTrade = Long.MAX_VALUE, lastTrade = Long.MIN_VALUE;

        for (Trade t : byTime) {
            firstTrade = Math.min(firstTrade, t.epochSeconds());
            lastTrade = Math.max(lastTrade, t.epochSeconds());

            // Exclusions BEFORE classification — spread legs first (a spread leg that is
            // also unreported counts once, as SPREAD_LEG).
            if (isSpreadLeg(t, cfg.spreadMarkers())) {
                excludedSpread += t.size();
                sides.add(null);
                continue;
            }
            if (t.unreported()) {
                excludedUnreported += t.size();
                sides.add(null);
                continue;
            }

            Side side = classifyTrade(t, prevailingQuote(quotesByTime, t.epochSeconds()));
            sides.add(side);

            switch (side) {
                case BUY -> buyStrong += t.size();
                case BUY_LEAN -> buyLean += t.size();
                case SELL -> sellStrong += t.size();
                case SELL_LEAN -> sellLean += t.size();
                case UNKNOWN -> unknown += t.size();
            }
            if (side.isBuy()) {
                buyNotional += t.price() * t.size() * 100;
                buyPriceVolume += t.price() * t.size();
            } else if (side.isSell()) {
                sellNotional += t.price() * t.size() * 100;
                sellPriceVolume += t.price() * t.size();
            }

            // Blocks: any single non-excluded print at/above the size floor, regardless of
            // classification bucket — negotiated prints cluster at mid, a midpoint block is
            // still a block.
            if (t.size() >= cfg.blockMinContracts()) {
                blockCount++;
                blockVolume += t.size();
                largestBlock = Math.max(largestBlock, t.size());
            }
        }

        Sweeps sweeps = detectSweeps(byTime, sides, cfg);

        long buy = buyStrong + buyLean;
        long sell = sellStrong + sellLean;
        long analyzed = buy + sell + unknown + excludedSpread + excludedUnreported;
        Double coverage = (contractDayVolume == null || contractDayVolume <= 0) ? null
                : (double) analyzed / contractDayVolume;

        return new AggressorProfile(
                partial ? AggressorProfile.STATUS_PARTIAL : AggressorProfile.STATUS_OK,
                buy, sell, unknown,
                buyStrong, buyLean, sellStrong, sellLean,
                excludedSpread, excludedUnreported,
                buy > 0 ? buyNotional : null,
                sell > 0 ? sellNotional : null,
                buy > 0 ? buyPriceVolume / buy : null,
                sell > 0 ? sellPriceVolume / sell : null,
                sweeps.count, sweeps.volume, sweeps.largest,
                (int) blockCount, blockVolume, largestBlock,
                coverage,
                byTime.isEmpty() ? null : Instant.ofEpochSecond(firstTrade),
                byTime.isEmpty() ? null : Instant.ofEpochSecond(lastTrade),
                null, null); // OI join happens in the escalation layer (needs the day memory)
    }

    /**
     * The prevailing quote for a trade at time T, under one-second resolution: the latest
     * quote in a STRICTLY earlier second when one exists — within the trade's own second the
     * trade-vs-quote order is unknowable, so a same-second quote must not beat an earlier
     * one. Only when ALL candidates share the trade's second is the last of them used
     * (arrival order within the callback list is chronological). No quote at or before T →
     * null → UNKNOWN.
     */
    static Quote prevailingQuote(List<Quote> quotesByTime, long tradeEpochSeconds) {
        Quote lastEarlier = null, lastAtOrBefore = null;
        for (Quote q : quotesByTime) { // sorted ascending; linear is fine, callers bound list size
            if (q.epochSeconds() > tradeEpochSeconds) break;
            lastAtOrBefore = q;
            if (q.epochSeconds() < tradeEpochSeconds) lastEarlier = q;
        }
        return lastEarlier != null ? lastEarlier : lastAtOrBefore;
    }

    /** Quote-rule classification of one trade against its prevailing quote. */
    static Side classifyTrade(Trade t, Quote q) {
        if (q == null) return Side.UNKNOWN;                    // trade before first quote
        double b = q.bid(), a = q.ask();
        if (!(a > b && b > 0)) return Side.UNKNOWN;            // crossed/locked/one-sided quote
        if (t.price() >= a) return Side.BUY;                   // lifted the offer (or through it)
        if (t.price() <= b) return Side.SELL;                  // hit the bid (or through it)
        double pos = (t.price() - b) / (a - b);                // in (0,1) here
        if (pos >= BUY_LEAN_MIN) return Side.BUY_LEAN;
        if (pos <= SELL_LEAN_MAX) return Side.SELL_LEAN;
        return Side.UNKNOWN;                                   // midpoint zone, deliberately untested
    }

    /**
     * The OI-delta join label — the single most valuable output: it turns "unusual volume"
     * into a positioning thesis. Dominance = one side carries >= 60% of the classified
     * directional volume (buy+sell, UNKNOWN excluded from the base).
     *
     *   buy-dominant  + OI up   → OPENING_BUYS   (new longs being built, urgently)
     *   buy-dominant  + OI down → SHORT_COVER    (writers buying their exposure back)
     *   sell-dominant + OI up   → OPENING_WRITES (premium sellers volunteering carry)
     *   sell-dominant + OI down → CLOSING_SALES  (longs monetizing)
     *   no dominance, or dominance with a flat OI (opens ≈ closes) → MIXED
     *   oiDelta unknown, or no classified directional volume at all → UNKNOWN
     *
     * Epoch caveat (also on AggressorProfile): OI is published pre-open as of the PREVIOUS
     * close, so the delta describes the prior session's net positioning while the dominance
     * describes today's flow — a thesis, not a proof.
     */
    public static String positionInference(long buyVolume, long sellVolume, Long oiDelta) {
        if (oiDelta == null) return "UNKNOWN";
        long directional = buyVolume + sellVolume;
        if (directional <= 0) return "UNKNOWN";
        boolean buyDominant = buyVolume >= 0.6 * directional;
        boolean sellDominant = sellVolume >= 0.6 * directional;
        if (!buyDominant && !sellDominant) return "MIXED";
        if (oiDelta == 0) return "MIXED";
        if (buyDominant) return oiDelta > 0 ? "OPENING_BUYS" : "SHORT_COVER";
        return oiDelta > 0 ? "OPENING_WRITES" : "CLOSING_SALES";
    }

    static boolean isSpreadLeg(Trade t, List<String> markers) {
        String cond = t.specialConditions();
        if (cond == null || cond.isBlank() || markers == null) return false;
        String lower = cond.toLowerCase(Locale.ROOT);
        for (String m : markers) {
            if (m != null && !m.isBlank() && lower.contains(m.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private record Sweeps(int count, long volume, long largest) {}

    /**
     * Sweep detection on the chronologically ordered, classified trade list: a run of >= 3
     * prints within the sweep window (anchored at the run's first print; at one-second
     * resolution the default 500ms effectively means same-second), all sharing one
     * classified direction (LEAN counts toward its direction), hitting >= 2 distinct
     * exchanges. UNKNOWN and excluded prints break the run — a run interleaved with
     * unclassifiable prints is not "someone clearing the book across venues" with the
     * confidence this counter claims.
     */
    private static Sweeps detectSweeps(List<Trade> byTime, List<Side> sides, Config cfg) {
        int count = 0;
        long volume = 0, largest = 0;

        int runStart = -1;
        Integer runDirection = null;

        for (int i = 0; i <= byTime.size(); i++) {
            Integer dir = null;
            boolean inWindow = false;
            if (i < byTime.size()) {
                Side side = sides.get(i);
                dir = side == null ? null : side.direction();
                inWindow = runStart >= 0
                        && (byTime.get(i).epochSeconds() - byTime.get(runStart).epochSeconds()) * 1000L
                           <= cfg.sweepWindowMs();
            }

            boolean continuesRun = runStart >= 0 && dir != null && dir.equals(runDirection) && inWindow;
            if (continuesRun) continue;

            // Run ends at i (exclusive) — evaluate it.
            if (runStart >= 0) {
                int len = i - runStart;
                if (len >= 3) {
                    Set<String> exchanges = new HashSet<>();
                    long runVolume = 0;
                    for (int j = runStart; j < i; j++) {
                        Trade t = byTime.get(j);
                        exchanges.add(t.exchange() == null ? "" : t.exchange());
                        runVolume += t.size();
                    }
                    if (exchanges.size() >= 2) {
                        count++;
                        volume += runVolume;
                        largest = Math.max(largest, runVolume);
                    }
                }
            }
            // Start a new run on a directional print, otherwise idle.
            runStart = dir != null ? i : -1;
            runDirection = dir;
        }
        return new Sweeps(count, volume, largest);
    }
}
