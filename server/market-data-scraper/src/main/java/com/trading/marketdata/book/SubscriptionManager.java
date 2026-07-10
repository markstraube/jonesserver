package com.trading.marketdata.book;

import com.ib.client.Contract;
import com.trading.marketdata.ibkr.IbkrConnectedEvent;
import com.trading.marketdata.ibkr.IbkrConnectionManager;
import com.trading.marketdata.ibkr.IbkrDisconnectedEvent;
import com.trading.marketdata.ibkr.IbkrSubscriptionErrorEvent;
import com.trading.marketdata.ibkr.IbkrWrapper;
import com.trading.marketdata.service.MarketStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
 *
 * LIVENESS WATCHDOG: on a tick stream, silence is indistinguishable from stillness — unless
 * something else proves the feed is alive. The anchor (SPY) ticks near-continuously during
 * REGULAR; if its volume was seen within {@code anchorFreshSeconds} but another ticker's
 * volume has been silent for {@code tickerStaleSeconds}, that ticker's stream is dead, not
 * calm: cancel + resubscribe it (AUTO_RESUBSCRIBE). The watchdog only acts in REGULAR
 * (pre/post/closed silence is normal) and stands down inside the expected nightly IBC
 * Gateway restart window, where a dead-looking world is routine.
 */
