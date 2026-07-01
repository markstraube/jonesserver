package com.trading.marketdata.ibkr;

import com.ib.client.Contract;
import com.trading.marketdata.model.QuoteData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * High-level service for requesting market data from IB Gateway.
 *
 * Usage pattern:
 *   1. Build a Contract (symbol, secType, exchange, currency)
 *   2. Register a CompletableFuture in the wrapper
 *   3. Send the request via EClientSocket
 *   4. Block with timeout — callbacks complete the future asynchronously
 *
 * This service is intentionally thin. Business logic (fallback, caching)
 * stays in the existing Service layer (QuoteService, OptionsService).
 */
@Service
public class IbkrMarketDataService {

    private static final Logger log = LoggerFactory.getLogger(IbkrMarketDataService.class);

    private static final int TIMEOUT_SECONDS = 5;

    private final IbkrConnectionManager connectionManager;
    private final IbkrWrapper wrapper;

    public IbkrMarketDataService(IbkrConnectionManager connectionManager, IbkrWrapper wrapper) {
        this.connectionManager = connectionManager;
        this.wrapper = wrapper;
    }

    /**
     * Fetches a realtime snapshot quote for a US stock via IBKR.
     * Returns null if gateway is not connected or request times out.
     */
    public QuoteData fetchQuote(String ticker) {
        if (!connectionManager.isConnected()) {
            log.debug("IBKR not connected — skipping quote for {}", ticker);
            return null;
        }

        // 1 = live, 3 = delayed, 4 = delayed-frozen
        // Live subscriptions are active — request realtime data
        connectionManager.getClient().reqMarketDataType(1);

        int reqId = connectionManager.nextReqId();
        CompletableFuture<IbkrQuoteResult> future = wrapper.registerQuoteRequest(reqId);

        Contract contract = usStockContract(ticker);

        // genericTickList "" = standard ticks only; snapshot = true = one-time request (no subscription)
        connectionManager.getClient().reqMktData(reqId, contract, "", true, false, null);

        try {
            IbkrQuoteResult result = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return toQuoteData(ticker, result);
        } catch (TimeoutException e) {
            log.warn("IBKR quote timeout for {} (reqId={})", ticker, reqId);
            return null;
        } catch (Exception e) {
            log.warn("IBKR quote failed for {}: {}", ticker, e.getMessage());
            return null;
        }
    }

    /**
     * Fetches options metrics for a US stock via IBKR Generic Tick Types:
     *   - 106 = Put/Call Ratio
     *   - 104 = 30-day Implied Volatility
     *   - 105 = 30-day Historical Volatility
     *
     * Generic ticks require a streaming subscription (snapshot=false).
     * We start the stream, wait for the ticks, then cancel.
     */
    public IbkrOptionsResult fetchOptionsMetrics(String ticker) {
        if (!connectionManager.isConnected()) {
            log.debug("IBKR not connected — skipping options metrics for {}", ticker);
            return null;
        }

        connectionManager.getClient().reqMarketDataType(1);

        int reqId = connectionManager.nextReqId();
        CompletableFuture<IbkrOptionsResult> future = wrapper.registerOptionsMetricsRequest(reqId);

        Contract contract = usStockContract(ticker);

        // Generic ticks require snapshot=false (streaming), not snapshot=true
        // We cancel the subscription manually after receiving the ticks
        connectionManager.getClient().reqMktData(reqId, contract, "104,105,106", false, false, null);

        try {
            IbkrOptionsResult result = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug("IBKR options metrics for {}: pcr={}, iv={}, hv={}",
                    ticker, result.putCallRatio(), result.impliedVolatility(), result.historicalVolatility());
            return result;
        } catch (TimeoutException e) {
            log.warn("IBKR options metrics timeout for {} (reqId={})", ticker, reqId);
            return null;
        } catch (Exception e) {
            log.warn("IBKR options metrics failed for {}: {}", ticker, e.getMessage());
            return null;
        } finally {
            // Always cancel the streaming subscription
            connectionManager.getClient().cancelMktData(reqId);
        }
    }

    /**
     * Fetches available option expirations and strikes for a US stock.
     * Useful for building a Gamma-Wall analysis without scraping.
     */
    public IbkrOptionsChainResult fetchOptionsChain(String ticker, int underlyingConId) {
        if (!connectionManager.isConnected()) {
            log.debug("IBKR not connected — skipping options chain for {}", ticker);
            return null;
        }

        connectionManager.getClient().reqMarketDataType(1);

        int reqId = connectionManager.nextReqId();
        CompletableFuture<IbkrOptionsChainResult> future = wrapper.registerChainRequest(reqId);

        // reqSecDefOptParams: returns all available expirations and strikes
        connectionManager.getClient().reqSecDefOptParams(reqId, ticker, "", "STK", underlyingConId);

        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("IBKR options chain timeout for {} (reqId={})", ticker, reqId);
            return null;
        } catch (Exception e) {
            log.warn("IBKR options chain failed for {}: {}", ticker, e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Contract usStockContract(String ticker) {
        Contract c = new Contract();
        c.symbol(ticker);
        c.secType("STK");
        c.currency("USD");
        c.exchange("SMART");   // SMART routing picks the best exchange automatically
        return c;
    }

    private QuoteData toQuoteData(String ticker, IbkrQuoteResult r) {
        if (r == null) return null;

        Double price = r.last() != null ? r.last()
                     : (r.bid() != null && r.ask() != null) ? (r.bid() + r.ask()) / 2.0
                     : null;

        Double change    = (price != null && r.close() != null) ? price - r.close() : null;
        Double changePct = (change != null && r.close() != null && r.close() != 0)
                         ? (change / r.close()) * 100.0 : null;

        return new QuoteData(
                ticker,
                price,
                change,
                changePct,
                r.open(),
                r.high(),
                r.low(),
                r.volume(),
                null,   // avgVolume — not from snapshot
                null,   // volumeRatio
                null,   // marketCap
                null,   // P/E
                null,   // 52w high
                null,   // 52w low
                null,   // marketState
                null,   // preMarketPrice
                null,   // preMarketChangePct
                null,   // postMarketPrice
                null,   // postMarketChangePct
                "ibkr",
                null,
                price != null,
                Instant.now()
        );
    }
}
