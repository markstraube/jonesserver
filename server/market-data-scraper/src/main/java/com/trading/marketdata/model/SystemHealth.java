package com.trading.marketdata.model;

public record SystemHealth(
        String status,
        int warnings,
        int errors
) {}