@Component
public class SubscriptionManager {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionManager.class);
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final IbkrConnectionManager connectionManager;
    private final IbkrWrapper wrapper;
    private final MarketDataBook book;
    private final MarketStateService marketStateService;

    @Value("${book.watchlist:MU,SNDK,INTC,AVGO,MRVL,AMD}")
    private List<String> watchlist;

    @Value("${book.liveness-anchor:SPY}")
    private String livenessAnchor;

    @Value("${book.subscribe-stagger-ms:100}")
    private long subscribeStaggerMs;

    @Value("${book.anchor-fresh-seconds:10}")
    private long anchorFreshSeconds;

    @Value("${book.ticker-stale-seconds:120}")
    private long tickerStaleSeconds;

    /** Backoff before retrying a subscription rejected with error 100 (pacing violation). */
    @Value("${book.pacing-retry-delay-ms:5000}")
    private long pacingRetryDelayMs;

    /**
     * Expected nightly IBC Gateway restart window, ET, HH:mm-HH:mm (may span midnight).
     * Inside it the liveness watchdog stands down — the restart is routine, not an error.
     * Empty disables the window.
     */
    @Value("${ibkr.expected-restart-window:23:45-00:20}")
    private String expectedRestartWindow;

    /** One live subscription: its reqId and when it was opened (the age reference for
     *  tickers whose volume tick has not arrived at all yet). */
    record Subscription(int reqId, Instant subscribedAt) {}

    private final Map<String, Subscription> activeSubscriptions = new ConcurrentHashMap<>();
    /** symbol → distinct failure reason (PACING_VIOLATION / MAX_TICKERS / code) for the
     *  summary report. Cleared by the next full resubscribe. */
    private final Map<String, String> failedSubscriptions = new ConcurrentHashMap<>();
    private final Object subscribeLock = new Object();

    public SubscriptionManager(IbkrConnectionManager connectionManager,
                               IbkrWrapper wrapper,
                               MarketDataBook book,
                               MarketStateService marketStateService) {
        this.connectionManager = connectionManager;
        this.wrapper = wrapper;
        this.book = book;
        this.marketStateService = marketStateService;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

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

    /**
     * Errors 100 and 101 on a Book line, kept strictly apart (they need opposite reactions):
     *   100 — pacing violation: transient, the line may open on retry → resubscribe this one
     *         symbol after a backoff. The subscribe stagger should make this unreachable.
     *   101 — max market-data lines reached: NOT transient, retrying cannot succeed until
     *         lines are freed → mark failed until the next full resubscribe, retry nothing.
     * Both reasons appear verbatim in the BOOK_SUBSCRIBE_SUMMARY report.
     */
    @EventListener(IbkrSubscriptionErrorEvent.class)
    public void onSubscriptionError(IbkrSubscriptionErrorEvent event) {
        switch (event.errorCode()) {
            case 100 -> {
                failedSubscriptions.put(event.symbol(), "PACING_VIOLATION(100)");
                Thread.ofVirtual().name("book-pacing-retry").start(() -> {
                    sleepQuietly(pacingRetryDelayMs);
                    if (connectionManager.isConnected()) {
                        log.info("Retrying subscription for {} after pacing violation", event.symbol());
                        resubscribeSymbol(event.symbol(), "pacing-retry");
                        failedSubscriptions.remove(event.symbol());
                    }
                });
            }
            case 101 -> {
                failedSubscriptions.put(event.symbol(), "MAX_TICKERS(101)");
                Subscription sub = activeSubscriptions.remove(event.symbol());
                if (sub != null) {
                    wrapper.unregisterBookRoute(sub.reqId());
                }
                log.error("Book subscription for {} dropped: market-data line budget exhausted "
                        + "(error 101) — will not retry until the next full resubscribe", event.symbol());
            }
            default -> { /* logged by the wrapper; watchdog owns liveness decisions */ }
        }
    }

    // -------------------------------------------------------------------------
    // Subscription setup
    // -------------------------------------------------------------------------

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
            activeSubscriptions.forEach((symbol, sub) -> {
                try {
                    connectionManager.getClient().cancelMktData(sub.reqId());
                } catch (Exception e) {
                    log.debug("cancelMktData({}) for {} failed: {}", sub.reqId(), symbol, e.getMessage());
                }
            });
            activeSubscriptions.clear();
            failedSubscriptions.clear();
            wrapper.clearBookRoutes();

            // 1 = realtime. Delayed/frozen switches (2–4) arrive via the marketDataType
            // callback per subscription and land in the Book's dataQuality state.
            connectionManager.getClient().reqMarketDataType(1);

            Set<String> symbols = bookSymbols();
            int firstReqId = -1, lastReqId = -1;
            for (String symbol : symbols) {
                int reqId = subscribe(symbol);
                if (firstReqId < 0) firstReqId = reqId;
                lastReqId = reqId;
                sleepQuietly(subscribeStaggerMs);
            }
            book.markConnected();
            log.info("BOOK_SUBSCRIBE_SUMMARY trigger={} subscriptions={} reqIds={}..{} symbols={} failures={}",
                    trigger, activeSubscriptions.size(), firstReqId, lastReqId, symbols, failedSubscriptions);
        }
    }

    /** Opens one streaming line and registers its Book route. Returns the reqId. */
    private int subscribe(String symbol) {
        int reqId = connectionManager.nextReqId();
        wrapper.registerBookRoute(reqId, symbol);
        connectionManager.getClient().reqMktData(
                reqId, usStockContract(symbol), genericTickList(), false, false, null);
        activeSubscriptions.put(symbol, new Subscription(reqId, Instant.now()));
        return reqId;
    }

    /** Cancel + resubscribe one symbol's stream with a fresh reqId. */
    private void resubscribeSymbol(String symbol, String reason) {
        synchronized (subscribeLock) {
            if (!connectionManager.isConnected()) return;
            Subscription old = activeSubscriptions.get(symbol);
            if (old != null) {
                try {
                    connectionManager.getClient().cancelMktData(old.reqId());
                } catch (Exception e) {
                    log.debug("cancelMktData({}) for {} failed: {}", old.reqId(), symbol, e.getMessage());
                }
                wrapper.unregisterBookRoute(old.reqId());
            }
            int reqId = subscribe(symbol);
            log.info("Book resubscribe symbol={} reason={} oldReqId={} newReqId={}",
                    symbol, reason, old != null ? old.reqId() : null, reqId);
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

    // -------------------------------------------------------------------------
    // Liveness watchdog
    // -------------------------------------------------------------------------

    @Scheduled(fixedDelayString = "${book.watchdog-interval-ms:15000}")
    public void livenessWatchdog() {
        if (!connectionManager.isConnected() || activeSubscriptions.isEmpty()) return;
        if (isInsideExpectedRestartWindow()) return; // nightly IBC restart: silence is routine
        if (marketStateService.getMarketState() != MarketStateService.MarketState.REGULAR) {
            return; // pre/post/closed silence is stillness, not failure; UNKNOWN: no statement
        }

        Instant now = Instant.now();
        TickerBook anchorBook = book.find(anchorSymbol());
        Long anchorAge = anchorBook != null ? anchorBook.volume().get().ageSeconds(now) : null;
        if (anchorAge == null || anchorAge > anchorFreshSeconds) {
            // The anchor itself is silent: the feed as a whole is in question, which is a
            // connection-level condition (reconnect watchdog / 1100-handling), not a
            // per-ticker one. Resubscribing individual tickers now would be guesswork.
            log.debug("Liveness watchdog: anchor {} not fresh (age={}s) — standing down",
                    anchorSymbol(), anchorAge);
            return;
        }

        for (Map.Entry<String, Subscription> entry : activeSubscriptions.entrySet()) {
            String symbol = entry.getKey();
            if (symbol.equals(anchorSymbol())) continue;
            TickerBook tb = book.find(symbol);
            Long volumeAge = tb != null ? tb.volume().get().ageSeconds(now) : null;
            // Never-ticked subscriptions are aged from their subscribe time — a line that
            // has been open for tickerStaleSeconds without a single volume tick during
            // REGULAR (while the anchor ticks) is just as dead as one that fell silent.
            long staleSeconds = volumeAge != null
                    ? volumeAge
                    : Math.max(0, now.getEpochSecond() - entry.getValue().subscribedAt().getEpochSecond());
            if (staleSeconds > tickerStaleSeconds) {
                log.warn("AUTO_RESUBSCRIBE ticker={} staleSeconds={} anchorAgeSeconds={}",
                        symbol, staleSeconds, anchorAge);
                resubscribeSymbol(symbol, "watchdog-stale");
            }
        }
    }

    boolean isInsideExpectedRestartWindow() {
        return isInsideWindow(expectedRestartWindow, LocalTime.now(NEW_YORK));
    }

    /** HH:mm-HH:mm window check, midnight-spanning supported (from > to). Unparseable or
     *  blank windows count as "not inside" — the watchdog then simply never stands down. */
    static boolean isInsideWindow(String window, LocalTime now) {
        if (window == null || window.isBlank()) return false;
        try {
            int dash = window.indexOf('-');
            LocalTime from = LocalTime.parse(window.substring(0, dash).trim(), HHMM);
            LocalTime to = LocalTime.parse(window.substring(dash + 1).trim(), HHMM);
            if (from.isAfter(to)) { // window spans midnight, e.g. 23:45-00:20
                return !now.isBefore(from) || !now.isAfter(to);
            }
            return !now.isBefore(from) && !now.isAfter(to);
        } catch (Exception e) {
            log.warn("restart window '{}' unparseable ({}) — ignoring window", window, e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Introspection / test hook
    // -------------------------------------------------------------------------

    Map<String, Subscription> activeSubscriptions() {
        return activeSubscriptions;
    }

    /**
     * TEST HOOK: kills one symbol's stream at the Gateway (cancelMktData) while leaving the
     * local subscription entry in place — from the Book's perspective the line simply goes
     * silent, which is exactly the dead-subscription scenario the watchdog must detect and
     * AUTO_RESUBSCRIBE. Exposed via the debug endpoint (disabled by default).
     */
    public boolean killSubscriptionForTest(String symbol) {
        Subscription sub = activeSubscriptions.get(symbol.toUpperCase());
        if (sub == null || !connectionManager.isConnected()) return false;
        connectionManager.getClient().cancelMktData(sub.reqId());
        log.warn("TEST HOOK: cancelled stream for {} (reqId={}) — expecting AUTO_RESUBSCRIBE "
                + "within ~{}s during REGULAR", symbol.toUpperCase(), sub.reqId(), tickerStaleSeconds);
        return true;
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
