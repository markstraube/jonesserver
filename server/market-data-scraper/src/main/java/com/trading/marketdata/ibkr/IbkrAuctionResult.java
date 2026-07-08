package com.trading.marketdata.ibkr;

/**
 * Auction (NOII) ticks collected for one Generic Tick 225 subscription.
 *
 * Request/delivery mapping (same classic TWS API trap as 100/104/106 — the requested
 * generic tick number and the delivered tick type ids differ):
 *
 *   requested 225 (RT Auction Values) → arrives as
 *     tickPrice type 35 = AUCTION_PRICE
 *     tickSize  type 34 = AUCTION_VOLUME
 *     tickSize  type 36 = AUCTION_IMBALANCE
 *     tickSize  type 61 = REGULATORY_IMBALANCE
 *
 * Unlike the options-metrics request there is NO tick combination that signals
 * completeness: outside the Nasdaq NOII dissemination windows none of these ticks is
 * ever sent, and inside the windows they arrive independently and update continuously.
 * The caller therefore harvests this builder after a fixed collection window instead
 * of waiting for a completion condition. All fields null = feed silent (normal).
 */
public record IbkrAuctionResult(
        Double auctionPrice,
        Long auctionVolume,
        Long imbalance,
        Long regulatoryImbalance
) {
    public boolean isEmpty() {
        return auctionPrice == null && auctionVolume == null
                && imbalance == null && regulatoryImbalance == null;
    }

    public static class Builder {
        private volatile Double auctionPrice;
        private volatile Long auctionVolume;
        private volatile Long imbalance;
        private volatile Long regulatoryImbalance;

        public Builder auctionPrice(double v)        { this.auctionPrice = v;        return this; }
        public Builder auctionVolume(long v)         { this.auctionVolume = v;       return this; }
        public Builder imbalance(long v)             { this.imbalance = v;           return this; }
        public Builder regulatoryImbalance(long v)   { this.regulatoryImbalance = v; return this; }

        public IbkrAuctionResult build() {
            return new IbkrAuctionResult(auctionPrice, auctionVolume, imbalance, regulatoryImbalance);
        }
    }

    public static Builder builder() { return new Builder(); }
}
