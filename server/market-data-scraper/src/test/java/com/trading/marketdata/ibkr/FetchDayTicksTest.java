package com.trading.marketdata.ibkr;

import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.HistoricalTickBidAsk;
import com.ib.client.HistoricalTickLast;
import com.ib.client.TagValue;
import com.ib.client.TickAttribBidAsk;
import com.ib.client.TickAttribLast;
import com.trading.marketdata.book.MarketDataBook;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * fetchDayTicks pagination and boundary conversion, driven through a stubbed
 * EClientSocket that answers reqHistoricalTicks synchronously via the wrapper callbacks
 * (the same thread-completes-future shortcut the EReader thread provides live). No
 * Mockito — the stub is a plain subclass, same pattern as the other ibkr tests.
 */
class FetchDayTicksTest {

    private static final ZonedDateTime SESSION_START =
            ZonedDateTime.of(2026, 7, 10, 9, 30, 0, 0, ZoneId.of("US/Eastern"));
    private static final long T0 = SESSION_START.toEpochSecond();

    private static HistoricalTickLast trade(long time, double price, long size) {
        return new HistoricalTickLast(time, new TickAttribLast(), price, Decimal.get(size), "CBOE", null);
    }

    private static HistoricalTickBidAsk quote(long time, double bid, double ask) {
        return new HistoricalTickBidAsk(time, new TickAttribBidAsk(), bid, ask, Decimal.get(1), Decimal.get(1));
    }

    /** Answers each reqHistoricalTicks synchronously with the next prepared batch (done=true). */
    private static final class StubClient extends EClientSocket {
        final List<String> requests = new ArrayList<>(); // "<whatToShow>|<startDateTime>"
        final Deque<List<HistoricalTickLast>> tradeBatches = new ArrayDeque<>();
        final Deque<List<HistoricalTickBidAsk>> quoteBatches = new ArrayDeque<>();
        private final IbkrWrapper wrapper;

        StubClient(IbkrWrapper wrapper) {
            super(wrapper, new EJavaSignal());
            this.wrapper = wrapper;
        }

        @Override
        public synchronized void reqHistoricalTicks(int reqId, Contract contract, String startDateTime,
                String endDateTime, int numberOfTicks, String whatToShow, int useRth,
                boolean ignoreSize, List<TagValue> miscOptions) {
            requests.add(whatToShow + "|" + startDateTime);
            if ("TRADES".equals(whatToShow)) {
                wrapper.historicalTicksLast(reqId,
                        tradeBatches.isEmpty() ? List.of() : tradeBatches.poll(), true);
            } else {
                wrapper.historicalTicksBidAsk(reqId,
                        quoteBatches.isEmpty() ? List.of() : quoteBatches.poll(), true);
            }
        }
    }

    private record Fixture(IbkrMarketDataService service, StubClient client) {}

    private static Fixture fixture() {
        IbkrWrapper wrapper = new IbkrWrapper(new MarketDataBook(), event -> {});
        StubClient client = new StubClient(wrapper);
        IbkrConnectionManager conn = new IbkrConnectionManager(wrapper, event -> {}) {
            @Override public boolean isConnected() { return true; }
            @Override public EClientSocket getClient() { return client; }
        };
        IbkrMarketDataService service = new IbkrMarketDataService(conn, wrapper);
        service.aggressorRequestTimeoutSeconds = 5;
        return new Fixture(service, client);
    }

