package com.trading.marketdata.book;

import com.ib.client.Contract;
import com.trading.marketdata.ibkr.IbkrConnectedEvent;
import com.trading.marketdata.ibkr.IbkrConnectionManager;
import com.trading.marketdata.ibkr.IbkrDisconnectedEvent;
import com.trading.marketdata.ibkr.IbkrWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns all permanent streaming market-data subscriptions and their lifecycle: one
 * {@code reqMktData} line per Book symbol (watchlist + liveness anchor), established on
 * connect, re-established on reconnect with fresh reqIds, staggered to respect IBKR pacing
 * limits (≤ ~50 msgs/sec — 100ms apart is plenty).
 *
 * WHY PERMANENT STREAMS AND NEVER snapshot=true: the TWS API is an asynchronous tick stream;
 * a snapshot=true request is answered from the Gateway's LOCAL CACHE and closed at exactly
 * that cache state — in a fast market the delivered LAST is "true but old" (observed live
 * 2026-07: LAST stuck at the 902.40 opening print while the market traded ~940). The
 * fetch-on-request era worked around this with a volume-tick freshness sentinel plus a grace
 * window per request; with a permanent stream the workaround is obsolete by construction —
 * the subscription has long outlived the initial cache image, so every stored tick reflects
 * the market. snapshot=true also cannot carry generic tick lists at all per IBKR's docs, so
 * the options-metrics and auction ticks of these lines are unreachable via snapshot anyway.
 */
@Component
public class SubscriptionManager {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionManager.class);

    private final IbkrConnectionManager connectionManager;
    private final IbkrWrapper wrapper;
    private final MarketDataBook book;

    @Value("${book.watchlist:MU,SNDK,INTC,AVGO,MRVL,AMD}")
    private List<String> watchlist;

    @Value("${book.liveness-anchor:SPY}")
    private String livenessAnchor;

    @Value("${book.subscribe-stagger-ms:100}")
    private long subscribeStaggerMs;

    /** symbol → active reqId. Cleared on disconnect, rebuilt with fresh reqIds on connect. */
    private final Map<String, Integer> activeSubscriptions = new ConcurrentHashMap<>();
    private final Object subscribeLock = new Object();

    public SubscriptionManager(IbkrConnectionManager connectionManager,
                               IbkrWrapper wrapper,
                               MarketDataBook book) {
        this.connectionManager = connectionManager;
        this.wrapper = wrapper;
        this.book = book;
    }

    /** Covers the initial @PostConstruct connect, which fires before event listeners exist. */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (connectionManager.isConnected()) {
            resubscribeAll("startup");
        }
    }

    @EventListener(IbkrConnectedEvent.class)
    public void onIbkrConnected(IbkrConnectedEvent event) {
        resubscribeAll("reconnect");
    }

    /** Book invalidation (values kept, flagged) — the subscriptions themselves died with the
     *  socket, so only local state is dropped here. Idempotent: both the connectionClosed
     *  callback and the reader-loop exit can publish this for one drop. */
    @EventListener(IbkrDisconnectedEvent.class)
    public void onIbkrDisconnected(IbkrDisconnectedEvent event) {
        activeSubscriptions.clear();
        book.invalidateAll(event.reason());
    }

    /** All Book symbols: watchlist plus the liveness anchor, deduped, upper case. */
    public Set<String> bookSymbols() {
        Set<String> symbols = new LinkedHashSet<>();
        for (String s : watchlist) {
            if (s != null && !s.isBlank()) symbols.add(s.trim().toUpperCase());
        }
        symbols.add(livenessAnchor.trim().toUpperCase());
        return symbols;
    }

    public boolean isBookSymbol(String symbol) {
        return bookSymbols().contains(symbol.toUpperCase());
    }

    public String anchorSymbol() {
        return livenessAnchor.trim().toUpperCase();
    }

    /** Runs asynchronously — subscription setup sleeps between requests (pacing stagger)
     *  and must never block an event-listener or scheduler thread. */
    public void resubscribeAll(String trigger) {
        Thread.ofVirtual().name("book-subscribe").start(() -> doSubscribeAll(trigger));
    }

    private void doSubscribeAll(String trigger) {
        synchronized (subscribeLock) {
            if (!connectionManager.isConnected()) {
                log.warn("Book subscribe ({}) skipped — not connected", trigger);
                return;
            }
            // Cancel leftovers first (relevant when re-triggered on a live connection —
            // after a socket loss the old reqIds are already dead and this is a no-op).
            activeSubscriptions.forEach((symbol, reqId) -> {
                try {
                    connectionManager.getClient().cancelMktData(reqId);
                } catch (Exception e) {
                    log.debug("cancelMktData({}) for {} failed: {}", reqId, symbol, e.getMessage());
                }
            });
            activeSubscriptions.clear();
            wrapper.clearBookRoutes();

            // 1 = realtime. Delayed/frozen switches (2–4) arrive via the marketDataType
            // callback per subscription and land in the Book's dataQuality state.
            connectionManager.getClient().reqMarketDataType(1);

            Set<String> symbols = bookSymbols();
            int firstReqId = -1, lastReqId = -1;
            for (String symbol : symbols) {
                int reqId = connectionManager.nextReqId();
                if (firstReqId < 0) firstReqId = reqId;
                lastReqId = reqId;
                wrapper.registerBookRoute(reqId, symbol);
                connectionManager.getClient().reqMktData(
                        reqId, usStockContract(symbol), genericTickList(), false, false, null);
                activeSubscriptions.put(symbol, reqId);
                sleepQuietly(subscribeStaggerMs);
            }
            book.markConnected();
            log.info("BOOK_SUBSCRIBE_SUMMARY trigger={} subscriptions={} reqIds={}..{} symbols={}",
                    trigger, activeSubscriptions.size(), firstReqId, lastReqId, symbols);
        }
    }

    /**
     * Generic ticks of every Book line — one market-data line per symbol carries quote,
     * options metrics AND auction/NOII. Classic TWS API trap: the REQUESTED generic tick
     * numbers and the DELIVERED tick type ids differ —
     *   requested 100 (option volume)    → arrives as tickSize 29 (call) / 30 (put);
     *                                      there is NO put/call-ratio tick, the PCR is
     *                                      computed from 29/30 (TickerBook.putCallRatio)
     *   requested 104 (30d historical vol) → arrives as tickGeneric 23
     *   requested 106 (30d implied vol)    → arrives as tickGeneric 24
     *   requested 225 (RT auction values)  → arrives as tickPrice 35 (auction price) and
     *                                        tickSize 34/36/61 (volume/imbalance/regulatory)
     * Generic ticks REQUIRE a streaming subscription: snapshot=true cannot carry a generic
     * tick list at all per IBKR's docs.
     */
    private String genericTickList() {
        return "100,104,106,225";
    }

    Map<String, Integer> activeSubscriptions() {
        return activeSubscriptions;
    }

    private static Contract usStockContract(String ticker) {
        Contract c = new Contract();
        c.symbol(ticker);
        c.secType("STK");
        c.currency("USD");
        c.exchange("SMART"); // SMART routing picks the best exchange automatically
        return c;
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
