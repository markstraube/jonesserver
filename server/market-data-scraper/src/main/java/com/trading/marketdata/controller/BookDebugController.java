package com.trading.marketdata.controller;

import com.trading.marketdata.book.SubscriptionManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Verification-only endpoints for the Book lifecycle, disabled by default
 * (book.debug-endpoints-enabled=true to activate — never in production).
 */
@RestController
@RequestMapping("/api/v1/book/debug")
@Tag(name = "Book Debug", description = "Test hooks for Book lifecycle verification (disabled by default)")
@ConditionalOnProperty(value = "book.debug-endpoints-enabled", havingValue = "true")
public class BookDebugController {

    private final SubscriptionManager subscriptionManager;

    public BookDebugController(SubscriptionManager subscriptionManager) {
        this.subscriptionManager = subscriptionManager;
    }

    @PostMapping("/kill/{ticker}")
    @Operation(summary = "Simulate a dead subscription",
            description = "Cancels the ticker's stream at the Gateway while keeping the local "
                    + "subscription entry — the line goes silent and the liveness watchdog must "
                    + "detect it and AUTO_RESUBSCRIBE during REGULAR market state.")
    public ResponseEntity<Map<String, Object>> killSubscription(
            @Parameter(description = "Book symbol, e.g. MU") @PathVariable String ticker) {
        boolean killed = subscriptionManager.killSubscriptionForTest(ticker);
        return killed
                ? ResponseEntity.ok(Map.of("ticker", ticker.toUpperCase(), "killed", true,
                        "expect", "AUTO_RESUBSCRIBE within book.ticker-stale-seconds during REGULAR"))
                : ResponseEntity.badRequest().body(Map.of("ticker", ticker.toUpperCase(), "killed", false,
                        "reason", "no active subscription or not connected"));
    }
}
