package com.trading.marketdata.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NewsItem(
        String headline,
        String source,
        String url,
        Instant publishedAt
) {}
