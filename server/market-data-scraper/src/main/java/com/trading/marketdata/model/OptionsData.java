package com.trading.marketdata.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OptionsData(
        String ticker,
        Double putCallRatio,
        Double ivRank,
        Double ivPercentile,
        List<UnusualActivity> unusualActivity,
        Double maxPain,
        String source,
        String sourceError,
        boolean dataAvailable,
        Instant fetchedAt
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UnusualActivity(
            String expiry,
            Double strike,
            String type,
            Long volume,
            Long openInterest,
            Double volumeOiRatio,
            Double iv,
            String sentiment
    ) {}

    public static OptionsData empty(String ticker, String errorMsg) {
        return new OptionsData(ticker, null, null, null, null, null,
                null, errorMsg, false, Instant.now());
    }
}
