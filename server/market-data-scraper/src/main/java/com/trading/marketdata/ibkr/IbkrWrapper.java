package com.trading.marketdata.ibkr;

import com.ib.client.*;
import com.trading.marketdata.book.MarketDataBook;
import com.trading.marketdata.model.NewsItem;
import com.trading.marketdata.book.TickerBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;

/**
 * Extends DefaultEWrapper instead of implementing EWrapper directly.
 *
 * DefaultEWrapper provides empty default implementations for all EWrapper methods.
 * This means we never have to maintain stubs for new API versions — only override
 * what we actually need. Any new methods added in future API versions are handled
 * automatically by DefaultEWrapper.
 */
@Component
public class IbkrWrapper extends DefaultEWrapper {

    private static final Logger log = LoggerFactory.getLogger(IbkrWrapper.class);

    private EClientSocket client;

    private final MarketDataBook book;
    private final ApplicationEventPublisher eventPublisher;

    public IbkrWrapper(MarketDataBook book, ApplicationEventPublisher eventPublisher) {
        this.book = book;
        this.eventPublisher = eventPublisher;
    }

    // Permanent Book subscriptions: reqId → symbol. Registered by the SubscriptionManager;
    // ticks for these reqIds are written straight into the MarketDataBook (last-value cache)
    // on this EReader thread — the Book's single tick-writer. Routes survive error(): a
    // transient error must not silently unroute a permanent stream (the liveness watchdog
    // owns resubscription decisions).
    private final Map<Integer, String> bookRoutes = new ConcurrentHashMap<>();

    private final Map<Integer, IbkrOptionsChainResult.Builder>             pendingChains       = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<IbkrOptionsChainResult>>  pendingChainFutures = new ConcurrentHashMap<>();
    private final Map<Integer, String>                                     chainRequestTicker  = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<Integer>>                 pendingContractDetails = new ConcurrentHashMap<>();
    private final Map<Integer, IbkrOptionContractActivity.Builder>         pendingContractActivity = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<IbkrOptionContractActivity>> pendingContractActivityFutures = new ConcurrentHashMap<>();
    // true = only complete once the open-interest tick (27/28) arrives; false = volume alone is enough
    // (used when open interest is already cached and we only need a fresh volume read)
    private final Map<Integer, Boolean>                                    contractActivityWaitsForOI = new ConcurrentHashMap<>();
    // "C" or "P" — which side this reqId's contract actually is, purely for the diagnostic
    // logging in tickSize() below (suspected mismatch between field 27/28 and actual contract side)
    private final Map<Integer, String>                                     contractActivityRight = new ConcurrentHashMap<>();

    public void setClient(EClientSocket client) { this.client = client; }

    // =========================================================================
    // IBKR news (generic tick 292 on the Book lines + reqNewsArticle body fetch)
    // =========================================================================

    /** reqId authority (IbkrConnectionManager::nextReqId) — article fetches need fresh ids
     *  and the connection manager owns the counter; injected as a supplier to avoid the
     *  wrapper↔connection-manager constructor cycle (same pattern as setClient). */
    private IntSupplier reqIdSupplier;

    public void setReqIdSupplier(IntSupplier reqIdSupplier) { this.reqIdSupplier = reqIdSupplier; }

    @Value("${news.article-fetch-enabled:true}")
    private boolean articleFetchEnabled;

    @Value("${news.max-items-per-ticker:25}")
    private int newsMaxItemsPerTicker;

    @Value("${news.article-cache-max:200}")
    private int articleCacheMax;

    /** providerCode → providerName of the account's subscribed news feeds, filled by the
     *  newsProviders callback after reqNewsProviders on connect. Diagnostic + honest
     *  answer to "why is there no IBKR news": empty map after connect = no feeds. */
    private volatile Map<String, String> newsProviderCatalog = Map.of();

    public Map<String, String> newsProviderCatalog() { return newsProviderCatalog; }

    private record PendingArticle(String symbol, String articleId) {}
    private final Map<Integer, PendingArticle> pendingNewsArticles = new ConcurrentHashMap<>();

