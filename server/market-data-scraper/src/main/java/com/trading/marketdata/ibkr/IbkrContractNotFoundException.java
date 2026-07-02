package com.trading.marketdata.ibkr;

/**
 * Thrown when IBKR immediately and definitively rejects a requested option contract as
 * non-existent (error 200, "no security definition has been found for the request") —
 * as opposed to a timeout, where the contract may well be valid and a retry has a real
 * chance of succeeding. Confirmed in practice: reqSecDefOptParams returns the union of
 * strikes across ALL expirations for a trading class, but not every strike is actually
 * listed for every individual expiration (particularly true for very short-dated/0DTE
 * contracts, which can lag the full strike grid after a large intraday move). Retrying
 * this specific rejection is guaranteed to fail again — it is not a transient condition.
 */
public class IbkrContractNotFoundException extends RuntimeException {
    public IbkrContractNotFoundException(String message) {
        super(message);
    }
}
