package com.trading.marketdata.options;

import java.time.Instant;

public record OptionChainChange(
        String ticker,
        ChangeType type,
        String expiry,
        Double strike,
        String detail,
        Instant detectedAt
) {
    public enum ChangeType { EXPIRY_ADDED, EXPIRY_REMOVED, STRIKE_ADDED, STRIKE_REMOVED, MULTIPLIER_CHANGED }
}
