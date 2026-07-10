package com.trading.marketdata.ibkr;

import com.ib.client.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Fetch-on-request entry points for IBKR data that is NOT covered by the permanent Book
 * streams: options-chain discovery, conId resolution and the per-contract UA/OI scan.
 * These stay future-based by design — scanning dozens–hundreds of option contracts cannot
 * be permanently streamed within the market-data line budget.
 *
 * Usage pattern:
 *   1. Build a Contract (symbol, secType, exchange, currency)
 *   2. Register a CompletableFuture in the wrapper
 *   3. Send the request via EClientSocket
 *   4. Block with timeout — callbacks complete the future asynchronously
 *
 * Live quotes are NOT fetched here anymore: they are read from the MarketDataBook, fed by
 * the SubscriptionManager's permanent streams (see book/ package).
 */
@Service
public class IbkrMarketDataService {

    private static final Logger log = LoggerFactory.getLogger(IbkrMarketDataService.class);

    private static final int TIMEOUT_SECONDS = 15;

    // Deliberately shorter and separate from TIMEOUT_SECONDS: fetchContractActivity() scans
    // many contracts per call (nearest-strikes x expiries x 2), and unlike the snapshot-based
    // quote fetch, there's no documented lower bound on when (or whether) a streaming open
    // interest tick arrives — a contract with genuinely zero open interest may never send one
    // at all ("nothing to report"), which would otherwise cost the full TIMEOUT_SECONDS on
    // every such contract. This keeps a full scan bounded even when several strikes have no OI.
    @Value("${ibkr.contract-activity-timeout-seconds:2}")
    private int activityTimeoutSeconds = 5;

    private final IbkrConnectionManager connectionManager;
    private final IbkrWrapper wrapper;

    public IbkrMarketDataService(IbkrConnectionManager connectionManager, IbkrWrapper wrapper) {
        this.connectionManager = connectionManager;
        this.wrapper = wrapper;
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

        // Generic ticks require snapshot=false (streaming), not snapshot=true.
        // 100 = option volume (delivered as tick types 29/30 → put/call ratio is computed
        // from these, IBKR has no direct PCR tick), 104 = HV (arrives as 23), 106 = IV
        // (arrives as 24). We cancel the subscription manually after receiving the ticks.
        connectionManager.getClient().reqMktData(reqId, contract, "100,104,106", false, false, null);

        try {
            IbkrOptionsResult result = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug("IBKR options metrics for {}: pcr={}, iv={}, hv={}",
                    ticker, result.putCallRatio(), result.impliedVolatility(), result.historicalVolatility());
            return result;
        } catch (TimeoutException e) {
            log.warn("IBKR options metrics timeout for {} (reqId={})", ticker, reqId);
            wrapper.discardOptionsMetricsRequest(reqId);
            return null;
        } catch (Exception e) {
            log.warn("IBKR options metrics failed for {}: {}", ticker, e.getMessage());
            wrapper.discardOptionsMetricsRequest(reqId);
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
        CompletableFuture<IbkrOptionsChainResult> future = wrapper.registerChainRequest(reqId, ticker);

        // reqSecDefOptParams: returns all available expirations and strikes
        connectionManager.getClient().reqSecDefOptParams(reqId, ticker, "", "STK", underlyingConId);

        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("IBKR options chain timeout for {} (reqId={})", ticker, reqId);
            wrapper.discardChainRequest(reqId);
            return null;
        } catch (Exception e) {
            log.warn("IBKR options chain failed for {}: {}", ticker, e.getMessage());
            wrapper.discardChainRequest(reqId);
            return null;
        }
    }

