package com.trading.marketdata.ibkr;

public class IbkrException extends RuntimeException {
    private final int errorCode;

    public IbkrException(int errorCode, String message) {
        super(String.format("IBKR error %d: %s", errorCode, message));
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
