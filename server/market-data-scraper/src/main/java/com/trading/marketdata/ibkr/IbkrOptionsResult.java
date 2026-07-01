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

        public Builder putCallRatio(double v)        { this.putCallRatio = v;        return this; }
        public Builder impliedVolatility(double v)   { this.impliedVolatility = v;   return this; }
        public Builder historicalVolatility(double v){ this.historicalVolatility = v; return this; }

        public IbkrOptionsResult build() {
            return new IbkrOptionsResult(putCallRatio, impliedVolatility, historicalVolatility);
        }
    }

    public static Builder builder() { return new Builder(); }
}
