package com.trading.marketdata.options;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Selects scan candidates without ever inventing or deleting strikes. */
@Service
public class OptionCandidateSelector {
    public List<Double> nearestStrikes(Set<Double> listedStrikes, double spot, int limit) {
        return listedStrikes.stream()
                .sorted(Comparator
                        .comparingDouble((Double strike) -> Math.abs(strike - spot))
                        .thenComparingDouble(this::roundnessPenalty)
                        .thenComparingDouble(Double::doubleValue))
                .limit(Math.max(limit, 0))
                .toList();
    }

    private double roundnessPenalty(double strike) {
        // Tie-breaker only: round strikes win equal-distance ties, 2.5 strikes remain eligible.
        if (Math.abs(strike % 10.0) < 1e-9) return 0.0;
        if (Math.abs(strike % 5.0) < 1e-9) return 0.1;
        if (Math.abs((strike * 2.0) % 5.0) < 1e-9) return 0.2;
        return 0.3;
    }
}
