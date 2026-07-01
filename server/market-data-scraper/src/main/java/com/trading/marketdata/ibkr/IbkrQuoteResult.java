package com.trading.marketdata.ibkr;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Immutable result of a reqMktData snapshot call.
 */
public record IbkrQuoteResult(
        int reqId,
        Double bid,
        Double ask,
        Double last,
        Double open,
        Double high,
        Double low,
        Double close,
        Long volume
) {
    // Per-reqId builder cache — allows tickPrice callbacks to accumulate
    private static final Map<Integer, Builder> builders = new ConcurrentHashMap<>();

    public static Builder builderFor(int reqId) {
        return builders.computeIfAbsent(reqId, k -> new Builder());
    }

    public static class Builder {
        private Double bid, ask, last, open, high, low, close;
        private Long volume;

        public Builder bid(double v)    { this.bid = v;    return this; }
        public Builder ask(double v)    { this.ask = v;    return this; }
        public Builder last(double v)   { this.last = v;   return this; }
        public Builder open(double v)   { this.open = v;   return this; }
        public Builder high(double v)   { this.high = v;   return this; }
        public Builder low(double v)    { this.low = v;    return this; }
        public Builder close(double v)  { this.close = v;  return this; }
        public Builder volume(long v)   { this.volume = v; return this; }

        public IbkrQuoteResult build(int reqId) {
            builders.remove(reqId);
            return new IbkrQuoteResult(reqId, bid, ask, last, open, high, low, close, volume);
        }
    }
}
