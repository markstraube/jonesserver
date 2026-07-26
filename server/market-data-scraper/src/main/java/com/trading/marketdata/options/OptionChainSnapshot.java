package com.trading.marketdata.options;

import java.time.Instant;
import java.util.Set;

public record OptionChainSnapshot(
        String ticker,
        int underlyingConId,
        Set<String> expirations,
        Set<Double> strikes,
        String multiplier,
        Instant discoveredAt
) {
    public OptionChainSnapshot {
        expirations = Set.copyOf(expirations);
        strikes = Set.copyOf(strikes);
    }
}