    /**
     * Resolves a US stock symbol's IBKR contract ID (conId).
     *
     * reqSecDefOptParams technically accepts underlyingConId, but every real-world example
     * (IBKR's own C++/Java samples included) resolves this via reqContractDetails first rather
     * than passing 0 — passing 0 is not documented as reliably resolving every symbol, so we
     * don't rely on it. Result is effectively static per ticker (a stock's conId never changes),
     * so callers should cache this aggressively — see OptionActivityService.
     */
    public Integer fetchConId(String ticker) {
        if (!connectionManager.isConnected()) {
            log.debug("IBKR not connected — skipping conId lookup for {}", ticker);
            return null;
        }

        int reqId = connectionManager.nextReqId();
        CompletableFuture<Integer> future = wrapper.registerContractDetailsRequest(reqId);

        connectionManager.getClient().reqContractDetails(reqId, usStockContract(ticker));

        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("IBKR conId lookup timeout for {} (reqId={})", ticker, reqId);
            wrapper.discardContractDetailsRequest(reqId);
            return null;
        } catch (Exception e) {
            log.warn("IBKR conId lookup failed for {}: {}", ticker, e.getMessage());
            wrapper.discardContractDetailsRequest(reqId);
            return null;
        }
    }

    /**
     * Fetches volume (and, on demand, open interest) for one specific option contract.
     *
     * Open interest is an end-of-day figure — the exchanges publish it once, before market
     * open, and it does not change intraday. Volume changes continuously throughout the
     * session. So the two need different freshness, which is why this method takes
     * needOpenInterest as an explicit flag rather than always fetching both:
     *
     *   needOpenInterest=true  → streaming request with genericTickList "101" (Option Open
     *                            Interest per IBKR's generic tick reference). Volume (tick 8)
     *                            arrives as part of the same default-tick burst. Completes on
     *                            receiving the OI tick (27=call OI, 28=put OI depending on
     *                            the contract's right).
     *   needOpenInterest=false → streaming request with an empty genericTickList — cheaper,
     *                            only the default volume tick is requested. Completes as soon
     *                            as that arrives. Use this when open interest for this contract
     *                            is already cached and still fresh.
     *
     * Both modes use snapshot=false (streaming) and cancel the subscription in a finally block:
     * snapshot=true requests cannot carry a genericTickList at all per IBKR's docs ("Snapshot
     * requests can only be made for the default tick types; no generic ticks can be specified"),
     * so open interest specifically is unreachable via snapshot regardless of mode.
     */
    public IbkrOptionContractActivity fetchContractActivity(String ticker, String expiry, double strike,
                                                              String right, boolean needOpenInterest) {
        if (!connectionManager.isConnected()) {
            log.debug("IBKR not connected — skipping contract activity for {} {} {} {}",
                    ticker, expiry, strike, right);
            return null;
        }

        connectionManager.getClient().reqMarketDataType(1);

        int reqId = connectionManager.nextReqId();
        CompletableFuture<IbkrOptionContractActivity> future =
                wrapper.registerContractActivityRequest(reqId, needOpenInterest, right);

        Contract contract = optionContract(ticker, expiry, strike, right);
        String genericTicks = needOpenInterest ? "101" : "";

        connectionManager.getClient().reqMktData(reqId, contract, genericTicks, false, false, null);

        try {
            IbkrOptionContractActivity result = future.get(activityTimeoutSeconds, TimeUnit.SECONDS);
            log.info("IBKR contract activity for {} {} {} {}: volume={}, openInterest={}, bid={}, ask={}, last={}",
                    ticker, expiry, strike, right, result.volume(), result.openInterest(),
                    result.bid(), result.ask(), result.last());
            return result;
        } catch (TimeoutException e) {
            log.warn("IBKR contract activity timeout for {} {} {} {} (reqId={})",
                    ticker, expiry, strike, right, reqId);
            wrapper.discardContractActivityRequest(reqId);
            return null;
        } catch (java.util.concurrent.ExecutionException e) {
            wrapper.discardContractActivityRequest(reqId);
            if (e.getCause() instanceof IbkrException ie && ie.getErrorCode() == 200) {
                // "Es wurde keine Wertpapierdefinition zu der Anfrage gefunden" — this exact
                // strike/expiry/right combination is not a listed contract. Confirmed live: the
                // aggregate strike list from reqSecDefOptParams includes strikes that only exist
                // for OTHER expirations, not this one. No point retrying.
                log.debug("IBKR contract activity: {} {} {} {} does not exist for this expiry (error 200)",
                        ticker, expiry, strike, right);
                throw new IbkrContractNotFoundException(
                        ticker + " " + expiry + " " + strike + " " + right + " has no security definition");
            }
            log.warn("IBKR contract activity failed for {} {} {} {}: {}",
                    ticker, expiry, strike, right, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("IBKR contract activity failed for {} {} {} {}: {}",
                    ticker, expiry, strike, right, e.getMessage());
            wrapper.discardContractActivityRequest(reqId);
            return null;
        } finally {
            connectionManager.getClient().cancelMktData(reqId);
            wrapper.clearContractActivityRight(reqId);
        }
    }

    /**
     * Collects Nasdaq auction/NOII data (Generic Tick 225) for a US stock.
     *
     * Inverted timeout semantics compared to every other fetch in this class: there is no
     * completing tick and no end-of-data signal — outside Nasdaq's NOII dissemination windows
     * (~09:28–09:30 and ~15:50–16:00 ET) the subscription simply stays silent. The request
     * therefore streams for a FIXED collection window and then harvests whatever arrived.
     * TimeoutException on the future is the NORMAL path; the future only completes (always
     * exceptionally) on a hard error such as an unknown contract or a dropped connection.
     *
     * Same snapshot=false constraint as all other generic-tick requests: 225 cannot be
     * combined with the snapshot=true quote request.
     *
     * Returns a result with all-null fields when the feed was silent (dataAvailable is
     * decided by the caller via isEmpty()), or null on hard errors / no connection.
     */
    public IbkrAuctionResult fetchAuctionData(String ticker, long collectWindowMs) {
        if (!connectionManager.isConnected()) {
            log.debug("IBKR not connected — skipping auction data for {}", ticker);
            return null;
        }

        connectionManager.getClient().reqMarketDataType(1);

        int reqId = connectionManager.nextReqId();
        CompletableFuture<Void> errorSignal = wrapper.registerAuctionRequest(reqId);

        Contract contract = usStockContract(ticker);

        // 225 = RT Auction Values → delivered as tick types 34/35/36/61 (see IbkrAuctionResult)
        connectionManager.getClient().reqMktData(reqId, contract, "225", false, false, null);

        try {
            errorSignal.get(collectWindowMs, TimeUnit.MILLISECONDS);
            // Unreachable by design: the future never completes normally.
            log.warn("IBKR auction request for {} completed normally — unexpected (reqId={})", ticker, reqId);
            return wrapper.harvestAuctionRequest(reqId);
        } catch (TimeoutException e) {
            // Normal path: collection window elapsed, harvest whatever ticks arrived.
            IbkrAuctionResult result = wrapper.harvestAuctionRequest(reqId);
            if (result != null && !result.isEmpty()) {
                log.info("IBKR auction data for {}: price={}, volume={}, imbalance={}, regImbalance={}",
                        ticker, result.auctionPrice(), result.auctionVolume(),
                        result.imbalance(), result.regulatoryImbalance());
            }
            return result;
        } catch (Exception e) {
            log.warn("IBKR auction data failed for {}: {}", ticker, e.getMessage());
            wrapper.discardAuctionRequest(reqId);
            return null;
        } finally {
            // Always cancel the streaming subscription
            connectionManager.getClient().cancelMktData(reqId);
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

    /** expiry in IBKR's YYYYMMDD format (as returned by reqSecDefOptParams), right is "C" or "P". */
    private Contract optionContract(String ticker, String expiry, double strike, String right) {
        Contract c = new Contract();
        c.symbol(ticker);
        c.secType("OPT");
        c.currency("USD");
        c.exchange("SMART");
        c.lastTradeDateOrContractMonth(expiry);
        c.strike(strike);
        c.right(right);
        c.multiplier("100");
        return c;
    }

}
