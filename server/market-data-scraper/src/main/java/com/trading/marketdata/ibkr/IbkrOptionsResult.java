package com.trading.marketdata.ibkr;

/**
 * Options metrics fetched from IBKR via Generic Tick Types:
 *   - 106 = Put/Call Ratio (ratio of put volume to call volume)
 *   - 104 = Implied Volatility (30-day IV)
 *   - 105 = Historical Volatility
 *
 * Note: IBKR does not provide IV Rank or Max Pain directly.
 * IV Rank is calculated from IV + Historical IV range.
 * Max Pain requires OI per strike (separate request chain).
 */
public record IbkrOptionsResult(
        Double putCallRatio,
        Double impliedVolatility,   // 30-day IV as decimal (e.g. 0.45 = 45%)
        Double historicalVolatility // 30-day HV as decimal
) {
    public static class Builder {
        private Double putCallRatio;
        private Double impliedVolatility;
        private Double historicalVolatility;
        private Long callVolume;
        private Long putVolume;

        public Builder putCallRatio(double v)        { this.putCallRatio = v;        return this; }
        public Builder impliedVolatility(double v)   { this.impliedVolatility = v;   return this; }
        public Builder historicalVolatility(double v){ this.historicalVolatility = v; return this; }
        public Builder callVolume(long v)            { this.callVolume = v;          return this; }
        public Builder putVolume(long v)             { this.putVolume = v;           return this; }

        /** True once both option-volume ticks (29 call / 30 put) have arrived — the point at
         *  which the put/call ratio becomes computable and the request can complete. */
        public boolean hasBothVolumes() {
            return callVolume != null && putVolume != null;
        }

        public IbkrOptionsResult build() {
            // IBKR delivers no put/call-ratio tick — it must be computed from the option-volume
            // ticks (generic tick 100 → tick types 29/30). A directly set ratio wins if present.
            Double pcr = putCallRatio;
            if (pcr == null && hasBothVolumes() && callVolume > 0) {
                pcr = (double) putVolume / callVolume;
            }
            return new IbkrOptionsResult(pcr, impliedVolatility, historicalVolatility);
        }
    }

    public static Builder builder() { return new Builder(); }
}
