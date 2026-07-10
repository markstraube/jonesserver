package com.trading.marketdata.book;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The single in-memory source of truth for current market state: one {@link TickerBook} per
 * symbol, written by the streaming subscriptions (EReader thread) and the UA/OI scanner,
 * read synchronously by the REST/snapshot path. Reads never block on IBKR.
 *
 * Lifecycle: on connection loss every field of every ticker is invalidated — ages freeze
 * naturally (no ticks arrive) and the connectionLost flag marks the values as last-known
 * rather than live. Values are deliberately NOT cleared. On reconnect the
 * SubscriptionManager resubscribes and incoming ticks clear the invalidation field by field.
 */
@Component
public class MarketDataBook {

    private static final Logger log = LoggerFactory.getLogger(MarketDataBook.class);

    private final Map<String, TickerBook> books = new ConcurrentHashMap<>();

    private volatile boolean connectionLost;
    private volatile Instant connectionLostAt;

    /** Book for a symbol, created on first access. Symbol is normalized to upper case. */
    public TickerBook book(String symbol) {
        return books.computeIfAbsent(symbol.toUpperCase(), TickerBook::new);
    }

    /** Book for a symbol or null — never creates. For read paths that must not leave garbage. */
    public TickerBook find(String symbol) {
        return books.get(symbol.toUpperCase());
    }

    public Collection<TickerBook> all() {
        return books.values();
    }

    /**
     * Connection lost: freeze the world. Every field of every ticker is flagged invalidated,
     * values stay readable as honestly-aged last knowns.
     */
    public void invalidateAll(String reason) {
        connectionLost = true;
        connectionLostAt = Instant.now();
        books.values().forEach(TickerBook::invalidateAll);
        log.warn("Book invalidated ({} tickers): {}", books.size(), reason);
    }

    /** Called by the SubscriptionManager once streams are re-established. Individual fields
     *  stay invalidated until their first fresh tick clears them. */
    public void markConnected() {
        connectionLost = false;
        connectionLostAt = null;
    }

    public boolean isConnectionLost() {
        return connectionLost;
    }

    public Instant connectionLostAt() {
        return connectionLostAt;
    }
}
