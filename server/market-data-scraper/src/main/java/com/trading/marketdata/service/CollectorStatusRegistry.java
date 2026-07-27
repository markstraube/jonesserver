package com.trading.marketdata.service;

import com.trading.marketdata.model.CollectorRunStatus;
import com.trading.marketdata.model.CollectorStatus;
import com.trading.marketdata.model.SystemHealth;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-local operational state for the independent collectors. This is deliberately not
 * persisted with market history: it describes the running process, not a market fact.
 */
@Service
public class CollectorStatusRegistry {

    private final Map<String, Map<String, CollectorRunStatus>> byTicker = new ConcurrentHashMap<>();

    public Run start(String ticker, String collector) {
        return new Run(normalize(ticker), collector, Instant.now());
    }

    public CollectorStatus snapshot(String ticker) {
        Map<String, CollectorRunStatus> current = byTicker.getOrDefault(normalize(ticker), Map.of());
        Map<String, CollectorRunStatus> ordered = new LinkedHashMap<>();
        current.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> ordered.put(e.getKey(), e.getValue()));

        int warnings = 0;
        int errors = 0;
        for (CollectorRunStatus status : ordered.values()) {
            if ("ERROR".equals(status.status())) errors++;
            else if ("PARTIAL".equals(status.status()) || "WARNING".equals(status.status())) warnings++;
        }
        String overall = errors > 0 ? "ERROR" : warnings > 0 ? "WARNING" : "OK";
        return new CollectorStatus(new SystemHealth(overall, warnings, errors), ordered);
    }

    private void put(String ticker, String collector, String status, Instant startedAt,
                     Map<String, Object> metrics, String message) {
        Instant now = Instant.now();
        long durationMs = Math.max(0, Duration.between(startedAt, now).toMillis());
        byTicker.computeIfAbsent(ticker, ignored -> new ConcurrentHashMap<>())
                .put(collector, new CollectorRunStatus(status, now, durationMs, metrics, message));
    }

    private static String normalize(String ticker) {
        return ticker == null ? "_GLOBAL" : ticker.toUpperCase();
    }

    public final class Run {
        private final String ticker;
        private final String collector;
        private final Instant startedAt;
        private boolean finished;

        private Run(String ticker, String collector, Instant startedAt) {
            this.ticker = ticker;
            this.collector = collector;
            this.startedAt = startedAt;
        }

        public void ok(Map<String, Object> metrics) {
            finish("OK", metrics, null);
        }

        public void partial(Map<String, Object> metrics, String message) {
            finish("PARTIAL", metrics, message);
        }

        public void error(Throwable error, Map<String, Object> metrics) {
            String message = error == null ? "unknown error" : error.getMessage();
            finish("ERROR", metrics, message);
        }

        private synchronized void finish(String status, Map<String, Object> metrics, String message) {
            if (finished) return;
            finished = true;
            put(ticker, collector, status, startedAt, metrics, message);
        }
    }
}
