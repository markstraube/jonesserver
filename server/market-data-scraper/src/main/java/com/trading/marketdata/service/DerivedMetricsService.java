package com.trading.marketdata.service;

import com.trading.marketdata.model.DerivedMetrics;
import com.trading.marketdata.model.OptionsData;
import com.trading.marketdata.model.QuoteData;
import com.trading.marketdata.model.ShortData;
import com.trading.marketdata.persistence.SnapshotEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Pure, deterministic feature computation on top of a raw snapshot. No I/O, no state, no
 * judgements — every field is arithmetic a reviewer can verify by hand. The previous-snapshot
 * parameter is optional; delta fields stay null without history.
 */
@Service
public class DerivedMetricsService {

    public DerivedMetrics compute(QuoteData quote, OptionsData options, ShortData shortData,
                                  SnapshotEntity previous) {
        // --- Intraday position ---
        Double prevClose = null, pctFromOpen = null, pctFromHigh = null, pctFromLow = null, rangePct = null;
        if (quote != null && quote.price() != null) {
            double price = quote.price();
            if (quote.change() != null) {
                prevClose = round4(price - quote.change());
            }
            if (quote.open() != null && quote.open() != 0) {
                pctFromOpen = round4((price / quote.open() - 1) * 100);
            }
            if (quote.high() != null && quote.high() != 0) {
                pctFromHigh = round4((price / quote.high() - 1) * 100);
            }
            if (quote.low() != null && quote.low() != 0) {
                pctFromLow = round4((price / quote.low() - 1) * 100);
            }
            if (quote.high() != null && quote.low() != null && prevClose != null && prevClose != 0) {
                rangePct = round4((quote.high() - quote.low()) / prevClose * 100);
            }
        }

        // --- Relative volume (RVOL): today's tape vs. the Finviz average day ---
        Double relativeVolume = null;
        if (quote != null && quote.volume() != null && quote.volume() > 0
                && shortData != null && shortData.avgVolume() != null && shortData.avgVolume() > 0) {
            relativeVolume = round4((double) quote.volume() / shortData.avgVolume());
        }

        // --- OI window aggregates ---
        // The chain-wide sums stay for backward compatibility (persisted as scalar columns),
        // but note they mix expiry boards with different meaning — the per-board breakdown
        // below is the number to actually read.
        Long oiCallTotal = null, oiPutTotal = null;
        Double oiPcr = null;
        List<DerivedMetrics.ExpiryOi> oiByExpiry = null;
        if (options != null && options.oiProfile() != null && !options.oiProfile().isEmpty()) {
            long calls = 0, puts = 0;
            java.util.Map<String, long[]> byExpiry = new java.util.TreeMap<>(); // sorted: near board first
            for (OptionsData.OiLevel level : options.oiProfile()) {
                long c = level.callOpenInterest() != null ? level.callOpenInterest() : 0;
                long p = level.putOpenInterest() != null ? level.putOpenInterest() : 0;
                calls += c;
                puts += p;
                if (level.expiry() != null) {
                    long[] sums = byExpiry.computeIfAbsent(level.expiry(), k -> new long[2]);
                    sums[0] += c;
                    sums[1] += p;
                }
            }
            oiCallTotal = calls;
            oiPutTotal = puts;
            if (calls > 0) {
                oiPcr = round4((double) puts / calls);
            }
            oiByExpiry = byExpiry.entrySet().stream()
                    .map(en -> new DerivedMetrics.ExpiryOi(
                            en.getKey(),
                            en.getValue()[0],
                            en.getValue()[1],
                            en.getValue()[0] > 0 ? round4((double) en.getValue()[1] / en.getValue()[0]) : null))
                    .collect(java.util.stream.Collectors.toList());
        }

        // --- Unusual activity aggregates ---
        Long uaCallVol = null, uaPutVol = null;
        Double uaCallNotional = null, uaPutNotional = null;
        Double uaCallPremium = null, uaPutPremium = null;
        if (options != null && options.unusualActivity() != null && !options.unusualActivity().isEmpty()) {
            long cv = 0, pv = 0;
            double cn = 0, pn = 0;
            double cp = 0, pp = 0;
            boolean anyCallPremium = false, anyPutPremium = false;
            for (OptionsData.UnusualActivity ua : options.unusualActivity()) {
                long vol = ua.volume() != null ? ua.volume() : 0;
                double notional = ua.strike() != null ? vol * ua.strike() * 100 : 0;
                if ("CALL".equals(ua.type())) {
                    cv += vol; cn += notional;
                    if (ua.premiumNotionalUsd() != null) { cp += ua.premiumNotionalUsd(); anyCallPremium = true; }
                } else if ("PUT".equals(ua.type())) {
                    pv += vol; pn += notional;
                    if (ua.premiumNotionalUsd() != null) { pp += ua.premiumNotionalUsd(); anyPutPremium = true; }
                }
            }
            uaCallVol = cv;
            uaPutVol = pv;
            uaCallNotional = round2(cn);
            uaPutNotional = round2(pn);
            // Deliberately null (not 0) when no flagged contract carried price ticks: "no
            // premium data" and "premium of zero dollars" must stay distinguishable. When some
            // contracts carried prices and others didn't, this is a lower bound.
            uaCallPremium = anyCallPremium ? round2(cp) : null;
            uaPutPremium = anyPutPremium ? round2(pp) : null;
        }

        // --- Deltas vs. previous persisted snapshot ---
        Double priceDeltaPct = null, oiPcrDelta = null;
        Long volumeDelta = null, minutesSince = null;
        java.time.Instant previousAt = null;
        if (previous != null) {
            previousAt = previous.getSnapshotTs();
            if (previousAt != null) {
                minutesSince = Duration.between(previousAt, java.time.Instant.now()).toMinutes();
            }
            if (quote != null && quote.price() != null
                    && previous.getPrice() != null && previous.getPrice() != 0) {
                priceDeltaPct = round4((quote.price() / previous.getPrice() - 1) * 100);
            }
            if (quote != null && quote.volume() != null && previous.getVolume() != null) {
                volumeDelta = quote.volume() - previous.getVolume();
            }
            if (oiPcr != null && previous.getOiPutCallRatio() != null) {
                oiPcrDelta = round4(oiPcr - previous.getOiPutCallRatio());
            }
        }

        return new DerivedMetrics(
                prevClose, pctFromOpen, pctFromHigh, pctFromLow, rangePct, relativeVolume,
                oiCallTotal, oiPutTotal, oiPcr, oiByExpiry,
                uaCallVol, uaPutVol, uaCallNotional, uaPutNotional, uaCallPremium, uaPutPremium,
                priceDeltaPct, volumeDelta, oiPcrDelta, minutesSince, previousAt);
    }

    private static Double round4(double v) {
        return Math.round(v * 10_000d) / 10_000d;
    }

    private static Double round2(double v) {
        return Math.round(v * 100d) / 100d;
    }
}
