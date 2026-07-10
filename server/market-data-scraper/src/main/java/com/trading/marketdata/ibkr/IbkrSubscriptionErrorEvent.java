package com.trading.marketdata.ibkr;

/**
 * Published when error() hits the reqId of a permanent Book subscription — the wrapper
 * cannot depend on the SubscriptionManager directly (it is a dependency OF it), so error
 * reactions (pacing backoff for 100, permanent-failure marking for 101) travel as an event.
 */
public record IbkrSubscriptionErrorEvent(int reqId, String symbol, int errorCode, String message) {}
