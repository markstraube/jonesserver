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
 */
public record IbkrOptionContractActivity(
        Long volume,
        Long openInterest,
        Double bid,
        Double ask,
        Double last
) {
    public static class Builder {
        private Long volume;
        private Long openInterest;
        private Double bid;
        private Double ask;
        private Double last;

        public Builder volume(long v)       { this.volume = v;       return this; }
        public Builder openInterest(long v) { this.openInterest = v; return this; }
        public Builder bid(double v)        { this.bid = v;          return this; }
        public Builder ask(double v)        { this.ask = v;          return this; }
        public Builder last(double v)       { this.last = v;         return this; }

        public IbkrOptionContractActivity build() {
            return new IbkrOptionContractActivity(volume, openInterest, bid, ask, last);
        }
    }

    public static Builder builder() { return new Builder(); }
}
