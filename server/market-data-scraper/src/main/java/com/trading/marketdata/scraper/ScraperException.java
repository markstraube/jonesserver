package com.trading.marketdata.scraper;

public class ScraperException extends RuntimeException {

    private final String source;

    public ScraperException(String source, String message) {
        super(message);
        this.source = source;
    }

    public ScraperException(String source, String message, Throwable cause) {
        super(message, cause);
        this.source = source;
    }

    public String getSource() {
        return source;
    }
}
