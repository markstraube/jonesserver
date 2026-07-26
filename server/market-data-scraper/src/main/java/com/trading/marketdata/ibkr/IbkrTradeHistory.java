package com.trading.marketdata.ibkr;

import com.trading.marketdata.analysis.AggressorClassifier;
import java.util.List;

/** Historical option trades collected independently from quote history. */
public record IbkrTradeHistory(
        List<AggressorClassifier.Trade> trades,
        boolean partial,
        int requestEquivalentsUsed
) {
    public IbkrTradeHistory {
        trades = trades == null ? List.of() : List.copyOf(trades);
    }
}
