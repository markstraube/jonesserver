package com.trading.marketdata.service;

import com.trading.marketdata.analysis.IntradayVolumeCurve;
import com.trading.marketdata.persistence.SnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds and caches the per-symbol {@link IntradayVolumeCurve} from the service's own
 * persisted snapshots, and answers the one question the derived layer needs: what share of
 * an average day's volume is normally on the tape by NOW? The curve is rebuilt at most once
 * per ET day per ticker (cache key carries the date); the underlying query is one indexed
 * range scan over (ticker, snapshotTs).
 *
 * Returns null — and thereby keeps the derived fields null — when history is too thin
 * (fewer than {@code min-days} qualifying days), when the DB is unreachable, or in the
 * opening minutes where the expected share is so small that the ratio would amplify noise.
 */
@Service
public class IntradayVolumeService {

    private static final Logger log = LoggerFactory.getLogger(IntradayVolumeService.class);
    private static final ZoneId ET = ZoneId.of("America/New_York");
    private static final LocalTime SESSION_OPEN = LocalTime.of(9, 30);

    private final SnapshotRepository repository;
    private final CacheManager cacheManager;

    @Value("${volume.curve.lookback-days:20}")
    int lookbackDays = 20;

    @Value("${volume.curve.min-days:5}")
    int minDays = 5;

    /** Below this expected share the normalized ratio amplifies opening noise — stay null. */
    @Value("${volume.curve.min-share:0.02}")
    double minShare = 0.02;

    public IntradayVolumeService(SnapshotRepository repository, CacheManager cacheManager) {
        this.repository = repository;
        this.cacheManager = cacheManager;
    }

    /**
     * Expected cumulative share of an average day's volume at this moment, from the
     * ticker's own curve — or null (thin history, DB down, pre-market, opening noise
     * floor). 1.0 after the close, so downstream time-normalized RVOL converges to plain
     * RVOL exactly when the day is complete.
     */
    public Double expectedShareNow(String ticker) {
        ZonedDateTime nowEt = ZonedDateTime.now(ET);
        int secondsIntoSession = (int) java.time.Duration
                .between(nowEt.toLocalDate().atTime(SESSION_OPEN).atZone(ET), nowEt).getSeconds();
        if (secondsIntoSession <= 0) return null; // pre-market: no expectation to normalize against

        IntradayVolumeCurve curve = curveFor(ticker.toUpperCase(), nowEt.toLocalDate());
        if (curve == null) return null;
        double share = curve.shareAt(secondsIntoSession);
        return share < minShare ? null : share;
    }

    private IntradayVolumeCurve curveFor(String ticker, LocalDate todayEt) {
        String key = ticker + ":" + todayEt; // date in the key = daily invalidation for free
        Cache cache = cacheManager.getCache("intradayVolumeCurve");
        if (cache != null) {
            Cache.ValueWrapper cached = cache.get(key);
            if (cached != null) {
                return (IntradayVolumeCurve) cached.get(); // may be a cached null (thin history)
            }
        }
        IntradayVolumeCurve curve = buildCurve(ticker, todayEt);
        if (cache != null) {
            cache.put(key, curve); // cache nulls too: a thin history stays thin for the day
        }
        return curve;
    }

    private IntradayVolumeCurve buildCurve(String ticker, LocalDate todayEt) {
        List<Object[]> rows;
        try {
            Instant from = todayEt.minusDays(lookbackDays).atTime(SESSION_OPEN).atZone(ET).toInstant();
            rows = repository.findVolumeSeries(ticker, from);
        } catch (Exception e) {
            log.warn("Intraday volume curve for {}: history query failed: {}", ticker, e.getMessage());
            return null;
        }

        // Group into per-ET-day series of (secondsIntoSession, cumulativeVolume). TODAY is
        // excluded — its final volume does not exist yet; a same-day series would qualify
        // late in the session and then contribute shares against a still-growing
        // denominator. Snapshots outside the regular session are clamped to its edges,
        // consistent with shareAt's clamping on the query side.
        Map<LocalDate, List<IntradayVolumeCurve.Point>> days = new TreeMap<>();
        for (Object[] row : rows) {
            Instant ts = (Instant) row[0];
            Long volume = (Long) row[1];
            if (volume == null || volume <= 0) continue;
            ZonedDateTime et = ts.atZone(ET);
            LocalDate day = et.toLocalDate();
            if (day.equals(todayEt)) continue;
            int seconds = (int) java.time.Duration
                    .between(day.atTime(SESSION_OPEN).atZone(ET), et).getSeconds();
            seconds = Math.max(0, Math.min(seconds, IntradayVolumeCurve.SESSION_SECONDS));
            days.computeIfAbsent(day, d -> new ArrayList<>())
                    .add(new IntradayVolumeCurve.Point(seconds, volume));
        }

        IntradayVolumeCurve curve = IntradayVolumeCurve.build(new ArrayList<>(days.values()), minDays);
        log.info("Intraday volume curve for {}: {} days in window, curve={}", ticker, days.size(),
                curve == null ? "null (thin history)" : curve.daysUsed() + " days used");
        return curve;
    }
}