    /** articleId → body. The same story ticks on several symbols (sector news) and again on
     *  reconnect; one fetch per article is enough. Bounded crudely: past the cap the whole
     *  cache is dropped — correctness never depends on a hit, a miss just refetches. */
    private final Map<String, String> articleTextCache = new ConcurrentHashMap<>();

    /** Leading IBKR headline metadata like "{K:n/a,C:0.6}Actual headline". */
    private static final Pattern HEADLINE_META = Pattern.compile("^\\{[^}]*\\}");

    @Override
    public void newsProviders(NewsProvider[] providers) {
        Map<String, String> catalog = new java.util.LinkedHashMap<>();
        if (providers != null) {
            for (NewsProvider np : providers) {
                catalog.put(np.providerCode(), np.providerName());
            }
        }
        newsProviderCatalog = Map.copyOf(catalog);
        if (catalog.isEmpty()) {
            log.warn("NEWS_PROVIDERS none — the account has no news feed subscriptions; "
                    + "generic tick 292 will stay silent on every Book line");
        } else {
            log.info("NEWS_PROVIDERS count={} {}", catalog.size(), catalog);
        }
    }

    /**
     * A headline on a Book line (generic tick 292). tickerId IS the reqMktData reqId of the
     * symbol's permanent subscription, so the existing bookRoutes map resolves the symbol.
     * The body is fetched asynchronously via reqNewsArticle; both this callback and the
     * newsArticle response run on the EReader thread, preserving the Book's single-writer
     * contract for the news field.
     */
    @Override
    public void tickNews(int tickerId, long timeStamp, String providerCode, String articleId,
                         String headline, String extraData) {
        String symbol = bookRoutes.get(tickerId);
        if (symbol == null) {
            log.debug("tickNews for unrouted reqId={} articleId={} — dropped", tickerId, articleId);
            return;
        }
        TickerBook tb = book.book(symbol);
        Instant now = Instant.now();

        String cleanHeadline = headline == null ? "" : stripHeadlineMeta(headline);
        // IBKR delivers epoch millis; guard against a seconds-scale value anyway (cheap,
        // and a wrong century in publishedAt would poison downstream recency logic).
        Instant published = timeStamp <= 0 ? now
                : Instant.ofEpochMilli(timeStamp < 100_000_000_000L ? timeStamp * 1000 : timeStamp);

        NewsItem item = new NewsItem(cleanHeadline, "ibkr:" + providerCode, null, published,
                providerCode, articleId, articleTextCache.get(articleId));
        tb.appendNews(item, newsMaxItemsPerTicker, now);
        log.info("IBKR_NEWS symbol={} provider={} articleId={} headline=\"{}\"",
                symbol, providerCode, articleId, cleanHeadline);

        if (item.fullText() == null && articleFetchEnabled
                && client != null && reqIdSupplier != null
                && providerCode != null && articleId != null) {
            int reqId = reqIdSupplier.getAsInt();
            pendingNewsArticles.put(reqId, new PendingArticle(symbol, articleId));
            client.reqNewsArticle(reqId, providerCode, articleId, null);
        }
    }

    /** Article body response. articleType: 0 = plain text, 1 = HTML (Briefing delivers
     *  HTML) — HTML is flattened to text with jsoup, which the project already ships for
     *  the scrapers. */
    @Override
    public void newsArticle(int requestId, int articleType, String articleText) {
        PendingArticle pending = pendingNewsArticles.remove(requestId);
        if (pending == null) return;
        String text = articleText == null ? "" : articleText;
        if (articleType == 1) {
            text = org.jsoup.Jsoup.parse(text).text();
        }
        if (articleTextCache.size() >= articleCacheMax) {
            articleTextCache.clear(); // crude bound, see field comment
        }
        articleTextCache.put(pending.articleId(), text);
        TickerBook tb = book.find(pending.symbol());
        boolean attached = tb != null
                && tb.attachArticleText(pending.articleId(), text, Instant.now());
        log.debug("IBKR_NEWS_ARTICLE symbol={} articleId={} chars={} attached={}",
                pending.symbol(), pending.articleId(), text.length(), attached);
    }

