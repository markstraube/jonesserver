package com.trading.marketdata.service;

import com.trading.marketdata.model.DerivedMetrics;
import com.trading.marketdata.model.QuoteData;
import com.trading.marketdata.model.ShortData;
import com.trading.marketdata.persistence.SnapshotEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionBoundaryMetricsTest {

    private final DerivedMetricsService service = new DerivedMetricsService();

    @Test
    void overnightLegsAcrossMidnightShareTradingDate() {
        Instant sundayEveningEt = Instant.parse("2026-07-20T01:00:00Z"); // Sun 21:00 ET
        Instant mondayMorningEt = Instant.parse("2026-07-20T07:00:00Z"); // Mon 03:00 ET

        assertTrue(DerivedMetricsService.sameTradingSession(
                "OVERNIGHT", mondayMorningEt, "OVERNIGHT", sundayEveningEt));
    }

    @Test
    void differentStatesDoNotProduceSnapshotDeltas() {
        Instant currentAt = Instant.parse("2026-07-17T07:19:55Z");
        QuoteData quote = quote(currentAt, 815.01, 1_009_883L);
        SnapshotEntity previous = previous(
                Instant.parse("2026-07-16T23:57:59Z"), "POST", 832.66, 46_719_368L);

        DerivedMetrics metrics = service.compute(
                quote, null, shortData(), previous, null, 0.02, "OVERNIGHT");

        assertNull(metrics.priceDeltaPct());
        assertNull(metrics.volumeDelta());
        assertNull(metrics.relativeVolume(), "full-day RVOL is hidden outside REGULAR");
        assertNull(metrics.relativeVolumeAtTime());
    }

    @Test
    void cumulativeVolumeResetIsNeverReportedAsNegativeVolume() {
        Instant currentAt = Instant.parse("2026-07-17T15:00:00Z");
        QuoteData quote = quote(currentAt, 820.0, 100_000L);
        SnapshotEntity previous = previous(
                Instant.parse("2026-07-17T14:55:00Z"), "REGULAR", 819.0, 900_000L);

        DerivedMetrics metrics = service.compute(
                quote, null, shortData(), previous, null, 0.10, "REGULAR");

        assertEquals(0.1221, metrics.priceDeltaPct());
        assertNull(metrics.volumeDelta());
    }

    @Test
    void overnightScheduleRecognizesFridayEarlyMorningButNotFridayEvening() {
        assertTrue(MarketStateService.isOvernightWindow(Instant.parse("2026-07-17T07:19:00Z")));
        assertFalse(MarketStateService.isOvernightWindow(Instant.parse("2026-07-18T01:00:00Z")));
    }

    private static QuoteData quote(Instant fetchedAt, double price, long volume) {
        return new QuoteData("MU", price, null, null, null, null, null, volume,
                null, null, null, null, null, null, null, null, null, null, null,
                "ibkr", null, true, fetchedAt);
    }

    private static SnapshotEntity previous(Instant at, String state, double price, long volume) {
        SnapshotEntity entity = new SnapshotEntity();
        entity.setSnapshotTs(at);
        entity.setMarketState(state);
        entity.setPrice(price);
        entity.setVolume(volume);
        return entity;
    }

    private static ShortData shortData() {
        return new ShortData("MU", null, null, null, null, null, null,
                51_100_000L, "test", null, true, Instant.now());
    }
}
