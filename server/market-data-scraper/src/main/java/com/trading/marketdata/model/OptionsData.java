package com.trading.marketdata.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.trading.marketdata.analysis.AggressorProfile;

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
        Double todayMaxPain,
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
     *
     * lastLocation/aggressor/lastAgeSeconds replace the former "sentiment" label
     * (2026-07-07). "PUT => BEARISH" classified the existence of an instrument, not the
     * direction of aggression: a put SOLD at the bid is a premium seller volunteering to
     * carry downside — neutral-to-bullish, the opposite of what the label claimed. Same
     * reasoning that removed the news sentiment classifier: a confidently wrong label is
     * worse than an honest raw feature. Interpretation (put bought at ask in a falling
     * tape = crash protection demand, put hit at bid = someone monetizing hedges or
     * writing premium) belongs in the analysis layer, with context this record can't see.
     *
     * lastLocation: (last - bid) / (ask - bid) against the quote pair FROZEN when the last
     *         tick arrived (falls back to the end-of-window pair when no freeze happened),
     *         clamped to [0,1]. 0 = printed at bid (seller-initiated), 1 = at ask
     *         (buyer-initiated) — the quote rule of Lee/Ready trade classification.
     * aggressor: AT_ASK (>= 0.75) / AT_BID (<= 0.25) / MID — or STALE when the last trade
     *         is older than the configured threshold (the quote has moved on; a location
     *         against today's quote would be fiction), or UNKNOWN when there is no last,
     *         no two-sided quote, or a crossed quote (ask <= bid).
     * lastAgeSeconds: now minus the last trade's own timestamp (IBKR tick 45/88), when
     *         delivered. The staleness measurement behind STALE — also lets the analysis
     *         layer weigh a 3-second-old print differently from a 3-hour-old one.
     *
     * Caveat that stays true no matter how clean the classification: one last print does
     * not classify the day's cumulative volume behind a UA signal, and spread legs execute
     * at negotiated prices where aggressor logic has no meaning. This is an honest feature
     * about the most recent trade, not a verdict about the flow.
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
            Double lastLocation,
            String aggressor,
            Long lastAgeSeconds,
            Double distanceToSpotPct,
            Long oiPrevious,
            AggressorProfile aggressorProfile
    ) {
        /** Stage-1 shape (no aggressor profile) — the scanner and the Barchart fallback
         *  create entries with this; stage 2 attaches the profile additively afterwards. */
        public UnusualActivity(String expiry, Double strike, String type, Long volume,
                Long openInterest, Double volumeOiRatio, Double bid, Double ask, Double last,
                Double premiumNotionalUsd, Double iv, Double lastLocation, String aggressor,
                Long lastAgeSeconds) {
            this(expiry, strike, type, volume, openInterest, volumeOiRatio, bid, ask, last,
                    premiumNotionalUsd, iv, lastLocation, aggressor, lastAgeSeconds, null, null, null);
        }

        /** Same entry with the stage-2 profile attached (records are immutable). */
        public UnusualActivity withAggressorProfile(AggressorProfile profile) {
            return new UnusualActivity(expiry, strike, type, volume, openInterest, volumeOiRatio,
                    bid, ask, last, premiumNotionalUsd, iv, lastLocation, aggressor,
                    lastAgeSeconds, distanceToSpotPct, oiPrevious, profile);
        }

        /** Post-scan enrichment: strike distance to spot and the previous session's OI for
         *  THIS contract (from the day memory). Both are consumer conveniences — the LLM
         *  should read them, not recompute/guess them. */
        public UnusualActivity enriched(Double distanceToSpotPct, Long oiPrevious) {
            return new UnusualActivity(expiry, strike, type, volume, openInterest, volumeOiRatio,
                    bid, ask, last, premiumNotionalUsd, iv, lastLocation, aggressor,
                    lastAgeSeconds, distanceToSpotPct, oiPrevious, aggressorProfile);
        }
    }

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
        return new OptionsData(ticker, null, null, null, null, null, null, null, null, null,
                null, errorMsg, false, Instant.now());
    }
}
