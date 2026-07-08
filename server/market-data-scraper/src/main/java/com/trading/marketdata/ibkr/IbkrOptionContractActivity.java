package com.trading.marketdata.ibkr;

/**
 * Result of a per-contract reqMktData call on a single option contract (one specific
 * expiry + strike + right).
 *
 * volume: TickType.VOLUME (field 8) / DELAYED_VOLUME (field 74) — this contract's own
 *         traded volume for the session. This is a *default* tick, delivered automatically,
 *         no generic tick list required.
 * openInterest: TickType.OPTION_CALL_OPEN_INTEREST (field 27) for calls, or
 *         OPTION_PUT_OPEN_INTEREST (field 28) for puts — only delivered when "101" is
 *         included in the request's genericTickList. Open interest is an end-of-day figure
 *         published once daily, not a live-updating number, so callers are expected to
 *         cache it rather than request it on every poll (see OptionOpenInterestCache).
 * bid/ask/last: TickType.BID/ASK/LAST (fields 1/2/4, delayed 66/67/68) — default ticks on
 *         the very same subscription, zero additional request cost. They usually arrive
 *         before the volume/OI tick that completes the future; when they don't, the fields
 *         are simply null (never fabricated). NOTE: "last" for a contract that hasn't
 *         traded today is the previous session's last — good enough for order-of-magnitude
 *         premium notional, not for pricing.
 * bidAtLast/askAtLast: the bid/ask that were already in this builder AT THE MOMENT the
 *         last tick arrived — the quote pair contemporaneous (within the subscription
 *         window) with the last print. Aggressor classification (was the last trade near
 *         the bid = seller-initiated, or near the ask = buyer-initiated) must compare
 *         against THIS pair: the end-of-window bid/ask may have moved after the last tick
 *         landed, and comparing prices from two different moments fabricates a location.
 *         Null when the last tick arrived before any quote tick in this window — consumers
 *         may then fall back to the end-of-window pair (same ~2s market moment, slightly
 *         weaker guarantee) but must not mix one frozen side with one end-of-window side.
 * lastTimestampEpoch: TickType.LAST_TIMESTAMP (field 45, delayed 88) — unix epoch seconds
 *         of the last trade, a default tick on the same subscription. This is what turns
 *         "the last print might be stale" from a guess into a measurement: a last from
 *         hours ago sitting next to a live quote must not be classified as if it just
 *         printed against that quote.
 */
public record IbkrOptionContractActivity(
        Long volume,
        Long openInterest,
        Double bid,
        Double ask,
        Double last,
        Double bidAtLast,
        Double askAtLast,
        Long lastTimestampEpoch
) {
    public static class Builder {
        private Long volume;
        private Long openInterest;
        private Double bid;
        private Double ask;
        private Double last;
        private Double bidAtLast;
        private Double askAtLast;
        private Long lastTimestampEpoch;

        public Builder volume(long v)       { this.volume = v;       return this; }
        public Builder openInterest(long v) { this.openInterest = v; return this; }
        public Builder bid(double v)        { this.bid = v;          return this; }
        public Builder ask(double v)        { this.ask = v;          return this; }

        /**
         * Setting the last price freezes the currently known bid/ask alongside it. IBKR
         * delivers bid/ask ticks before the last on a live line, so the frozen pair is
         * normally populated; when the last arrives first, the frozen pair stays null
         * rather than being backfilled later — a later quote is exactly the "quote moved
         * away from the print" case the freeze exists to exclude. A newer last re-freezes.
         */
        public Builder last(double v) {
            this.last = v;
            this.bidAtLast = this.bid;
            this.askAtLast = this.ask;
            return this;
        }

        public Builder lastTimestamp(long epochSeconds) { this.lastTimestampEpoch = epochSeconds; return this; }

        public IbkrOptionContractActivity build() {
            return new IbkrOptionContractActivity(volume, openInterest, bid, ask, last,
                    bidAtLast, askAtLast, lastTimestampEpoch);
        }
    }

    public static Builder builder() { return new Builder(); }
}