    static String stripHeadlineMeta(String headline) {
        Matcher m = HEADLINE_META.matcher(headline);
        return m.find() ? headline.substring(m.end()).trim() : headline.trim();
    }

    // =========================================================================
    // Book routing — permanent streaming subscriptions (SubscriptionManager)
    // =========================================================================

    public void registerBookRoute(int reqId, String symbol) {
        bookRoutes.put(reqId, symbol.toUpperCase());
    }

    public void unregisterBookRoute(int reqId) {
        bookRoutes.remove(reqId);
    }

    public void clearBookRoutes() {
        bookRoutes.clear();
    }

    /**
     * Routes one price tick of a permanent Book subscription. IBKR sends -1 (or 0) on a
     * side with no quote; such placeholders must be dropped, not stored. Field ids: live
     * 1/2/4/6/7/9/14, delayed 66/67/68/72/73/75/76 — same field, delayed line.
     *
     * Tick 35 = AUCTION_PRICE (generic tick 225): the indicative cross price. Every auction
     * tick is logged at INFO deliberately — the tick-35/34/36/61 semantics (especially the
     * sign convention of the imbalance, see tickSize) are wire-log-verified the same way the
     * 27/28 and 29/30 mappings were, and these logs ARE that verification.
     */
    private void routeBookPrice(TickerBook tb, int field, double price) {
        if (field == 35) {
            log.info("IBKR auction tick: symbol={} type=35 AUCTION_PRICE raw={}", tb.symbol(), price);
            if (price > 0) {
                tb.auctionPrice().update(price, Instant.now());
            }
            return;
        }
        if (price <= 0) return;
        Instant now = Instant.now();
        switch (field) {
            case 1, 66  -> tb.bid().update(price, now);
            case 2, 67  -> tb.ask().update(price, now);
            case 4, 68  -> tb.last().update(price, now);
            case 6, 72  -> tb.high().update(price, now);
            case 7, 73  -> tb.low().update(price, now);
            case 9, 75  -> tb.close().update(price, now);
            case 14, 76 -> tb.open().update(price, now);
            default -> {}
        }
    }

    /**
     * Routes one size tick of a permanent Book subscription.
     *
     * IBKR uses Integer.MAX_VALUE as an "undefined" sentinel in size ticks — such values
     * must be filtered, never stored as data.
     *
     * Option volume (generic tick 100 → 29 call / 30 put): there is NO put/call-ratio tick;
     * the PCR is computed from these two (TickerBook.putCallRatio).
     *
     * Auction (generic tick 225 → 34 AUCTION_VOLUME, 36 AUCTION_IMBALANCE,
     * 61 REGULATORY_IMBALANCE): values are stored EXACTLY as delivered (after the sentinel
     * filter). In particular the imbalance is NOT abs()'d and NOT sign-filtered: whether
     * IBKR encodes the imbalance side as a sign on tick 36 (native Nasdaq NOII carries
     * quantity and side in two separate fields) is unverified until observed live — the
     * INFO logs are the verification instrument. Until then, treat the sign in downstream
     * analysis as provisional.
     */
    private void routeBookSize(TickerBook tb, int field, long v) {
        Instant now = Instant.now();
        switch (field) {
            case 8, 74 -> { // 74 = delayed volume
                if (v >= 0 && v < Integer.MAX_VALUE) tb.volume().update(v, now);
            }
            case 29 -> {
                if (v >= 0 && v < Integer.MAX_VALUE) tb.callOptionVolume().update(v, now);
            }
            case 30 -> {
                if (v >= 0 && v < Integer.MAX_VALUE) tb.putOptionVolume().update(v, now);
            }
            case 34, 36, 61 -> {
                String meaning = switch (field) {
                    case 34 -> "AUCTION_VOLUME";
                    case 36 -> "AUCTION_IMBALANCE";
                    default -> "REGULATORY_IMBALANCE";
                };
                log.info("IBKR auction tick: symbol={} type={} {} raw={}", tb.symbol(), field, meaning, v);
                if (Math.abs(v) < Integer.MAX_VALUE) {
                    switch (field) {
                        case 34 -> tb.auctionVolume().update(v, now);
                        case 36 -> tb.imbalance().update(v, now);
                        default -> tb.regulatoryImbalance().update(v, now);
                    }
                }
            }
            default -> {}
        }
    }

