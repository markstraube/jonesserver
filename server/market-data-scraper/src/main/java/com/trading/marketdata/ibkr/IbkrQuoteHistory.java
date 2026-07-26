package com.trading.marketdata.ibkr;

import com.trading.marketdata.analysis.AggressorClassifier;
import java.util.List;

/** Historical NBBO price states plus the exact intervals they cover. */
public record IbkrQuoteHistory(
        List<AggressorClassifier.Quote> quotes,
        List<AggressorClassifier.Interval> coverage,
        int requestEquivalentsUsed
) {
    public IbkrQuoteHistory {
        quotes = quotes == null ? List.of() : List.copyOf(quotes);
        coverage = coverage == null ? null : List.copyOf(coverage);
    }
}
