package com.trading.marketdata.service;

import com.trading.marketdata.model.CollectorRunStatus;
import com.trading.marketdata.model.CollectorStatus;
import com.trading.marketdata.model.SystemHealth;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Process-local operational state for collectors and shared infrastructure. */
@Service
public class CollectorStatusRegistry {

    private static final String GLOBAL = "_GLOBAL";
    private final Map<String, Map<String, CollectorRunStatus>> byTicker = new ConcurrentHashMap<>();

    public Run start(String ticker, String collector) {
        return start(ticker, collector, List.of());
    }

    public Run start(String ticker, String collector, List<String> dependencies) {
        return new Run(normalize(ticker), collector, dependencies, Instant.now());
    }

    /** Reports long-lived infrastructure state such as the IBKR connection. */
    public void reportGlobal(String collector, String status, String lifecycle,
                             Map<String, Object> metrics, String message) {
        Instant now = Instant.now();
        put(GLOBAL, collector, new CollectorRunStatus(
                collector, status, lifecycle, null, now, now, null, null,
                withStandardMetrics(metrics),
                "WARNING".equals(status) || "PARTIAL".equals(status) ? List.of(messageOrDefault(message, status)) : null,
                "ERROR".equals(status) ? List.of(messageOrDefault(message, status)) : null,
                message));
    }

    public CollectorStatus snapshot(String ticker) {
        Map<String, CollectorRunStatus> ordered = new LinkedHashMap<>();
        merge(ordered, byTicker.getOrDefault(GLOBAL, Map.of()));
        merge(ordered, byTicker.getOrDefault(normalize(ticker), Map.of()));

        int warnings = 0;
        int errors = 0;
        for (CollectorRunStatus status : ordered.values()) {
            if ("ERROR".equals(status.status())) errors++;
            else if ("PARTIAL".equals(status.status()) || "WARNING".equals(status.status())) warnings++;
        }
        String overall = errors > 0 ? "ERROR" : warnings > 0 ? "WARNING" : "OK";
        return new CollectorStatus(new SystemHealth(overall, warnings, errors), ordered);
    }

    private static void merge(Map<String, CollectorRunStatus> target, Map<String, CollectorRunStatus> source) {
        source.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> target.put(e.getKey(), e.getValue()));
    }

    private void put(String ticker, String collector, CollectorRunStatus status) {
        byTicker.computeIfAbsent(ticker, ignored -> new ConcurrentHashMap<>()).put(collector, status);
    }

    private void finish(String ticker, String collector, String status, String lifecycle,
                        Instant startedAt, List<String> dependencies,
                        Map<String, Object> metrics, String message) {
        Instant now = Instant.now();
        Long durationMs = startedAt == null ? null : Math.max(0, Duration.between(startedAt, now).toMillis());
        List<String> warnings = ("PARTIAL".equals(status) || "WARNING".equals(status))
                ? List.of(messageOrDefault(message, status)) : null;
        List<String> errors = "ERROR".equals(status) ? List.of(messageOrDefault(message, status)) : null;
        put(ticker, collector, new CollectorRunStatus(
                collector, status, lifecycle, startedAt, now, now, durationMs,
                dependencies, withStandardMetrics(metrics), warnings, errors, message));
    }

    private static Map<String, Object> withStandardMetrics(Map<String, Object> metrics) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("itemsRequested", 0);
        result.put("itemsProcessed", 0);
        result.put("itemsSucceeded", 0);
        result.put("itemsFailed", 0);
        if (metrics != null) result.putAll(metrics);
        return result;
    }

    private static String messageOrDefault(String message, String status) {
        return message == null || message.isBlank() ? status.toLowerCase() : message;
    }

    private static String normalize(String ticker) {
        return ticker == null ? GLOBAL : ticker.toUpperCase();
    }

    public final class Run {
        private final String ticker;
        private final String collector;
        private final List<String> dependencies;
        private final Instant startedAt;
        private boolean finished;

        private Run(String ticker, String collector, List<String> dependencies, Instant startedAt) {
            this.ticker = ticker;
            this.collector = collector;
            this.dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
            this.startedAt = startedAt;
        }

        public void ok(Map<String, Object> metrics) { finish("OK", "COMPLETED", metrics, null); }
        public void partial(Map<String, Object> metrics, String message) { finish("PARTIAL", "COMPLETED", metrics, message); }
        public void skipped(Map<String, Object> metrics, String message) { finish("WARNING", "SKIPPED", metrics, message); }
        public void error(Throwable error, Map<String, Object> metrics) {
            finish("ERROR", "FAILED", metrics, error == null ? "unknown error" : error.getMessage());
        }

        private synchronized void finish(String status, String lifecycle, Map<String, Object> metrics, String message) {
            if (finished) return;
            finished = true;
            CollectorStatusRegistry.this.finish(ticker, collector, status, lifecycle, startedAt,
                    dependencies, metrics, message);
        }
    }
}
