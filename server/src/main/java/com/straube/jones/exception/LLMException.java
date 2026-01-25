package com.straube.jones.exception;

public class LLMException extends RuntimeException {
    private final String errorCode;
    private final boolean retryable;

    public LLMException(String message, String errorCode, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public LLMException(String message, Throwable cause, String errorCode, boolean retryable) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
