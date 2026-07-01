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
    private final Map<Integer, IbkrOptionsResult.Builder>                  pendingOptionsMetrics = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<IbkrOptionsResult>>       pendingOptionsMetricsFutures = new ConcurrentHashMap<>();

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
            IbkrQuoteResult.builderFor(reqId).volume(size.longValue());
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
        if (!"SMART".equals(exchange)) return;
        pendingChains.computeIfAbsent(reqId, k -> new IbkrOptionsChainResult.Builder())
                .expirations(expirations).strikes(strikes).multiplier(multiplier);
    }

    @Override
    public void securityDefinitionOptionalParameterEnd(int reqId) {
        IbkrOptionsChainResult.Builder b = pendingChains.remove(reqId);
        CompletableFuture<IbkrOptionsChainResult> f = pendingChainFutures.remove(reqId);
        if (b != null && f != null) f.complete(b.build());
    }

    // =========================================================================
    // Registration helpers
    // =========================================================================

    public CompletableFuture<IbkrQuoteResult> registerQuoteRequest(int reqId) {
        CompletableFuture<IbkrQuoteResult> f = new CompletableFuture<>();
        pendingQuotes.put(reqId, f);
        return f;
    }

    public CompletableFuture<IbkrOptionsChainResult> registerChainRequest(int reqId) {
        CompletableFuture<IbkrOptionsChainResult> f = new CompletableFuture<>();
        pendingChainFutures.put(reqId, f);
        return f;
    }

    public CompletableFuture<IbkrOptionsResult> registerOptionsMetricsRequest(int reqId) {
        CompletableFuture<IbkrOptionsResult> f = new CompletableFuture<>();
        pendingOptionsMetricsFutures.put(reqId, f);
        pendingOptionsMetrics.put(reqId, IbkrOptionsResult.builder());
        return f;
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
            log.debug("IBKR info [{}]: {}", errorCode, errorMsg);
            return;
        }
        if (errorCode == 10167) {
            // "Requested market data is not subscribed. Delayed market data is available."
            // This is informational — IBKR still delivers delayed data via tickPrice callbacks.
            // Do NOT fail the pending future; let it resolve normally from the delayed ticks.
            log.debug("IBKR delayed data notice [{}]: {}", errorCode, errorMsg);
            return;
        }
        log.warn("IBKR error id={} code={}: {}", id, errorCode, errorMsg);
        CompletableFuture<IbkrQuoteResult> qf = pendingQuotes.remove(id);
        if (qf != null) qf.completeExceptionally(new IbkrException(errorCode, errorMsg));
        CompletableFuture<IbkrOptionsChainResult> cf = pendingChainFutures.remove(id);
        if (cf != null) cf.completeExceptionally(new IbkrException(errorCode, errorMsg));
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
    }

    @Override public void nextValidId(int o) { log.info("IBKR next valid order ID: {}", o); }
    @Override public void connectAck() { log.info("IBKR connectAck received."); }
}
