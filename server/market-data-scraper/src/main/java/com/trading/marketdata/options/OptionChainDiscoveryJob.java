package com.trading.marketdata.options;

import com.trading.marketdata.book.SubscriptionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Refreshes chain definitions independently from market snapshots and flow collection. */
@Service
public class OptionChainDiscoveryJob {
    private static final Logger log = LoggerFactory.getLogger(OptionChainDiscoveryJob.class);
    private final SubscriptionManager subscriptions;
    private final OptionChainDiscoveryCollector collector;

    public OptionChainDiscoveryJob(SubscriptionManager subscriptions, OptionChainDiscoveryCollector collector) {
        this.subscriptions = subscriptions;
        this.collector = collector;
    }

    @Scheduled(initialDelayString = "${options.chain.discovery-initial-delay-ms:45000}",
               fixedDelayString = "${options.chain.discovery-interval-ms:21600000}")
    public void refreshWatchlist() {
        for (String symbol : subscriptions.bookSymbols()) {
            if (symbol.equals(subscriptions.anchorSymbol())) continue;
            try {
                OptionChainSnapshot chain = collector.refresh(symbol);
                if (chain != null) {
                    log.info("Option chain discovery {}: expiries={} strikes={} discoveredAt={}",
                            symbol, chain.expirations().size(), chain.strikes().size(), chain.discoveredAt());
                }
            } catch (Exception e) {
                log.warn("Option chain discovery {} failed: {}", symbol, e.getMessage());
            }
        }
    }
}
