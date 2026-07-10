package com.trading.marketdata.book;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The expected-restart window must work across midnight — IBC restarts land there. */
class SubscriptionManagerWindowTest {

    @Test
    void midnightSpanningWindow() {
        String window = "23:45-00:20";
        assertTrue(SubscriptionManager.isInsideWindow(window, LocalTime.of(23, 45)));
        assertTrue(SubscriptionManager.isInsideWindow(window, LocalTime.of(23, 59)));
        assertTrue(SubscriptionManager.isInsideWindow(window, LocalTime.of(0, 0)));
        assertTrue(SubscriptionManager.isInsideWindow(window, LocalTime.of(0, 20)));
        assertFalse(SubscriptionManager.isInsideWindow(window, LocalTime.of(0, 21)));
        assertFalse(SubscriptionManager.isInsideWindow(window, LocalTime.of(23, 44)));
        assertFalse(SubscriptionManager.isInsideWindow(window, LocalTime.of(12, 0)));
    }

    @Test
    void sameDayWindow() {
        String window = "01:00-02:30";
        assertTrue(SubscriptionManager.isInsideWindow(window, LocalTime.of(1, 0)));
        assertTrue(SubscriptionManager.isInsideWindow(window, LocalTime.of(2, 30)));
        assertFalse(SubscriptionManager.isInsideWindow(window, LocalTime.of(2, 31)));
        assertFalse(SubscriptionManager.isInsideWindow(window, LocalTime.of(0, 59)));
    }

    @Test
    void blankOrBrokenWindowNeverMatches() {
        assertFalse(SubscriptionManager.isInsideWindow("", LocalTime.NOON));
        assertFalse(SubscriptionManager.isInsideWindow(null, LocalTime.NOON));
        assertFalse(SubscriptionManager.isInsideWindow("banana", LocalTime.NOON));
        assertFalse(SubscriptionManager.isInsideWindow("25:00-26:00", LocalTime.NOON));
    }
}