    /**
     * Delayed/frozen switch (1 = realtime, 2 = frozen, 3 = delayed, 4 = delayed-frozen).
     * A switch to 2–4 is a statement about the LINE's quality, not a data tick: it lands in
     * the Book's dataQuality state and deliberately does NOT touch any field's lastSeenAt —
     * a frozen line is exactly the silence the timestamp pair exists to expose.
     */
    @Override
    public void marketDataType(int reqId, int marketDataType) {
        String symbol = bookRoutes.get(reqId);
        if (symbol != null) {
            book.book(symbol).setMarketDataType(marketDataType, Instant.now());
            if (marketDataType != 1) {
                log.warn("IBKR market data type for {} switched to {} (2=frozen, 3=delayed, 4=delayed-frozen)",
                        symbol, marketDataType);
            }
        }
    }

    // =========================================================================
    // Tick callbacks
    // =========================================================================

    @Override
    public void tickPrice(int reqId, int field, double price, TickAttrib attrib) {
        String bookSymbol = bookRoutes.get(reqId);
        if (bookSymbol != null) {
            routeBookPrice(book.book(bookSymbol), field, price);
            return;
        }

        // Option contract activity requests receive the same default price ticks (bid/ask/
        // last) on their existing subscription — capture them at zero additional request
        // cost. IBKR sends -1 (or 0) on a side with no quote; such placeholders must be
        // dropped, not stored. Deliberately NO completion here: completion stays volume/OI-
        // driven (see tickSize). On a live line the price ticks arrive first and are in the
        // builder by completion time; when they aren't, the fields stay null and the caller
        // falls back (last -> mid -> none) instead of getting fabricated prices.
        IbkrOptionContractActivity.Builder actBuilder = pendingContractActivity.get(reqId);
        if (actBuilder != null && price > 0) {
            switch (field) {
                case 1, 66 -> actBuilder.bid(price);   // 66 = delayed bid
                case 2, 67 -> actBuilder.ask(price);   // 67 = delayed ask
                case 4, 68 -> actBuilder.last(price);  // 68 = delayed last
                default -> {}
            }
        }

    }

    @Override
    public void tickString(int reqId, int tickType, String value) {
        // Option contract activity: field 45 = LAST_TIMESTAMP (delayed: 88) — unix epoch
        // seconds of the last trade, a default tick on the same subscription. This is the
        // staleness measurement for aggressor classification: without it, a last print from
        // hours ago is indistinguishable from one that just hit the current quote. Same
        // no-completion rule as tickPrice: this tick only enriches the builder.
        if (tickType == 45 || tickType == 88) {
            IbkrOptionContractActivity.Builder actBuilder = pendingContractActivity.get(reqId);
            if (actBuilder != null && value != null && !value.isBlank()) {
                try {
                    long epoch = Long.parseLong(value.trim());
                    if (epoch > 0) actBuilder.lastTimestamp(epoch);
                } catch (NumberFormatException e) {
                    log.debug("IBKR tickString reqId={} field={} unparseable last-timestamp '{}'", reqId, tickType, value);
                }
            }
        }
    }

