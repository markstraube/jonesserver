package com.trading.marketdata.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Nasdaq opening/closing cross imbalance data (NOII), fetched via IBKR Generic Tick 225.
 *
 * The feed is silent for almost the entire session: Nasdaq disseminates opening-cross NOII
 * only from ~09:28 ET until the 09:30 cross, and closing-cross NOII from ~15:50 ET until
 * the 16:00 cross. Outside those windows every field is legitimately null — that is the
 * NORMAL state, never an error. dataAvailable=true means at least one auction tick arrived.
 *
 * Field semantics (IBKR tick type in parentheses):
 *   auctionPrice        (35) — indicative cross price at which the auction would execute now
 *   auctionVolume       (34) — shares matched (paired) at that indicative price
 *   imbalance           (36) — unmatched shares on the heavier side.
 *   regulatoryImbalance (61) — regulatory-imbalance figure, closing cross only on Nasdaq.
 *
 * SIGN SEMANTICS OF imbalance ARE UNVERIFIED: the native Nasdaq NOII protocol carries
 * imbalance quantity and imbalance SIDE as two separate fields. Whether IBKR folds the side
 * into the sign of tick 36 (negative = sell imbalance) or delivers an unsigned quantity has
 * to be confirmed at the wire log during a real auction window before the sign is trusted.
 * The raw value is stored as delivered; IbkrWrapper logs every auction tick at INFO for
 * exactly this verification.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuctionData(
        String ticker,
        Double auctionPrice,
        Long auctionVolume,
        Long imbalance,
        Long regulatoryImbalance,
        String source,
        boolean dataAvailable,
        Instant fetchedAt
) {}
