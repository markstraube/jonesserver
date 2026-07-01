package com.trading.marketdata.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuoteData(
        String ticker,
        Double price,
        Double change,
        Double changePct,
        Double open,
        Double high,
        Double low,
        Long volume,
        Long avgVolume,
        Double volumeRatio,
        String marketCap,
        Double pe,
        String week52High,
        String week52Low,
        String marketState,
        Double preMarketPrice,
        Double preMarketChangePct,
        Double postMarketPrice,
        Double postMarketChangePct,
        String source,
        String sourceError,
        boolean dataAvailable,
        Instant fetchedAt
) {
    public static QuoteData empty(String ticker, String errorMsg) {
        return new QuoteData(ticker, null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, errorMsg, false, Instant.now());
    }
}
