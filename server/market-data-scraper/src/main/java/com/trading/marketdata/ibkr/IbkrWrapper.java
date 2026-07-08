package com.trading.marketdata.ibkr;

import com.ib.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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

    private final Map<Integer, CompletableFuture<IbkrQuoteResult>>        pendingQuotes       = new ConcurrentHashMap<>();
    private final Map<Integer, IbkrOptionsChainResult.Builder>             pendingChains       = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<IbkrOptionsChainResult>>  pendingChainFutures = new ConcurrentHashMap<>();
    private final Map<Integer, String>                                     chainRequestTicker  = new ConcurrentHashMap<>();
    private final Map<Integer, IbkrOptionsResult.Builder>                  pendingOptionsMetrics = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<IbkrOptionsResult>>       pendingOptionsMetricsFutures = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<Integer>>                 pendingContractDetails = new ConcurrentHashMap<>();
    private final Map<Integer, IbkrOptionContractActivity.Builder>         pendingContractActivity = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<IbkrOptionContractActivity>> pendingContractActivityFutures = new ConcurrentHashMap<>();
    // true = only complete once the open-interest tick (27/28) arrives; false = volume alone is enough
    // (used when open interest is already cached and we only need a fresh volume read)
    private final Map<Integer, Boolean>                                    contractActivityWaitsForOI = new ConcurrentHashMap<>();
    // "C" or "P" — which side this reqId's contract actually is, purely for the diagnostic
    // logging in tickSize() below (suspected mismatch between field 27/28 and actual contract side)
    private final Map<Integer, String>                                     contractActivityRight = new ConcurrentHashMap<>();
    // Auction/NOII (generic tick 225): builder is harvested by the caller after a fixed
    // collection window — there is no completing tick (see IbkrAuctionResult). The future
    // exists ONLY to propagate hard errors (error() / connectionClosed()); it is never
    // completed normally, so the caller's timeout on it is the expected path.
    private final Map<Integer, IbkrAuctionResult.Builder>                  pendingAuction = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<Void>>                    pendingAuctionFutures = new ConcurrentHashMap<>();

    public void setClient(EClientSocket client) { this.client = client; }

    // =========================================================================
    // Quote callbacks
    // =========================================================================

    @Override
    public void tickPrice(int reqId, int field, double price, TickAttrib attrib) {
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

        // Auction (NOII) subscription: tick 35 = AUCTION_PRICE — the indicative cross price.
        // Every auction tick is logged at INFO deliberately: the tick-35/34/36/61 semantics
        // (especially the sign convention of the imbalance, see tickSize) are wire-log-verified
        // the same way the 27/28 and 29/30 mappings were, and these logs ARE that verification.
        IbkrAuctionResult.Builder auctionBuilder = pendingAuction.get(reqId);
        if (auctionBuilder != null && field == 35) {
            if (price > 0) {
                auctionBuilder.auctionPrice(price);
            }
            log.info("IBKR auction tick: reqId={} type=35 AUCTION_PRICE raw={}", reqId, price);
            return;
        }

        CompletableFuture<IbkrQuoteResult> future = pendingQuotes.get(reqId);
        if (future == null) return;
        IbkrQuoteResult.Builder b = IbkrQuoteResult.builderFor(reqId);
        switch (field) {
            case 1, 66  -> b.bid(price);   // 66 = delayed bid
            case 2, 67  -> b.ask(price);   // 67 = delayed ask
            case 4, 68  -> b.last(price);  // 68 = delayed last
            case 6, 72  -> b.high(price);  // 72 = delayed high
            case 7, 73  -> b.low(price);   // 73 = delayed low
            case 9, 75  -> b.close(price); // 75 = delayed close
            case 14, 76 -> b.open(price);  // 76 = delayed open
            default -> {}
        }
        // Do NOT complete here — wait for tickSnapshotEnd to get all fields
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
        // Auction (NOII) subscription, generic tick 225 → delivered as:
        //   34 = AUCTION_VOLUME (paired shares), 36 = AUCTION_IMBALANCE, 61 = REGULATORY_IMBALANCE.
        // Values are stored EXACTLY as delivered (after the usual MAX_VALUE sentinel filter).
        // In particular the imbalance is NOT abs()'d and NOT sign-filtered: whether IBKR encodes
        // the imbalance side as a sign on tick 36 (native Nasdaq NOII carries quantity and side
        // in two separate fields) is unverified until observed live — the INFO logs below are
        // the verification instrument. Until then, treat the sign in downstream analysis as
        // provisional.
        IbkrAuctionResult.Builder auctionBuilder = pendingAuction.get(reqId);
        if (auctionBuilder != null && (field == 34 || field == 36 || field == 61)) {
            long v = size.longValue();
            String meaning = switch (field) {
                case 34 -> "AUCTION_VOLUME";
                case 36 -> "AUCTION_IMBALANCE";
                default -> "REGULATORY_IMBALANCE";
            };
            log.info("IBKR auction tick: reqId={} type={} {} raw={}", reqId, field, meaning, v);
            if (Math.abs(v) < Integer.MAX_VALUE) {
                switch (field) {
                    case 34 -> auctionBuilder.auctionVolume(v);
                    case 36 -> auctionBuilder.imbalance(v);
                    default -> auctionBuilder.regulatoryImbalance(v);
                }
            }
            return;
        }

        // Option volume ticks for the METRICS request on the underlying (generic tick 100):
        // 29 = call option volume, 30 = put option volume. IBKR uses Integer.MAX_VALUE as
        // an "undefined" sentinel — such values must be ignored, not treated as volume.
        if (field == 29 || field == 30) {
            IbkrOptionsResult.Builder metricsBuilder = pendingOptionsMetrics.get(reqId);
            if (metricsBuilder != null) {
                long v = size.longValue();
                if (v >= 0 && v < Integer.MAX_VALUE) {
                    if (field == 29) metricsBuilder.callVolume(v); else metricsBuilder.putVolume(v);
                    if (metricsBuilder.hasBothVolumes()) {
                        CompletableFuture<IbkrOptionsResult> f = pendingOptionsMetricsFutures.remove(reqId);
                        pendingOptionsMetrics.remove(reqId);
                        if (f != null && !f.isDone()) f.complete(metricsBuilder.build());
                    }
                }
            }
            return;
        }

        if (field == 8 || field == 74) { // 74 = delayed volume
            if (pendingQuotes.containsKey(reqId)) {
                IbkrQuoteResult.builderFor(reqId).volume(size.longValue());
            }

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

    @Override
    public void tickSnapshotEnd(int reqId) {
        // Complete quote future if pending
        CompletableFuture<IbkrQuoteResult> quoteFuture = pendingQuotes.remove(reqId);
        if (quoteFuture != null && !quoteFuture.isDone()) {
            quoteFuture.complete(IbkrQuoteResult.builderFor(reqId).build(reqId));
        }
        // Complete options metrics future if pending
        IbkrOptionsResult.Builder optBuilder = pendingOptionsMetrics.remove(reqId);
        CompletableFuture<IbkrOptionsResult> optFuture = pendingOptionsMetricsFutures.remove(reqId);
        if (optBuilder != null && optFuture != null && !optFuture.isDone()) {
            optFuture.complete(optBuilder.build());
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

    public CompletableFuture<IbkrQuoteResult> registerQuoteRequest(int reqId) {
        CompletableFuture<IbkrQuoteResult> f = new CompletableFuture<>();
        pendingQuotes.put(reqId, f);
        return f;
    }

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

    public CompletableFuture<IbkrOptionsResult> registerOptionsMetricsRequest(int reqId) {
        CompletableFuture<IbkrOptionsResult> f = new CompletableFuture<>();
        pendingOptionsMetricsFutures.put(reqId, f);
        pendingOptionsMetrics.put(reqId, IbkrOptionsResult.builder());
        return f;
    }

    /**
     * Drops pending state for a reqId the caller has given up on (e.g. after a client-side
     * timeout). Without this, a reqId whose tickSnapshotEnd never arrives leaks forever in
     * pendingQuotes/IbkrQuoteResult.builders — real risk on a long-running service pointed at
     * a data source with a non-trivial timeout rate.
     */
    public void discardQuoteRequest(int reqId) {
        pendingQuotes.remove(reqId);
        IbkrQuoteResult.discard(reqId);
    }

    /** Same cleanup as {@link #discardQuoteRequest} but for the options-metrics generic-tick maps. */
    public void discardOptionsMetricsRequest(int reqId) {
        pendingOptionsMetricsFutures.remove(reqId);
        pendingOptionsMetrics.remove(reqId);
    }

    /**
     * Registers an auction (NOII) collection request. The returned future NEVER completes
     * normally — it exists solely so error() / connectionClosed() can abort the caller's
     * collection window early with a real exception. The expected flow is: caller blocks on
     * the future for the collection window, gets a TimeoutException (normal!), then calls
     * {@link #harvestAuctionRequest(int)} to collect whatever ticks arrived.
     */
    public CompletableFuture<Void> registerAuctionRequest(int reqId) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        pendingAuctionFutures.put(reqId, f);
        pendingAuction.put(reqId, IbkrAuctionResult.builder());
        return f;
    }

    /** Removes and builds the auction builder for this reqId. Null if already discarded. */
    public IbkrAuctionResult harvestAuctionRequest(int reqId) {
        pendingAuctionFutures.remove(reqId);
        IbkrAuctionResult.Builder b = pendingAuction.remove(reqId);
        return b != null ? b.build() : null;
    }

    public void discardAuctionRequest(int reqId) {
        pendingAuctionFutures.remove(reqId);
        pendingAuction.remove(reqId);
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
    // There is NO put/call-ratio tick — it is computed from 29/30 in the Builder.
    // Completion happens in tickSize() once both volume ticks are in.
    // =========================================================================

    @Override
    public void tickGeneric(int reqId, int tickType, double value) {
        IbkrOptionsResult.Builder b = pendingOptionsMetrics.get(reqId);
        if (b == null) return;
        switch (tickType) {
            case 24 -> b.impliedVolatility(value);   // OPTION_IMPLIED_VOL (requested as 106)
            case 23 -> b.historicalVolatility(value); // OPTION_HISTORICAL_VOL (requested as 104)
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
        log.warn("IBKR error id={} code={}: {}", id, errorCode, errorMsg);
        CompletableFuture<IbkrQuoteResult> qf = pendingQuotes.remove(id);
        if (qf != null) qf.completeExceptionally(new IbkrException(errorCode, errorMsg));
        CompletableFuture<IbkrOptionsChainResult> cf = pendingChainFutures.remove(id);
        if (cf != null) cf.completeExceptionally(new IbkrException(errorCode, errorMsg));
        CompletableFuture<IbkrOptionsResult> of = pendingOptionsMetricsFutures.remove(id);
        if (of != null) {
            pendingOptionsMetrics.remove(id);
            of.completeExceptionally(new IbkrException(errorCode, errorMsg));
        }
        CompletableFuture<Integer> cdf = pendingContractDetails.remove(id);
        if (cdf != null) cdf.completeExceptionally(new IbkrException(errorCode, errorMsg));
        CompletableFuture<IbkrOptionContractActivity> af = pendingContractActivityFutures.remove(id);
        if (af != null) {
            pendingContractActivity.remove(id);
            contractActivityWaitsForOI.remove(id);
            af.completeExceptionally(new IbkrException(errorCode, errorMsg));
        }
        CompletableFuture<Void> auf = pendingAuctionFutures.remove(id);
        if (auf != null) {
            pendingAuction.remove(id);
            auf.completeExceptionally(new IbkrException(errorCode, errorMsg));
        }
    }

    @Override public void error(String str) { log.warn("IBKR: {}", str); }
    @Override public void error(Exception e) { log.error("IBKR exception: {}", e.getMessage(), e); }

    @Override
    public void connectionClosed() {
        log.warn("IBKR connection closed.");
        pendingQuotes.forEach((id, f) -> f.completeExceptionally(new IbkrException(0, "Connection closed")));
        pendingQuotes.clear();
        pendingChainFutures.forEach((id, f) -> f.completeExceptionally(new IbkrException(0, "Connection closed")));
        pendingChainFutures.clear();
        pendingChains.clear();
        chainRequestTicker.clear();
        pendingOptionsMetricsFutures.forEach((id, f) -> f.completeExceptionally(new IbkrException(0, "Connection closed")));
        pendingOptionsMetricsFutures.clear();
        pendingOptionsMetrics.clear();
        pendingContractDetails.forEach((id, f) -> f.completeExceptionally(new IbkrException(0, "Connection closed")));
        pendingContractDetails.clear();
        pendingContractActivityFutures.forEach((id, f) -> f.completeExceptionally(new IbkrException(0, "Connection closed")));
        pendingContractActivityFutures.clear();
        pendingContractActivity.clear();
        contractActivityWaitsForOI.clear();
        pendingAuctionFutures.forEach((id, f) -> f.completeExceptionally(new IbkrException(0, "Connection closed")));
        pendingAuctionFutures.clear();
        pendingAuction.clear();
    }

    @Override public void nextValidId(int o) { log.info("IBKR next valid order ID: {}", o); }
    @Override public void connectAck() { log.info("IBKR connectAck received."); }
}
