package com.trading.marketdata.ibkr;

import java.util.Collections;
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
        private Set<String> expirations = Collections.emptySet();
        private Set<Double> strikes     = Collections.emptySet();
        private String multiplier       = "100";

        public Builder expirations(Set<String> v) { this.expirations = new TreeSet<>(v); return this; }
        public Builder strikes(Set<Double> v)      { this.strikes = new TreeSet<>(v);     return this; }
        public Builder multiplier(String v)        { this.multiplier = v;                 return this; }

        public IbkrOptionsChainResult build() {
            return new IbkrOptionsChainResult(expirations, strikes, multiplier);
        }
    }
}
