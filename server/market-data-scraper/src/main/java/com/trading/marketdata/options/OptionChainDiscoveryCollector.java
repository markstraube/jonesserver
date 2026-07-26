package com.trading.marketdata.options;

import com.trading.marketdata.ibkr.IbkrMarketDataService;
import com.trading.marketdata.ibkr.IbkrOptionsChainResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/** Independent chain discovery collector with its own refresh cadence. */
@Service
public class OptionChainDiscoveryCollector {
    private final IbkrMarketDataService ibkr;
    private final OptionContractCatalog catalog;

    @Value("${options.chain.max-age-seconds:21600}")
    private long maxAgeSeconds;

    public OptionChainDiscoveryCollector(IbkrMarketDataService ibkr, OptionContractCatalog catalog) {
        this.ibkr = ibkr;
        this.catalog = catalog;
    }

    public OptionChainSnapshot getOrRefresh(String ticker) {
        OptionChainSnapshot current = catalog.current(ticker);
        if (current != null && Duration.between(current.discoveredAt(), Instant.now()).getSeconds() < maxAgeSeconds) {
            return current;
        }
        return refresh(ticker);
    }

    public OptionChainSnapshot refresh(String ticker) {
        Integer conId = ibkr.fetchConId(ticker.toUpperCase());
        if (conId == null) return catalog.current(ticker);
        IbkrOptionsChainResult chain = ibkr.fetchOptionsChain(ticker.toUpperCase(), conId);
        if (chain == null || chain.expirations().isEmpty() || chain.strikes().isEmpty()) return catalog.current(ticker);
        return catalog.update(ticker, conId, chain);
    }
}
