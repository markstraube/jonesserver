package com.trading.marketdata.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CollectorRunStatus(
        String status,
        Instant lastRun,
        Long durationMs,
        Map<String, Object> metrics,
        String message
) {
    public CollectorRunStatus {
        metrics = metrics == null || metrics.isEmpty() ? null : Map.copyOf(metrics);
    }
}