    @Override
    public void tickSize(int reqId, int field, Decimal size) {
        String bookSymbol = bookRoutes.get(reqId);
        if (bookSymbol != null) {
            routeBookSize(book.book(bookSymbol), field, size.longValue());
            return;
        }

        if (field == 8 || field == 74) { // 74 = delayed volume
            IbkrOptionContractActivity.Builder actBuilder = pendingContractActivity.get(reqId);
            if (actBuilder != null) {
                actBuilder.volume(size.longValue());
                if (Boolean.FALSE.equals(contractActivityWaitsForOI.get(reqId))) {
                    completeContractActivity(reqId);
                }
            }
        } else if (field == 27 || field == 28) { // 27 = call open interest, 28 = put open interest
            IbkrOptionContractActivity.Builder actBuilder = pendingContractActivity.get(reqId);
            String expectedRight = contractActivityRight.get(reqId);
            String fieldMeaning = field == 27 ? "CALL_OI" : "PUT_OI";
            // Confirmed via diagnostic logging (see prior investigation): IBKR sends BOTH field
            // 27 and field 28 for every option contract request, regardless of that contract's
            // actual right — field 27 arrives first, consistently, even for put contracts, with
            // a value of 0 (irrelevant/not-applicable placeholder). The field that actually
            // matches the contract's right is field 27 for calls, field 28 for puts; the other
            // is not applicable to this specific contract and must be ignored, not just "first
            // one wins" — otherwise puts always complete on the spurious field-27 zero before
            // the real field-28 value arrives.
            boolean fieldMatchesContract = ("C".equals(expectedRight) && field == 27)
                    || ("P".equals(expectedRight) && field == 28);
            if (actBuilder != null && fieldMatchesContract) {
                actBuilder.openInterest(size.longValue());
                completeContractActivity(reqId);
            } else if (actBuilder != null) {
                log.debug("IBKR OI tick: reqId={} field={} ({}) value={} contractRight={} — ignored, doesn't match contract's right",
                        reqId, field, fieldMeaning, size.longValue(), expectedRight);
            }
        }
    }

    private void completeContractActivity(int reqId) {
        IbkrOptionContractActivity.Builder b = pendingContractActivity.remove(reqId);
        CompletableFuture<IbkrOptionContractActivity> f = pendingContractActivityFutures.remove(reqId);
        contractActivityWaitsForOI.remove(reqId);
        // contractActivityRight is deliberately NOT cleared here — kept around briefly so a
        // late-arriving tick (received after this future already completed) still logs with
        // the correct expected-right context instead of "contractRight=null". Cleaned up in
        // discardContractActivityRequest() once the caller is done with this reqId entirely.
        if (b != null && f != null && !f.isDone()) {
            f.complete(b.build());
        }
    }

    // =========================================================================
    // Options chain callbacks
    // =========================================================================

    @Override
    public void securityDefinitionOptionalParameter(int reqId, String exchange,
            int underlyingConId, String tradingClass, String multiplier,
            Set<String> expirations, Set<Double> strikes) {
        String ticker = chainRequestTicker.get(reqId);
        // Filter on tradingClass matching the ticker itself, NOT on exchange=="SMART".
        // IBKR can (and does) return adjusted/legacy trading classes — e.g. after a corporate
        // action, a symbol like "MU" can carry a second, thin class like "2MU" with only a
        // handful of legacy strikes — and that adjusted class is not guaranteed to be absent
        // from the SMART-routed callback. Accepting only exchange=="SMART" silently picked up
        // "2MU" once (5 strikes total) instead of the real ~500-strike "MU" chain. Multiple
        // exchanges legitimately report the correct tradingClass, so we union all of them
        // (see IbkrOptionsChainResult.Builder) rather than picking just one exchange.
        if (ticker == null || !ticker.equalsIgnoreCase(tradingClass)) return;
        pendingChains.computeIfAbsent(reqId, k -> new IbkrOptionsChainResult.Builder())
                .expirations(expirations).strikes(strikes).multiplier(multiplier);
    }

    @Override
    public void securityDefinitionOptionalParameterEnd(int reqId) {
        IbkrOptionsChainResult.Builder b = pendingChains.remove(reqId);
        CompletableFuture<IbkrOptionsChainResult> f = pendingChainFutures.remove(reqId);
        chainRequestTicker.remove(reqId);
        if (f != null && !f.isDone()) {
            f.complete(b != null ? b.build() : new IbkrOptionsChainResult.Builder().build());
        }
    }

    // =========================================================================
    // Contract details callbacks — used solely to resolve a symbol's conId,
    // which reqSecDefOptParams requires (passing conId=0 is not reliable across
    // symbols per IBKR's own sample code, which always resolves it first).
    // =========================================================================

    @Override
    public void contractDetails(int reqId, ContractDetails contractDetails) {
        CompletableFuture<Integer> f = pendingContractDetails.get(reqId);
        if (f != null && !f.isDone()) {
            f.complete(contractDetails.contract().conid());
        }
    }

