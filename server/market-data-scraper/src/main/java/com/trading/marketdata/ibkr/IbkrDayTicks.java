package com.trading.marketdata.ibkr;

import com.trading.marketdata.analysis.AggressorClassifier;

import java.util.List;

/**
 * Raw material for stage-2 aggressor classification: one session's trade prints and NBBO
 * updates for one option contract, already converted from IBKR wire types to the
 * classifier's internal records at this boundary (no com.ib.client types leave the ibkr
 * package).
 *
 * tradesPartial: the trade fetch did not cover the full session (timeout, pagination
 * stall, budget cap) — only shrinks coverage, the distribution stays trustworthy for what
 * it saw.
 *
 * quoteCoverage: null = the quote stream is COMPLETE for the session. Non-null = the NBBO
 * was SAMPLED as stratified islands across the session (the full 0DTE quote stream is far
 * beyond any pacing budget); the classifier classifies only inside islands and reports
 * classifiedShare — see AggressorClassifier. An empty list = no usable quote coverage.
 */
public record IbkrDayTicks(
        List<AggressorClassifier.Trade> trades,
        List<AggressorClassifier.Quote> quotes,
        boolean tradesPartial,
        List<AggressorClassifier.Interval> quoteCoverage,
        int requestEquivalentsUsed
) {
    public boolean partial() {
        return tradesPartial || quoteCoverage != null;
    }
}
