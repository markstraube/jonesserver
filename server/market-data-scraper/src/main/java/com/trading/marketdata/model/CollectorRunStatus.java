package com.trading.marketdata.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CollectorRunStatus(
        String id,
        String status,
        String lifecycle,
        Instant startedAt,
        Instant finishedAt,
        Instant lastRun,
        Long durationMs,
        List<String> dependencies,
        Map<String, Object> metrics,
        List<String> warnings,
        List<String> errors,
        String message
) {
    public CollectorRunStatus {
        dependencies = dependencies == null || dependencies.isEmpty() ? null : List.copyOf(dependencies);
        metrics = metrics == null || metrics.isEmpty() ? null : Map.copyOf(metrics);
        warnings = warnings == null || warnings.isEmpty() ? null : List.copyOf(warnings);
        errors = errors == null || errors.isEmpty() ? null : List.copyOf(errors);
    }
}
