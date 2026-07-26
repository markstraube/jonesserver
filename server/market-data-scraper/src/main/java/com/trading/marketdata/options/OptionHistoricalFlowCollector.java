package com.trading.marketdata.options;

import com.trading.marketdata.ibkr.HistoricalRequestBudget;
import com.trading.marketdata.ibkr.IbkrDayTicks;
import com.trading.marketdata.ibkr.IbkrQuoteHistory;
import com.trading.marketdata.ibkr.IbkrTradeHistory;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;

/** Coordinates independent trade and quote collectors under one shared budget. */
@Service
public class OptionHistoricalFlowCollector {
    private final OptionHistoricalTradeCollector trades;
    private final OptionHistoricalQuoteCollector quotes;

    public OptionHistoricalFlowCollector(OptionHistoricalTradeCollector trades, OptionHistoricalQuoteCollector quotes) {
        this.trades = trades;
        this.quotes = quotes;
    }

    public IbkrDayTicks collect(String ticker, String expiry, double strike, String right,
                                ZonedDateTime sessionStart, HistoricalRequestBudget budget) {
        if (budget == null) return null;
        int available = budget.remaining();
        int quoteReserve = available >= 3 ? Math.max(2, (available / 2) & ~1) : 0;
        quoteReserve = Math.min(quoteReserve, Math.max(0, available - 1));
        IbkrTradeHistory tradeHistory = trades.collect(ticker, expiry, strike, right, sessionStart,
                budget.slice(Math.max(0, available - quoteReserve)));
        if (tradeHistory == null) return null;
        IbkrQuoteHistory quoteHistory = quotes.collect(ticker, expiry, strike, right, sessionStart, budget, tradeHistory);
        if (quoteHistory == null) return null;
        return new IbkrDayTicks(tradeHistory.trades(), quoteHistory.quotes(), tradeHistory.partial(),
                quoteHistory.coverage(), tradeHistory.requestEquivalentsUsed() + quoteHistory.requestEquivalentsUsed());
    }
}
