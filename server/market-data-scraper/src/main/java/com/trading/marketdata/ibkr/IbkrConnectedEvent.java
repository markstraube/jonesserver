package com.trading.marketdata.ibkr;

/** Published after a successful (re)connect to IB Gateway — the SubscriptionManager
 *  reacts by (re)establishing all permanent Book subscriptions. */
public record IbkrConnectedEvent() {}
