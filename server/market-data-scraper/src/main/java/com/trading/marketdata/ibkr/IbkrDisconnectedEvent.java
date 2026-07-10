package com.trading.marketdata.ibkr;

/**
 * Published when the IB Gateway connection is lost (connectionClosed callback or reader-loop
 * exit — both can fire for one drop, listeners must be idempotent). The nightly IBC Gateway
 * restart makes this a ROUTINE event, not an error: the Book is invalidated (values kept,
 * flagged stale) and the reconnect watchdog re-establishes everything.
 */
public record IbkrDisconnectedEvent(String reason) {}