    @Test
    void singlePageFetchConvertsAndFilters() {
        Fixture fx = fixture();
        HistoricalTickLast unreported = new HistoricalTickLast(T0 + 10, new TickAttribLast(),
                5.0, Decimal.get(7), "ISE", "cond");
        unreported.tickAttribLast().unreported(true);
        fx.client().tradeBatches.add(List.of(
                trade(T0 + 5, 5.0, 10),
                trade(T0 + 6, 5.0, 0),                        // zero-size print → dropped
                trade(T0 + 7, 5.0, Integer.MAX_VALUE),        // sentinel → dropped
                unreported));
        fx.client().quoteBatches.add(List.of(quote(T0 + 1, 4.0, 6.0)));

        IbkrDayTicks result = fx.service().fetchDayTicks("MU", "20260710", 100, "C",
                SESSION_START, new HistoricalRequestBudget(20));

        assertFalse(result.partial());
        assertEquals(3, result.requestEquivalentsUsed()); // 1 TRADES + 2 BID_ASK
        assertEquals(2, result.trades().size());
        assertTrue(result.trades().get(1).unreported());
        assertEquals("cond", result.trades().get(1).specialConditions());
        assertEquals(1, result.quotes().size());
        assertEquals(T0 + 1, result.quotes().get(0).epochSeconds());
        // Exactly one page each, starting at the session start in ET with explicit timezone
        assertEquals(List.of("TRADES|20260710 09:30:00 US/Eastern",
                             "BID_ASK|20260710 09:30:00 US/Eastern"),
                fx.client().requests);
    }

    @Test
    void fullPagePaginatesPastLastTickSecond() {
        Fixture fx = fixture();
        List<HistoricalTickLast> fullPage = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            fullPage.add(trade(T0 + i, 5.0, 1)); // last tick at T0+999
        }
        fx.client().tradeBatches.add(fullPage);
        fx.client().tradeBatches.add(List.of(trade(T0 + 1200, 5.0, 2)));
        fx.client().quoteBatches.add(List.of());

        IbkrDayTicks result = fx.service().fetchDayTicks("MU", "20260710", 100, "C",
                SESSION_START, new HistoricalRequestBudget(20));

        assertFalse(result.partial());
        assertEquals(1001, result.trades().size());
        assertEquals(4, result.requestEquivalentsUsed()); // 2 TRADES pages + 1 BID_ASK page
        // Second TRADES page starts one second PAST the last received tick (T0+999 → +1000s after 09:30)
        assertEquals("TRADES|20260710 09:46:40 US/Eastern", fx.client().requests.get(1));
    }

    @Test
    void budgetExhaustionMidFetchReturnsPartial() {
        Fixture fx = fixture();
        fx.client().tradeBatches.add(List.of(trade(T0 + 5, 5.0, 10)));
        // Budget 1: the TRADES page consumes it; the BID_ASK page (cost 2) cannot start.
        IbkrDayTicks result = fx.service().fetchDayTicks("MU", "20260710", 100, "C",
                SESSION_START, new HistoricalRequestBudget(1));

        assertTrue(result.partial());
        assertEquals(1, result.trades().size());
        assertEquals(0, result.quotes().size());
        assertEquals(1, result.requestEquivalentsUsed());
        assertEquals(1, fx.client().requests.size()); // BID_ASK request was never sent
    }

    @Test
    void wrapperAccumulatesBatchesUntilDone() {
        IbkrWrapper wrapper = new IbkrWrapper(new MarketDataBook(), event -> {});
        var future = wrapper.registerHistoricalTradesRequest(42);

        wrapper.historicalTicksLast(42, List.of(trade(T0, 5.0, 1)), false);
        assertFalse(future.isDone());
        wrapper.historicalTicksLast(42, List.of(trade(T0 + 1, 5.0, 2)), true);
        assertTrue(future.isDone());
        assertEquals(2, future.join().size());
    }

    @Test
    void wrapperErrorFailsPendingHistoricalRequest() {
        IbkrWrapper wrapper = new IbkrWrapper(new MarketDataBook(), event -> {});
        var future = wrapper.registerHistoricalBidAskRequest(43);

        wrapper.error(43, 0L, 162, "Historical Market Data Service error", null);
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    void lateBatchAfterDiscardIsIgnored() {
        IbkrWrapper wrapper = new IbkrWrapper(new MarketDataBook(), event -> {});
        var future = wrapper.registerHistoricalTradesRequest(44);
        wrapper.discardHistoricalTradesRequest(44); // timeout path

        wrapper.historicalTicksLast(44, List.of(trade(T0, 5.0, 1)), true);
        assertFalse(future.isDone()); // late done must not complete a discarded request
    }
}
