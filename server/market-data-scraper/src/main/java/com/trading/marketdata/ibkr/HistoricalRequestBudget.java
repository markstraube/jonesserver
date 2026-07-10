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
    /** Non-null for a slice: consumption must clear BOTH the slice cap and the parent. */
    private final HistoricalRequestBudget parent;

    public HistoricalRequestBudget(int requestEquivalents) {
        this(requestEquivalents, null);
    }

    private HistoricalRequestBudget(int requestEquivalents, HistoricalRequestBudget parent) {
        this.total = Math.max(requestEquivalents, 0);
        this.remaining = this.total;
        this.parent = parent;
    }

    /**
     * A capped view of this budget for ONE candidate — the fair-share mechanism of the
     * escalation stage. The slice can consume at most {@code maxEquivalents}, and every
     * consumption also draws from this (parent) budget, so unused share flows back to the
     * pool automatically: slicing reserves nothing, it only CAPS. Without this, the first
     * candidate of a cycle (typically the highest-volume contract, i.e. the most pages)
     * starves all others into SKIPPED_BUDGET — observed live on the first stage-2 day:
     * one 12k-contract 0DTE ate all 20 equivalents, three candidates got nothing.
     */
    public HistoricalRequestBudget slice(int maxEquivalents) {
        return new HistoricalRequestBudget(Math.min(Math.max(maxEquivalents, 0), remaining), this);
    }

    /** Consumes {@code cost} equivalents when available. False = budget exhausted, do not send. */
    public boolean tryConsume(int cost) {
        if (remaining < cost) return false;
        if (parent != null && !parent.tryConsume(cost)) return false;
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
