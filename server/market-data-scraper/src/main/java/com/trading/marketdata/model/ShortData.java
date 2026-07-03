package com.trading.marketdata.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShortData(
        String ticker,
        Double shortFloat,
        Long shortShares,
        Double daysToCover,
        Double shortRatio,
        Double instOwn,
        Double insiderOwn,
        Long avgVolume,
        String source,
        String sourceError,
        boolean dataAvailable,
        Instant fetchedAt
) {
    public static ShortData empty(String ticker, String errorMsg) {
        return new ShortData(ticker, null, null, null, null, null, null, null,
                null, errorMsg, false, Instant.now());
    }
}
