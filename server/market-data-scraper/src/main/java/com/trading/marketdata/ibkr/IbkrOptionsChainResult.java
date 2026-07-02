package com.trading.marketdata.ibkr;

import java.util.Set;
import java.util.TreeSet;

/**
 * Result of reqSecDefOptParams — available expirations and strikes for a symbol.
 */
public record IbkrOptionsChainResult(
        Set<String> expirations,
        Set<Double> strikes,
        String multiplier
) {
    public static class Builder {
        private final Set<String> expirations = new TreeSet<>();
        private final Set<Double> strikes     = new TreeSet<>();
        private String multiplier             = "100";

        // Union, not overwrite — multiple exchanges can report the same tradingClass with
        // slightly different available strikes; we want the broadest usable view, not
        // whichever exchange's callback happened to arrive last.
        public Builder expirations(Set<String> v) { this.expirations.addAll(v); return this; }
        public Builder strikes(Set<Double> v)      { this.strikes.addAll(v);     return this; }
        public Builder multiplier(String v)        { this.multiplier = v;                 return this; }

        public IbkrOptionsChainResult build() {
            return new IbkrOptionsChainResult(expirations, strikes, multiplier);
        }
    }
}
