package com.trading.marketdata.ibkr;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Process-wide pacing guard for IBKR historical requests.
 *
 * Enforces the documented global constraints that a per-scan budget cannot protect:
 *  - max 60 historical request equivalents per rolling 10 minutes
 *  - max 5 identical-key requests per rolling 2 seconds (the sixth is delayed)
 *  - no identical request key more often than every 15 seconds
 * BID_ASK callers pass cost=2, all other historical tick requests cost=1.
 */
@Component
public class IbkrRequestGovernor {
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Duration TWO_SECONDS = Duration.ofSeconds(2);
    private static final Duration DUPLICATE_GAP = Duration.ofSeconds(15);

    private record WeightedStamp(Instant at, int cost) { }

    private final Deque<WeightedStamp> globalHistory = new ArrayDeque<>();
    private final Map<String, Deque<Instant>> keyHistory = new HashMap<>();
    private final Map<String, Instant> lastIdentical = new HashMap<>();

    public synchronized void acquireHistorical(String scopeKey, String identicalKey, int cost) {
        if (cost <= 0) throw new IllegalArgumentException("cost must be > 0");
        String normalizedScope = scopeKey == null ? "unknown" : scopeKey;
        String normalizedIdentical = identicalKey == null ? normalizedScope : identicalKey;

        while (true) {
            Instant now = Instant.now();
            prune(now);

            int globalCost = globalHistory.stream().mapToInt(WeightedStamp::cost).sum();
            Deque<Instant> perKey = keyHistory.computeIfAbsent(normalizedScope, k -> new ArrayDeque<>());
            Instant last = lastIdentical.get(normalizedIdentical);

            long waitMs = 0;
            if (globalCost + cost > 60 && !globalHistory.isEmpty()) {
                waitMs = Math.max(waitMs, Duration.between(now,
                        globalHistory.peekFirst().at().plus(TEN_MINUTES)).toMillis());
            }
            if (perKey.size() >= 5) {
                waitMs = Math.max(waitMs, Duration.between(now,
                        perKey.peekFirst().plus(TWO_SECONDS)).toMillis());
            }
            if (last != null) {
                waitMs = Math.max(waitMs, Duration.between(now, last.plus(DUPLICATE_GAP)).toMillis());
            }

            if (waitMs <= 0) {
                Instant accepted = Instant.now();
                globalHistory.addLast(new WeightedStamp(accepted, cost));
                perKey.addLast(accepted);
                lastIdentical.put(normalizedIdentical, accepted);
                return;
            }

            try {
                wait(Math.min(waitMs, 1_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for IBKR pacing budget", e);
            }
        }
    }

    private void prune(Instant now) {
        while (!globalHistory.isEmpty()
                && !globalHistory.peekFirst().at().isAfter(now.minus(TEN_MINUTES))) {
            globalHistory.removeFirst();
        }
        keyHistory.entrySet().removeIf(entry -> {
            Deque<Instant> q = entry.getValue();
            while (!q.isEmpty() && !q.peekFirst().isAfter(now.minus(TWO_SECONDS))) q.removeFirst();
            return q.isEmpty();
        });
        lastIdentical.entrySet().removeIf(e -> !e.getValue().isAfter(now.minus(DUPLICATE_GAP)));
    }
}
