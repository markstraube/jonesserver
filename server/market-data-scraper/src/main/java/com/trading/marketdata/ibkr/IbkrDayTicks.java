package com.trading.marketdata.ibkr;

import com.trading.marketdata.analysis.AggressorClassifier;

import java.util.List;

/**
 * Raw material for stage-2 aggressor classification: one session's trade prints and NBBO
 * updates for one option contract, already converted from IBKR wire types to the
 * classifier's internal records at this boundary (no com.ib.client types leave the ibkr
 * package).
 *
 * partial: the fetch did not cover the full session — a request timed out, pagination
 * could not advance, or the pacing budget ran out mid-fetch. The classifier propagates
 * this into the profile's status; tickCoverage quantifies how much is missing.
 */
public record IbkrDayTicks(
        List<AggressorClassifier.Trade> trades,
        List<AggressorClassifier.Quote> quotes,
        boolean partial,
        int requestEquivalentsUsed
) {}
