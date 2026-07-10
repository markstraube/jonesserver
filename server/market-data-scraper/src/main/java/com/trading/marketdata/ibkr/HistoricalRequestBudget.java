package com.trading.marketdata.ibkr;

/**
 * Pacing budget for one UA stage-2 scan cycle, counted in REQUEST-EQUIVALENTS: historical
 * tick requests share IBKR's historical-data budget (~60 requests per 10 minutes) and
 * BID_ASK requests count DOUBLE against it — so one TRADES page costs 1, one BID_ASK page
 * costs 2. The escalation layer creates one budget per scan cycle and skips remaining
 * candidates when it is exhausted (SKIPPED_BUDGET), which keeps the stage's worst case
 * bounded no matter how many contracts stage 1 flags.
 *
 * Deliberately NOT thread-safe: a budget belongs to exactly one scan cycle, and scan
 * cycles run sequentially on their own thread.
 */
public final class HistoricalRequestBudget {

    private final int total;
    private int remaining;

    public HistoricalRequestBudget(int requestEquivalents) {
        this.total = Math.max(requestEquivalents, 0);
        this.remaining = this.total;
    }

    /** Consumes {@code cost} equivalents when available. False = budget exhausted, do not send. */
    public boolean tryConsume(int cost) {
        if (remaining < cost) return false;
        remaining -= cost;
        return true;
    }

    public int used() {
        return total - remaining;
    }

    public int remaining() {
        return remaining;
    }
}
