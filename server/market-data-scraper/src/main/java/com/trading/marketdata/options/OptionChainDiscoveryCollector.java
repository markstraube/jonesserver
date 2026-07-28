package com.trading.marketdata.options;

import com.trading.marketdata.ibkr.IbkrMarketDataService;
import com.trading.marketdata.ibkr.IbkrOptionsChainResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.trading.marketdata.service.CollectorStatusRegistry;
import java.util.Map;

import java.time.Duration;
import java.time.Instant;

/** Independent chain discovery collector with its own refresh cadence. */
@Service
public class OptionChainDiscoveryCollector {
    private final IbkrMarketDataService ibkr;
    private final OptionContractCatalog catalog;
    private final CollectorStatusRegistry collectorStatus;

    @Value("${options.chain.max-age-seconds:21600}")
    private long maxAgeSeconds;

    public OptionChainDiscoveryCollector(IbkrMarketDataService ibkr, OptionContractCatalog catalog,
                                         CollectorStatusRegistry collectorStatus) {
        this.ibkr = ibkr;
        this.catalog = catalog;
        this.collectorStatus = collectorStatus;
    }

    public OptionChainSnapshot getOrRefresh(String ticker) {
        OptionChainSnapshot current = catalog.current(ticker);
        if (current != null && Duration.between(current.discoveredAt(), Instant.now()).getSeconds() < maxAgeSeconds) {
            return current;
        }
        return refresh(ticker);
    }

    public OptionChainSnapshot refresh(String ticker) {
        String symbol = ticker.toUpperCase();
        CollectorStatusRegistry.Run run = collectorStatus.start(symbol, "chainDiscovery", java.util.List.of("ibkrConnection", "underlyingLookup"));
        int changesBefore = catalog.recentChanges(symbol).size();
        try {
            Integer conId = ibkr.fetchConId(symbol);
            if (conId == null) {
                run.skipped(Map.of("contracts", 0), "underlying conId unavailable");
                return catalog.current(symbol);
            }
            IbkrOptionsChainResult chain = ibkr.fetchOptionsChain(symbol, conId);
            if (chain == null || chain.expirations().isEmpty() || chain.strikes().isEmpty()) {
                run.partial(Map.of("underlyingConId", conId, "contracts", 0), "empty option chain");
                return catalog.current(symbol);
            }
            OptionChainSnapshot snapshot = catalog.update(symbol, conId, chain);
            int newChanges = Math.max(0, catalog.recentChanges(symbol).size() - changesBefore);
            run.ok(Map.of(
                    "underlyingConId", conId,
                    "expiries", snapshot.expirations().size(),
                    "strikes", snapshot.strikes().size(),
                    "changes", newChanges,
                    "multiplier", snapshot.multiplier() == null ? "" : snapshot.multiplier()
            ));
            return snapshot;
        } catch (RuntimeException e) {
            run.error(e, Map.of());
            throw e;
        }
    }
}
