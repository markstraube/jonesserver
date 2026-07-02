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

    public void setClient(EClientSocket client) { this.client = client; }

    // =========================================================================
    // Quote callbacks
    // =========================================================================

    @Override
    public void tickPrice(int reqId, int field, double price, TickAttrib attrib) {
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
    public void tickSize(int reqId, int field, Decimal size) {
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
            if (actBuilder != null) {
                actBuilder.openInterest(size.longValue());
                completeContractActivity(reqId);
            }
        }
    }

    private void completeContractActivity(int reqId) {
        IbkrOptionContractActivity.Builder b = pendingContractActivity.remove(reqId);
        CompletableFuture<IbkrOptionContractActivity> f = pendingContractActivityFutures.remove(reqId);
        contractActivityWaitsForOI.remove(reqId);
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

    public CompletableFuture<Integer> registerContractDetailsRequest(int reqId) {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        pendingContractDetails.put(reqId, f);
        return f;
    }

    public void discardContractDetailsRequest(int reqId) {
        pendingContractDetails.remove(reqId);
    }

    public CompletableFuture<IbkrOptionContractActivity> registerContractActivityRequest(int reqId, boolean waitForOpenInterest) {
        CompletableFuture<IbkrOptionContractActivity> f = new CompletableFuture<>();
        pendingContractActivityFutures.put(reqId, f);
        pendingContractActivity.put(reqId, IbkrOptionContractActivity.builder());
        contractActivityWaitsForOI.put(reqId, waitForOpenInterest);
        return f;
    }

    public void discardContractActivityRequest(int reqId) {
        pendingContractActivityFutures.remove(reqId);
        pendingContractActivity.remove(reqId);
        contractActivityWaitsForOI.remove(reqId);
    }

    // =========================================================================
    // Options metrics callbacks (Generic Tick Types)
    // tickGeneric receives: 106=Put/Call Ratio, 104=IV, 105=HV
    // Complete future after receiving Put/Call Ratio (most important tick)
    // =========================================================================

    @Override
    public void tickGeneric(int reqId, int tickType, double value) {
        IbkrOptionsResult.Builder b = pendingOptionsMetrics.get(reqId);
        if (b == null) return;
        switch (tickType) {
            case 106 -> {
                b.putCallRatio(value);
                // Put/Call ratio is the key metric — complete after receiving it
                CompletableFuture<IbkrOptionsResult> f = pendingOptionsMetricsFutures.remove(reqId);
                pendingOptionsMetrics.remove(reqId);
                if (f != null && !f.isDone()) f.complete(b.build());
            }
            case 104 -> b.impliedVolatility(value);
            case 105 -> b.historicalVolatility(value);
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
    }

    @Override public void nextValidId(int o) { log.info("IBKR next valid order ID: {}", o); }
    @Override public void connectAck() { log.info("IBKR connectAck received."); }
}