    @Override
    public void contractDetailsEnd(int reqId) {
        // If contractDetails() never fired above (no match for the symbol), fail explicitly
        // instead of leaving the caller to time out with no explanation.
        CompletableFuture<Integer> f = pendingContractDetails.remove(reqId);
        if (f != null && !f.isDone()) {
            f.completeExceptionally(new IbkrException(0, "No contract details returned"));
        }
    }

    // =========================================================================
    // Registration helpers
    // =========================================================================

    public CompletableFuture<IbkrOptionsChainResult> registerChainRequest(int reqId, String ticker) {
        CompletableFuture<IbkrOptionsChainResult> f = new CompletableFuture<>();
        pendingChainFutures.put(reqId, f);
        chainRequestTicker.put(reqId, ticker);
        return f;
    }

    public void discardChainRequest(int reqId) {
        pendingChainFutures.remove(reqId);
        pendingChains.remove(reqId);
        chainRequestTicker.remove(reqId);
    }

    public CompletableFuture<Integer> registerContractDetailsRequest(int reqId) {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        pendingContractDetails.put(reqId, f);
        return f;
    }

    public void discardContractDetailsRequest(int reqId) {
        pendingContractDetails.remove(reqId);
    }

    public CompletableFuture<IbkrOptionContractActivity> registerContractActivityRequest(int reqId, boolean waitForOpenInterest, String right) {
        CompletableFuture<IbkrOptionContractActivity> f = new CompletableFuture<>();
        pendingContractActivityFutures.put(reqId, f);
        pendingContractActivity.put(reqId, IbkrOptionContractActivity.builder());
        contractActivityWaitsForOI.put(reqId, waitForOpenInterest);
        contractActivityRight.put(reqId, right);
        return f;
    }

    public void discardContractActivityRequest(int reqId) {
        pendingContractActivityFutures.remove(reqId);
        pendingContractActivity.remove(reqId);
        contractActivityWaitsForOI.remove(reqId);
        contractActivityRight.remove(reqId);
    }

    /** Call after a successful fetch too — contractActivityRight isn't cleared on normal
     *  completion (kept around briefly for late-tick diagnostic logging), so it needs an
     *  explicit cleanup call on the success path as well to avoid leaking one entry per reqId. */
    public void clearContractActivityRight(int reqId) {
        contractActivityRight.remove(reqId);
    }

    // =========================================================================
    // Options metrics callbacks
    // Generic-tick REQUEST numbers and delivered TICK TYPE ids differ (classic TWS API trap):
    //   requested 104 (HV)            → arrives as tickGeneric type 23
    //   requested 106 (IV)            → arrives as tickGeneric type 24
    //   requested 100 (option volume) → arrives as tickSize types 29 (call) / 30 (put)
    // There is NO put/call-ratio tick — it is computed from 29/30 (TickerBook.putCallRatio).
    // =========================================================================

    @Override
    public void tickGeneric(int reqId, int tickType, double value) {
        String bookSymbol = bookRoutes.get(reqId);
        if (bookSymbol == null) return;
        TickerBook tb = book.book(bookSymbol);
        Instant now = Instant.now();
        switch (tickType) {
            case 24 -> tb.impliedVolatility().update(value, now);   // OPTION_IMPLIED_VOL (requested as 106)
            case 23 -> tb.historicalVolatility().update(value, now); // OPTION_HISTORICAL_VOL (requested as 104)
            default  -> {}
        }
    }

    // =========================================================================
    // Error handling — 10.47 signature: (int id, long errorTime, int code, String msg, String json)
    // =========================================================================

