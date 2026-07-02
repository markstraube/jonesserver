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
 */
public record IbkrOptionContractActivity(
        Long volume,
        Long openInterest
) {
    public static class Builder {
        private Long volume;
        private Long openInterest;

        public Builder volume(long v)       { this.volume = v;       return this; }
        public Builder openInterest(long v) { this.openInterest = v; return this; }

        public IbkrOptionContractActivity build() {
            return new IbkrOptionContractActivity(volume, openInterest);
        }
    }

    public static Builder builder() { return new Builder(); }
}
