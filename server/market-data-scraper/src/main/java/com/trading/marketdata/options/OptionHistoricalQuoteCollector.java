package com.trading.marketdata.options;

import com.trading.marketdata.ibkr.HistoricalRequestBudget;
import com.trading.marketdata.ibkr.IbkrMarketDataService;
import com.trading.marketdata.ibkr.IbkrQuoteHistory;
import com.trading.marketdata.ibkr.IbkrTradeHistory;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;

@Service
public class OptionHistoricalQuoteCollector {
    private final IbkrMarketDataService ibkr;
    public OptionHistoricalQuoteCollector(IbkrMarketDataService ibkr) { this.ibkr = ibkr; }
    public IbkrQuoteHistory collect(String ticker, String expiry, double strike, String right,
                                    ZonedDateTime sessionStart, HistoricalRequestBudget budget,
                                    IbkrTradeHistory trades) {
        return ibkr.fetchDayQuotes(ticker, expiry, strike, right, sessionStart, budget, trades);
    }
}
