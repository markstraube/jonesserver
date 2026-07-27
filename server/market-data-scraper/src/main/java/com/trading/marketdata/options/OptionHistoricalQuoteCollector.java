package com.trading.marketdata.options;

import com.trading.marketdata.ibkr.HistoricalRequestBudget;
import com.trading.marketdata.ibkr.IbkrMarketDataService;
import com.trading.marketdata.ibkr.IbkrQuoteHistory;
import com.trading.marketdata.ibkr.IbkrTradeHistory;
import org.springframework.stereotype.Service;
import com.trading.marketdata.service.CollectorStatusRegistry;
import java.util.Map;

import java.time.ZonedDateTime;

@Service
public class OptionHistoricalQuoteCollector {
    private final IbkrMarketDataService ibkr;
    private final CollectorStatusRegistry collectorStatus;
    public OptionHistoricalQuoteCollector(IbkrMarketDataService ibkr, CollectorStatusRegistry collectorStatus) {
        this.ibkr = ibkr;
        this.collectorStatus = collectorStatus;
    }
    public IbkrQuoteHistory collect(String ticker, String expiry, double strike, String right,
                                    ZonedDateTime sessionStart, HistoricalRequestBudget budget,
                                    IbkrTradeHistory trades) {
        CollectorStatusRegistry.Run run = collectorStatus.start(ticker, "quoteHistory");
        try {
            IbkrQuoteHistory result = ibkr.fetchDayQuotes(ticker, expiry, strike, right, sessionStart, budget, trades);
            if (result == null) {
                run.partial(Map.of("contract", expiry + " " + strike + " " + right), "no quote history");
                return null;
            }
            boolean sampled = result.coverage() != null;
            Map<String, Object> metrics = Map.of(
                    "contract", expiry + " " + strike + " " + right,
                    "quotes", result.quotes().size(),
                    "quoteIslands", result.coverage() == null ? 0 : result.coverage().size(),
                    "requestsUsed", result.requestEquivalentsUsed(),
                    "sampled", sampled
            );
            if (sampled) run.partial(metrics, "quote history sampled in coverage islands");
            else run.ok(metrics);
            return result;
        } catch (RuntimeException e) {
            run.error(e, Map.of("contract", expiry + " " + strike + " " + right));
            throw e;
        }
    }
}
