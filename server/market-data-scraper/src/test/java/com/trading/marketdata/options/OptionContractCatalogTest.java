package com.trading.marketdata.options;

import com.trading.marketdata.ibkr.IbkrOptionsChainResult;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OptionContractCatalogTest {
    @Test
    void detectsAddedAndRemovedStrikesAndExpiries() {
        OptionContractCatalog catalog = new OptionContractCatalog();
        catalog.update("MU", 123, new IbkrOptionsChainResult(Set.of("20260724"), Set.of(900.0, 902.5), "100"));
        catalog.update("MU", 123, new IbkrOptionsChainResult(Set.of("20260731"), Set.of(902.5, 905.0), "100"));

        var changes = catalog.recentChanges("MU");
        assertTrue(changes.stream().anyMatch(c -> c.type() == OptionChainChange.ChangeType.EXPIRY_ADDED && "20260731".equals(c.expiry())));
        assertTrue(changes.stream().anyMatch(c -> c.type() == OptionChainChange.ChangeType.EXPIRY_REMOVED && "20260724".equals(c.expiry())));
        assertTrue(changes.stream().anyMatch(c -> c.type() == OptionChainChange.ChangeType.STRIKE_ADDED && Double.valueOf(905.0).equals(c.strike())));
        assertTrue(changes.stream().anyMatch(c -> c.type() == OptionChainChange.ChangeType.STRIKE_REMOVED && Double.valueOf(900.0).equals(c.strike())));
    }
}
