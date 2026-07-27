package com.trading.marketdata.options;

import com.trading.marketdata.ibkr.HistoricalRequestBudget;
import com.trading.marketdata.ibkr.IbkrMarketDataService;
import com.trading.marketdata.ibkr.IbkrTradeHistory;
import org.springframework.stereotype.Service;
import com.trading.marketdata.service.CollectorStatusRegistry;
import java.util.Map;

import java.time.ZonedDateTime;

@Service
public class OptionHistoricalTradeCollector {
    private final IbkrMarketDataService ibkr;
    private final CollectorStatusRegistry collectorStatus;
    public OptionHistoricalTradeCollector(IbkrMarketDataService ibkr, CollectorStatusRegistry collectorStatus) {
        this.ibkr = ibkr;
        this.collectorStatus = collectorStatus;
    }
    public IbkrTradeHistory collect(String ticker, String expiry, double strike, String right,
                                    ZonedDateTime sessionStart, HistoricalRequestBudget budget) {
        CollectorStatusRegistry.Run run = collectorStatus.start(ticker, "tradeHistory");
        try {
            IbkrTradeHistory result = ibkr.fetchDayTrades(ticker, expiry, strike, right, sessionStart, budget);
            if (result == null) {
                run.partial(Map.of("contract", expiry + " " + strike + " " + right), "no trade history");
                return null;
            }
            Map<String, Object> metrics = Map.of(
                    "contract", expiry + " " + strike + " " + right,
                    "trades", result.trades().size(),
                    "requestsUsed", result.requestEquivalentsUsed(),
                    "partial", result.partial()
            );
            if (result.partial()) run.partial(metrics, "trade history did not cover the full session");
            else run.ok(metrics);
            return result;
        } catch (RuntimeException e) {
            run.error(e, Map.of("contract", expiry + " " + strike + " " + right));
            throw e;
        }
    }
}
