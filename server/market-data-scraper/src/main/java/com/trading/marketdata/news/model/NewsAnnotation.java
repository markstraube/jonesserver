package com.trading.marketdata.news.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NewsAnnotation(
        String direction,
        double confidence,
        String eventType,
        String materiality,
        String timeHorizon,
        List<String> topics,
        String rationale,
        String model,
        String promptVersion,
        Instant annotatedAt
) {}
