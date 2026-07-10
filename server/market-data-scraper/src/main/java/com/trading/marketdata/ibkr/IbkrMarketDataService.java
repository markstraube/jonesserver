package com.trading.marketdata.ibkr;

import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.HistoricalTickBidAsk;
import com.ib.client.HistoricalTickLast;
import com.trading.marketdata.analysis.AggressorClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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

    // -------------------------------------------------------------------------
    // Historical day ticks (UA stage 2)
    // -------------------------------------------------------------------------

    private static final ZoneId US_EASTERN = ZoneId.of("US/Eastern");
    private static final DateTimeFormatter HIST_TICK_START_FMT = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
    private static final int MAX_TICKS_PER_REQUEST = 1000; // hard IBKR cap on reqHistoricalTicks

    // Deliberately NOT ibkr.contract-activity-timeout-seconds: that 2s is calibrated for
    // streaming-tick completion on a warm line. Historical tick requests go to the
    // historical-data farm and legitimately take several seconds — a 2s timeout would
    // mislabel most fetches PARTIAL. 15s matches the service's general TIMEOUT_SECONDS.
    @Value("${ua.aggressor.request-timeout-seconds:15}")
    int aggressorRequestTimeoutSeconds = 15;

    /**
     * Fetches one option contract's trade prints and NBBO updates from the session start
     * forward (UA stage 2 raw material), paginated per §"verified API facts":
     * exactly ONE of start/end is set (start here, end empty = ticks FORWARD from start),
     * max 1000 ticks per request, next page starts PAST the last received tick's time.
     * At one-second resolution "past" means +1s — when a full 1000-tick page cuts inside a
     * single second, the remainder of that second is unreachable and the fetch is honest
     * about it via tickCoverage rather than risking an infinite same-start loop.
     *
     * Pacing: TRADES pages cost 1 request-equivalent, BID_ASK pages cost 2 (IBKR counts
     * BID_ASK double against the shared historical-data budget). The budget is checked
     * BEFORE every page; exhaustion mid-fetch returns what arrived, flagged partial.
     * Timeouts likewise: partial result, never an exception to the caller.
     */
    public IbkrDayTicks fetchDayTicks(String ticker, String expiry, double strike, String right,
                                      ZonedDateTime sessionStartEt, HistoricalRequestBudget budget) {
        if (!connectionManager.isConnected()) {
            log.debug("IBKR not connected — skipping day ticks for {} {} {} {}", ticker, expiry, strike, right);
            return null;
        }
        Contract contract = optionContract(ticker, expiry, strike, right);
        String label = ticker + " " + expiry + " " + strike + " " + right;

        Page<HistoricalTickLast> trades = fetchTickPages(contract, label, "TRADES", 1, sessionStartEt, budget,
                new Channel<>() {
                    public CompletableFuture<List<HistoricalTickLast>> register(int reqId) { return wrapper.registerHistoricalTradesRequest(reqId); }
                    public void discard(int reqId) { wrapper.discardHistoricalTradesRequest(reqId); }
                    public long timeOf(HistoricalTickLast t) { return t.time(); }
                });
        Page<HistoricalTickBidAsk> quotes = fetchTickPages(contract, label, "BID_ASK", 2, sessionStartEt, budget,
                new Channel<>() {
                    public CompletableFuture<List<HistoricalTickBidAsk>> register(int reqId) { return wrapper.registerHistoricalBidAskRequest(reqId); }
                    public void discard(int reqId) { wrapper.discardHistoricalBidAskRequest(reqId); }
                    public long timeOf(HistoricalTickBidAsk t) { return t.time(); }
                });

        IbkrDayTicks result = new IbkrDayTicks(toTrades(trades.ticks), toQuotes(quotes.ticks),
                trades.partial || quotes.partial, trades.used + quotes.used);
        log.info("IBKR day ticks {}: trades={} quotes={} requestEquivalents={} partial={}",
                label, result.trades().size(), result.quotes().size(),
                result.requestEquivalentsUsed(), result.partial());
        return result;
    }

    private interface Channel<T> {
        CompletableFuture<List<T>> register(int reqId);
        void discard(int reqId);
        long timeOf(T tick);
    }

    private record Page<T>(List<T> ticks, boolean partial, int used) {}

    private <T> Page<T> fetchTickPages(Contract contract, String label, String whatToShow, int costPerRequest,
                                       ZonedDateTime startEt, HistoricalRequestBudget budget, Channel<T> channel) {
        List<T> all = new ArrayList<>();
        boolean partial = false;
        int used = 0;
        ZonedDateTime start = startEt.withZoneSameInstant(US_EASTERN);

        while (true) {
            if (!budget.tryConsume(costPerRequest)) {
                log.info("UA_AGGRESSOR budget exhausted mid-fetch {} {} — returning partial ({} ticks)",
                        label, whatToShow, all.size());
                partial = true;
                break;
            }
            used += costPerRequest;
            int reqId = connectionManager.nextReqId();
            CompletableFuture<List<T>> future = channel.register(reqId);
            // Timezone suffix explicit per API contract; useRth=0 (pre/post prints belong to
            // the day's flow), ignoreSize=false.
            connectionManager.getClient().reqHistoricalTicks(reqId, contract,
                    HIST_TICK_START_FMT.format(start) + " US/Eastern", "",
                    MAX_TICKS_PER_REQUEST, whatToShow, 0, false, null);

            List<T> batch;
            try {
                batch = future.get(aggressorRequestTimeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.warn("IBKR historical ticks timeout {} {} (reqId={}) — returning partial ({} ticks)",
                        label, whatToShow, reqId, all.size());
                channel.discard(reqId);
                partial = true;
                break;
            } catch (Exception e) {
                log.warn("IBKR historical ticks failed {} {} (reqId={}): {} — returning partial ({} ticks)",
                        label, whatToShow, reqId, e.getMessage(), all.size());
                channel.discard(reqId);
                partial = true;
                break;
            }

            all.addAll(batch);
            if (batch.size() < MAX_TICKS_PER_REQUEST) {
                break; // fewer than requested returned = the day is covered up to now
            }
            ZonedDateTime next = Instant.ofEpochSecond(channel.timeOf(batch.get(batch.size() - 1)) + 1)
                    .atZone(US_EASTERN);
            if (!next.isAfter(start)) {
                // Paranoia guard: a page that cannot advance the cursor would loop forever
                // (would require IBKR returning ticks before the requested start).
                log.warn("IBKR historical ticks pagination stalled {} {} at {} — returning partial",
                        label, whatToShow, start);
                partial = true;
                break;
            }
            start = next;
        }
        return new Page<>(all, partial, used);
    }

    /**
     * Boundary conversion to the classifier's internal records — no com.ib.client type
     * leaves this package. Times are epoch SECONDS (verified against TwsApi.jar; the
     * classifier's same-second collision handling exists because of this resolution).
     * Decimal sizes take the same path as the live tickSize handling: longValue() with the
     * Integer.MAX_VALUE "undefined" sentinel filtered; invalid and non-positive sizes are
     * dropped entirely (a zero-size print is not a trade, but it would still lengthen
     * sweep runs if kept).
     */
    private static List<AggressorClassifier.Trade> toTrades(List<HistoricalTickLast> raw) {
        List<AggressorClassifier.Trade> out = new ArrayList<>(raw.size());
        for (HistoricalTickLast t : raw) {
            Decimal size = t.size();
            if (size == null || !Decimal.isValid(size)) continue;
            long contracts = size.longValue();
            if (contracts <= 0 || contracts >= Integer.MAX_VALUE) continue;
            boolean unreported = t.tickAttribLast() != null && t.tickAttribLast().unreported();
            out.add(new AggressorClassifier.Trade(t.time(), t.price(), contracts,
                    t.exchange(), t.specialConditions(), unreported));
        }
        return out;
    }

    private static List<AggressorClassifier.Quote> toQuotes(List<HistoricalTickBidAsk> raw) {
        List<AggressorClassifier.Quote> out = new ArrayList<>(raw.size());
        for (HistoricalTickBidAsk q : raw) {
            // Placeholder-side quotes (bid/ask <= 0) pass through: the classifier's
            // A > B > 0 gate turns them into UNKNOWN rather than dropping the timeline entry.
            out.add(new AggressorClassifier.Quote(q.time(), q.priceBid(), q.priceAsk()));
        }
        return out;
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
