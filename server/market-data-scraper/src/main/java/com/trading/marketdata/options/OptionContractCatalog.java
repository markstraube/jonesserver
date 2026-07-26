package com.trading.marketdata.options;

import com.trading.marketdata.ibkr.IbkrOptionsChainResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe catalog of every strike/expiry actually reported by IBKR. */
@Service
public class OptionContractCatalog {
    private final Map<String, OptionChainSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<String, List<OptionChainChange>> changes = new ConcurrentHashMap<>();

    public OptionChainSnapshot update(String ticker, int conId, IbkrOptionsChainResult chain) {
        String symbol = ticker.toUpperCase();
        OptionChainSnapshot next = new OptionChainSnapshot(symbol, conId, chain.expirations(), chain.strikes(),
                chain.multiplier(), Instant.now());
        OptionChainSnapshot previous = snapshots.put(symbol, next);
        if (previous != null) detectChanges(previous, next);
        return next;
    }

    public OptionChainSnapshot current(String ticker) {
        return snapshots.get(ticker.toUpperCase());
    }

    public List<OptionChainChange> recentChanges(String ticker) {
        return List.copyOf(changes.getOrDefault(ticker.toUpperCase(), List.of()));
    }

    private void detectChanges(OptionChainSnapshot oldValue, OptionChainSnapshot newValue) {
        List<OptionChainChange> out = changes.computeIfAbsent(newValue.ticker(), k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        Instant now = newValue.discoveredAt();
        diff(oldValue.expirations(), newValue.expirations()).forEach(v -> out.add(new OptionChainChange(newValue.ticker(), OptionChainChange.ChangeType.EXPIRY_ADDED, v, null, null, now)));
        diff(newValue.expirations(), oldValue.expirations()).forEach(v -> out.add(new OptionChainChange(newValue.ticker(), OptionChainChange.ChangeType.EXPIRY_REMOVED, v, null, null, now)));
        diff(oldValue.strikes(), newValue.strikes()).forEach(v -> out.add(new OptionChainChange(newValue.ticker(), OptionChainChange.ChangeType.STRIKE_ADDED, null, v, null, now)));
        diff(newValue.strikes(), oldValue.strikes()).forEach(v -> out.add(new OptionChainChange(newValue.ticker(), OptionChainChange.ChangeType.STRIKE_REMOVED, null, v, null, now)));
        if (!java.util.Objects.equals(oldValue.multiplier(), newValue.multiplier())) {
            out.add(new OptionChainChange(newValue.ticker(), OptionChainChange.ChangeType.MULTIPLIER_CHANGED, null, null,
                    oldValue.multiplier() + " -> " + newValue.multiplier(), now));
        }
        // Keep bounded diagnostics per ticker.
        while (out.size() > 1000) out.remove(0);
    }

    private static <T> Set<T> diff(Set<T> left, Set<T> right) {
        java.util.HashSet<T> result = new java.util.HashSet<>(right);
        result.removeAll(left);
        return result;
    }
}
