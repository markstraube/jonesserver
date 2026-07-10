package com.trading.marketdata.service;

import com.trading.marketdata.model.DataQuality;
import com.trading.marketdata.model.DerivedMetrics;
import com.trading.marketdata.model.QuoteData;
import com.trading.marketdata.persistence.SnapshotEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Delta fields must refuse stale inputs — a delta against an outage-frozen value measures
 *  the outage, not the market. */
class DerivedMetricsStaleGateTest {

    private final DerivedMetricsService service = new DerivedMetricsService();

    private static QuoteData quote() {
        return new QuoteData("MU", 940.0, 10.0, 1.08, 930.0, 945.0, 928.0, 1_000_000L,
                null, null, null, null, null, null, null, null, null, null, null,
                "ibkr", null, true, Instant.now());
    }

    private static SnapshotEntity previous() {
        SnapshotEntity e = new SnapshotEntity();
        e.setSnapshotTs(Instant.now().minusSeconds(600));
        e.setPrice(930.0);
        e.setVolume(900_000L);
        return e;
    }

    private static DataQuality quality(boolean quoteStale) {
        DataQuality.Section fresh = new DataQuality.Section(1L, 5L, false, false);
        DataQuality.Section quote = new DataQuality.Section(quoteStale ? 999L : 1L, 5L, quoteStale, false);
        return new DataQuality(false, 1, quote, fresh, fresh, fresh);
    }

    @Test
    void freshQuoteProducesDeltas() {
        DerivedMetrics m = service.compute(quote(), null, null, previous(), quality(false));
        assertNotNull(m.priceDeltaPct());
        assertEquals(100_000L, m.volumeDelta());
    }

    @Test
    void staleQuoteSuppressesDeltasButKeepsWindow() {
        DerivedMetrics m = service.compute(quote(), null, null, previous(), quality(true));
        assertNull(m.priceDeltaPct(), "stale quote must not produce a price delta");
        assertNull(m.volumeDelta(), "stale quote must not produce a volume delta");
        assertNotNull(m.minutesSincePrevious(), "the window itself stays reported");
        assertNotNull(m.pctFromOpen(), "intraday position fields are not deltas and stay");
    }

    @Test
    void nullQualityImposesNoGate() {
        DerivedMetrics m = service.compute(quote(), null, null, previous(), null);
        assertNotNull(m.priceDeltaPct());
        assertNotNull(m.volumeDelta());
    }
}