    @Override
    public void error(int id, long errorTime, int errorCode, String errorMsg, String advancedOrderRejectJson) {
        if (errorCode == 2104 || errorCode == 2106 || errorCode == 2107 ||
            errorCode == 2108 || errorCode == 2119 || errorCode == 2158) {
            log.info("IBKR info [reqId={}, code={}]: {}", id, errorCode, errorMsg);
            return;
        }
        if (errorCode == 10167) {
            // "Requested market data is not subscribed. Delayed market data is available."
            // This is informational — IBKR still delivers delayed data via tickPrice callbacks.
            // Do NOT fail the pending future; let it resolve normally from the delayed ticks.
            log.info("IBKR delayed data notice [reqId={}, code={}]: {}", id, errorCode, errorMsg);
            return;
        }
        // Gateway↔IB-server connectivity transitions (id = -1). The SOCKET to the Gateway is
        // still up in all three cases — do not confuse these with connectionClosed:
        //   1100 / 2110: upstream feed lost → ticks stop; invalidate the Book (values kept,
        //                flagged) so silence is not mistaken for stillness.
        //   1101: connectivity restored, DATA LOST → subscriptions are gone server-side;
        //         a full resubscribe with fresh reqIds is required.
        //   1102: connectivity restored, DATA MAINTAINED → subscriptions survived; just
        //         clear the connection-lost flag, ticks resume on their own.
        if (errorCode == 1100 || errorCode == 2110) {
            log.warn("IBKR upstream connectivity lost [code={}]: {} — invalidating Book", errorCode, errorMsg);
            book.invalidateAll("error " + errorCode + ": " + errorMsg);
            return;
        }
        if (errorCode == 1101) {
            log.warn("IBKR connectivity restored, data LOST [code=1101]: {} — full resubscribe", errorMsg);
            eventPublisher.publishEvent(new IbkrConnectedEvent());
            return;
        }
        if (errorCode == 1102) {
            log.info("IBKR connectivity restored, data maintained [code=1102]: {}", errorMsg);
            book.markConnected();
            return;
        }
        // Errors on a permanent Book subscription: log with symbol context and keep the
        // route — a transient error must not silently kill a permanent stream. Error 100
        // (pacing violation) and 101 (market-data line budget exhausted) are deliberately
        // kept apart: they look similar ("too much") but need opposite reactions — 100 is
        // retryable after backoff, 101 is not retryable until lines are freed. The
        // SubscriptionManager reacts via IbkrSubscriptionErrorEvent and reports both as
        // distinct failure reasons in the BOOK_SUBSCRIBE_SUMMARY.
        // Failed article fetch (bad articleId, feed hiccup): drop the pending entry so the
        // map cannot grow; the headline stays in the Book without a body, which the
        // fullText=null contract already expresses. Never worth a retry loop.
        PendingArticle failedArticle = pendingNewsArticles.remove(id);
        if (failedArticle != null) {
            log.warn("IBKR news article fetch failed symbol={} articleId={} code={}: {}",
                    failedArticle.symbol(), failedArticle.articleId(), errorCode, errorMsg);
            return;
        }

        String bookSymbol = bookRoutes.get(id);
        if (bookSymbol != null) {
            if (errorCode == 10090) {
                handlePartialSubscription(bookSymbol, id, errorMsg);
                return; // informational; nothing for the SubscriptionManager to react to
            }
            switch (errorCode) {
                case 100 -> log.error("IBKR_PACING_VIOLATION symbol={} reqId={}: {}", bookSymbol, id, errorMsg);
                case 101 -> log.error("IBKR_MAX_TICKERS symbol={} reqId={}: {}", bookSymbol, id, errorMsg);
                default  -> log.warn("IBKR error on Book subscription symbol={} reqId={} code={}: {}",
                        bookSymbol, id, errorCode, errorMsg);
            }
            eventPublisher.publishEvent(new IbkrSubscriptionErrorEvent(id, bookSymbol, errorCode, errorMsg));
            return;
        }

        log.warn("IBKR error id={} code={}: {}", id, errorCode, errorMsg);
        CompletableFuture<IbkrOptionsChainResult> cf = pendingChainFutures.remove(id);
        if (cf != null) cf.completeExceptionally(new IbkrException(errorCode, errorMsg));
        CompletableFuture<Integer> cdf = pendingContractDetails.remove(id);
        if (cdf != null) cdf.completeExceptionally(new IbkrException(errorCode, errorMsg));
        CompletableFuture<IbkrOptionContractActivity> af = pendingContractActivityFutures.remove(id);
        if (af != null) {
            pendingContractActivity.remove(id);
            contractActivityWaitsForOI.remove(id);
            af.completeExceptionally(new IbkrException(errorCode, errorMsg));
        }
    }

