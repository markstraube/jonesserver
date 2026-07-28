package com.trading.marketdata.service;

import org.junit.jupiter.api.Test;

import java.util.List;
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
        assertEquals("COMPLETED", snapshot.collectors().get("chainDiscovery").lifecycle());
        assertNotNull(snapshot.collectors().get("quoteHistory").lastRun());
    }

    @Test
    void globalIbkrErrorDominatesTickerWarning() {
        CollectorStatusRegistry registry = new CollectorStatusRegistry();
        registry.reportGlobal("ibkrConnection", "ERROR", "FAILED",
                Map.of("connected", false), "IBKR connection unavailable");
        registry.start("SNDK", "openInterest").partial(Map.of(), "empty");

        var snapshot = registry.snapshot("SNDK");
        assertEquals("ERROR", snapshot.systemHealth().status());
        assertEquals(1, snapshot.systemHealth().warnings());
        assertEquals(1, snapshot.systemHealth().errors());
        assertEquals(false, snapshot.collectors().get("ibkrConnection").metrics().get("connected"));
    }

    @Test
    void skippedRunIsExplicitAndCarriesDependencies() {
        CollectorStatusRegistry registry = new CollectorStatusRegistry();
        registry.start("MU", "chainDiscovery", List.of("ibkrConnection", "underlyingLookup"))
                .skipped(Map.of("contracts", 0), "underlying conId unavailable");

        var run = registry.snapshot("MU").collectors().get("chainDiscovery");
        assertEquals("WARNING", run.status());
        assertEquals("SKIPPED", run.lifecycle());
        assertEquals(List.of("ibkrConnection", "underlyingLookup"), run.dependencies());
        assertEquals(0, run.metrics().get("itemsRequested"));
    }
}
