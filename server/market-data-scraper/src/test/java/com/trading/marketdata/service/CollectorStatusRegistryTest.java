package com.trading.marketdata.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CollectorStatusRegistryTest {

    @Test
    void computesWarningHealthFromPartialCollector() {
        CollectorStatusRegistry registry = new CollectorStatusRegistry();
        registry.start("MU", "chainDiscovery").ok(Map.of("strikes", 100));
        registry.start("MU", "quoteHistory").partial(Map.of("coverage", 0.8), "sampled");

        var snapshot = registry.snapshot("mu");
        assertEquals("WARNING", snapshot.systemHealth().status());
        assertEquals(1, snapshot.systemHealth().warnings());
        assertEquals(0, snapshot.systemHealth().errors());
        assertEquals("OK", snapshot.collectors().get("chainDiscovery").status());
        assertNotNull(snapshot.collectors().get("quoteHistory").lastRun());
    }

    @Test
    void errorDominatesWarning() {
        CollectorStatusRegistry registry = new CollectorStatusRegistry();
        registry.start("SNDK", "openInterest").partial(Map.of(), "empty");
        registry.start("SNDK", "tradeHistory").error(new IllegalStateException("offline"), Map.of());

        var snapshot = registry.snapshot("SNDK");
        assertEquals("ERROR", snapshot.systemHealth().status());
        assertEquals(1, snapshot.systemHealth().warnings());
        assertEquals(1, snapshot.systemHealth().errors());
    }
}
