package com.trading.marketdata.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Additive per-snapshot data-quality block, derived from the Book's timestamp pairs. Only
 * present for Book symbols (permanent IBKR streams); null for tickers served by scrapers.
 *
 * The core distinction it exposes: old {@code changedAgeSeconds} + fresh {@code ageSeconds}
 * = calm market (feed alive, values simply not moving — healthy); both old = dead feed or
 * lost connection. {@code stale} folds that judgement together with the market state
 * (silence outside REGULAR is stillness, not failure).
 *
 * marketDataType is IBKR's line quality: 1 = realtime, 2 = frozen, 3 = delayed,
 * 4 = delayed-frozen. A switch to 2–4 is reported here and deliberately does not count as
 * field liveness (see IbkrWrapper.marketDataType).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DataQuality(
        boolean connectionLost,
        Integer marketDataType,
        Section quote,
        Section optionsMetrics,
        Section auction,
        Section uaScan
) {
    /**
     * Quality of one field group. ageSeconds/changedAgeSeconds are the FRESHEST member
     * field's values (null = no member field has ever been seen). invalidated = every seen
     * member field is flagged from a connection loss and has not re-ticked since.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Section(
            Long ageSeconds,
            Long changedAgeSeconds,
            boolean stale,
            Boolean invalidated
    ) {}
}
