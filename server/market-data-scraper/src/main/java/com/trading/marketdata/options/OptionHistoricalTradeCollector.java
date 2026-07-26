package com.trading.marketdata.options;

import com.trading.marketdata.ibkr.HistoricalRequestBudget;
import com.trading.marketdata.ibkr.IbkrMarketDataService;
import com.trading.marketdata.ibkr.IbkrTradeHistory;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;

@Service
public class OptionHistoricalTradeCollector {
    private final IbkrMarketDataService ibkr;
    public OptionHistoricalTradeCollector(IbkrMarketDataService ibkr) { this.ibkr = ibkr; }
    public IbkrTradeHistory collect(String ticker, String expiry, double strike, String right,
                                    ZonedDateTime sessionStart, HistoricalRequestBudget budget) {
        return ibkr.fetchDayTrades(ticker, expiry, strike, right, sessionStart, budget);
    }
}
