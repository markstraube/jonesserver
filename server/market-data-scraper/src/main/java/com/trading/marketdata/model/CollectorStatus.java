package com.trading.marketdata.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CollectorStatus(
        SystemHealth systemHealth,
        Map<String, CollectorRunStatus> collectors
) {
    public CollectorStatus {
        collectors = collectors == null ? Map.of() : Map.copyOf(collectors);
    }
}
