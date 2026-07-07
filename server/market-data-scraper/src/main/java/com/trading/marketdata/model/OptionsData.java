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
        Double iv,
        Double hv,
        List<UnusualActivity> unusualActivity,
        List<OiLevel> oiProfile,
        Double maxPain,
        String source,
        String sourceError,
        boolean dataAvailable,
        Instant fetchedAt
) {
    /**
     * bid/ask/last are the flagged contract's own prices at scan time — default ticks on
     * the same IBKR subscription as volume, zero extra request cost. premiumNotionalUsd is
     * volume x premium x 100 with premium = last (falls back to bid/ask mid); this measures
     * actual money spent, where strike-referenced notional overstates cheap OTM lottery
     * tickets by up to ~100x. Null when neither a last nor a two-sided quote arrived —
     * never fabricated. Caveat: for a contract that hasn't traded today, IBKR's "last" is
     * the previous session's print — order-of-magnitude correct, not tradeable pricing.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UnusualActivity(
            String expiry,
            Double strike,
            String type,
            Long volume,
            Long openInterest,
            Double volumeOiRatio,
            Double bid,
            Double ask,
            Double last,
            Double premiumNotionalUsd,
            Double iv,
            String sentiment
    ) {}

    /**
     * Open interest for calls and puts at one strike/expiry, regardless of whether today's
     * volume flagged it as "unusual" — the raw material for eyeballing where dealer hedging
     * flow is likely to create resistance/support as price approaches a strike. Covers the
     * same strikes/expiry scanned for unusualActivity, sorted by strike ascending so it reads
     * top-to-bottom as a resistance ladder around the current price.
     *
     * Serialization is deliberately ALWAYS (not NON_NULL): "OI is 0" and "the OI tick never
     * arrived" are different facts, and NON_NULL erased the difference on the wire (live
     * 2026-07-06: SNDK 20260710/1810 delivered call OI but no put tick — the field silently
     * vanished from the JSON and downstream consumers could not tell a gap from a zero).
     * An explicit null means: contract exists, tick missing.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record OiLevel(
            String expiry,
            Double strike,
            Long callOpenInterest,
            Long putOpenInterest
    ) {}

    public static OptionsData empty(String ticker, String errorMsg) {
        return new OptionsData(ticker, null, null, null, null, null, null, null, null,
                null, errorMsg, false, Instant.now());
    }
}