    /**
     * Error 10090 — "You have not subscribed to part of the requested market data. Ticks
     * requiring no subscription are still delivered." The message TEXT names the affected
     * tick group(s) in the locale-independent tail format {@code <SYMBOL> <EXCHANGE>/<Group>:<genericTick>}
     * (observed live 2026-07-10: "...werden weiterhin angezeigt.SPY ARCA/Auction:225").
     * Parsed structurally: each named generic tick marks its Book field group notSubscribed,
     * so the fields' permanent silence is attributed to the account's market-data
     * subscription instead of looking like a calm market or a dead feed (visible in the
     * snapshot's dataQuality block). Default ticks of the same line keep streaming.
     */
    private void handlePartialSubscription(String bookSymbol, int reqId, String errorMsg) {
        java.util.List<Integer> ticks = parseNotSubscribedGenericTicks(errorMsg);
        java.util.List<Integer> mapped = new java.util.ArrayList<>();
        for (int tick : ticks) {
            if (book.book(bookSymbol).markNotSubscribedGenericTick(tick)) {
                mapped.add(tick);
            } else {
                log.warn("IBKR_PARTIAL_SUBSCRIPTION symbol={} reqId={}: generic tick {} not mapped to any Book field group",
                        bookSymbol, reqId, tick);
            }
        }
        if (mapped.isEmpty()) {
            log.warn("IBKR_PARTIAL_SUBSCRIPTION symbol={} reqId={}: no tick group parseable from '{}'",
                    bookSymbol, reqId, errorMsg);
        } else {
            log.warn("IBKR_PARTIAL_SUBSCRIPTION symbol={} reqId={} genericTicks={} — fields marked notSubscribed",
                    bookSymbol, reqId, mapped);
        }
    }

    // Tail format of the 10090 text: "<EXCHANGE>/<GroupName>:<number>", e.g. "ARCA/Auction:225".
    // Only the number matters (it is the REQUESTED generic tick id); the group name is
    // locale-independent but redundant with the number.
    private static final java.util.regex.Pattern NOT_SUBSCRIBED_TICK =
            java.util.regex.Pattern.compile("/[^:/\\s][^:/]*:(\\d{1,4})");

    static java.util.List<Integer> parseNotSubscribedGenericTicks(String errorMsg) {
        if (errorMsg == null) return java.util.List.of();
        java.util.List<Integer> ticks = new java.util.ArrayList<>();
        java.util.regex.Matcher m = NOT_SUBSCRIBED_TICK.matcher(errorMsg);
        while (m.find()) {
            ticks.add(Integer.parseInt(m.group(1)));
        }
        return ticks;
    }

    @Override public void error(String str) { log.warn("IBKR: {}", str); }
    @Override public void error(Exception e) { log.error("IBKR exception: {}", e.getMessage(), e); }

    @Override
    public void connectionClosed() {
        // Routine event, not an error: the Gateway restarts nightly via IBC. The Book keeps
        // its last known values (flagged invalidated by the disconnect listener); the
        // SubscriptionManager resubscribes with fresh reqIds on reconnect.
        log.warn("IBKR connection closed.");
        eventPublisher.publishEvent(new IbkrDisconnectedEvent("connectionClosed callback"));
        pendingChainFutures.forEach((id, f) -> f.completeExceptionally(new IbkrException(0, "Connection closed")));
        pendingChainFutures.clear();
        pendingChains.clear();
        chainRequestTicker.clear();
        pendingContractDetails.forEach((id, f) -> f.completeExceptionally(new IbkrException(0, "Connection closed")));
        pendingContractDetails.clear();
        pendingContractActivityFutures.forEach((id, f) -> f.completeExceptionally(new IbkrException(0, "Connection closed")));
        pendingContractActivityFutures.clear();
        pendingContractActivity.clear();
        contractActivityWaitsForOI.clear();
    }

    @Override public void nextValidId(int o) { log.info("IBKR next valid order ID: {}", o); }
    @Override public void connectAck() { log.info("IBKR connectAck received."); }
}
