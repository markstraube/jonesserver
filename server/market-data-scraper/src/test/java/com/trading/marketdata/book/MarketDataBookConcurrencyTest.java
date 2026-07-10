package com.trading.marketdata.book;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One writer thread per field, many readers, no torn reads — the Book's concurrency contract.
 * The writer encodes the value into the timestamp (epoch milli == value), so any reader that
 * ever observes a Stamped whose timestamp does not match its value has caught a torn read.
 */
class MarketDataBookConcurrencyTest {

    @Test
    void readersNeverObserveTornStamps() throws Exception {
        MarketDataBook book = new MarketDataBook();
        TickerBook mu = book.book("MU");

        int writes = 200_000;
        int readerCount = 4;
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean writerDone = new AtomicBoolean(false);
        AtomicReference<String> tornRead = new AtomicReference<>();

        Thread writer = Thread.ofVirtual().start(() -> {
            awaitQuietly(start);
            for (long i = 1; i <= writes; i++) {
                mu.volume().update(i, Instant.ofEpochMilli(i));
            }
            writerDone.set(true);
        });

        List<Thread> readers = new ArrayList<>();
        for (int r = 0; r < readerCount; r++) {
            readers.add(Thread.ofVirtual().start(() -> {
                awaitQuietly(start);
                while (!writerDone.get() && tornRead.get() == null) {
                    TimestampedField.Stamped<Long> s = mu.volume().get();
                    if (s.value() == null) continue; // not yet written
                    // value/timestamp pair must always be consistent (single volatile record)
                    if (s.lastSeenAt().toEpochMilli() != s.value()
                            || s.lastChangedAt().toEpochMilli() != s.value()) {
                        tornRead.compareAndSet(null, "value=" + s.value()
                                + " seenAt=" + s.lastSeenAt() + " changedAt=" + s.lastChangedAt());
                    }
                }
            }));
        }

        start.countDown();
        writer.join();
        for (Thread t : readers) t.join();

        assertNull(tornRead.get(), "torn read observed: " + tornRead.get());
        assertEquals(writes, mu.volume().value());
    }

    @Test
    void bookCreatesOneTickerBookPerSymbolCaseInsensitive() {
        MarketDataBook book = new MarketDataBook();
        assertSame(book.book("mu"), book.book("MU"));
        assertSame(book.book("MU"), book.find("Mu"));
        assertNull(book.find("SPY"), "find() must never create");
    }

    @Test
    void invalidateAllFlagsEveryFieldAndKeepsValues() {
        MarketDataBook book = new MarketDataBook();
        Instant t = Instant.parse("2026-07-10T14:00:00Z");
        TickerBook mu = book.book("MU");
        mu.last().update(940.25, t);
        mu.volume().update(1_000_000L, t);
        mu.impliedVolatility().update(0.55, t);

        book.invalidateAll("test");

        assertTrue(book.isConnectionLost());
        assertTrue(mu.last().get().invalidated());
        assertTrue(mu.volume().get().invalidated());
        assertTrue(mu.impliedVolatility().get().invalidated());
        assertTrue(mu.bid().get().invalidated(), "never-seen fields are flagged too");
        assertEquals(940.25, mu.last().value(), "values stay readable after invalidation");
        assertEquals(t, mu.last().get().lastSeenAt(), "ages freeze, they don't reset");

        // A fresh tick after reconnect clears the flag field by field
        mu.last().update(941.00, t.plusSeconds(60));
        assertTrue(!mu.last().get().invalidated());
        assertTrue(mu.volume().get().invalidated(), "fields without fresh ticks stay flagged");

        book.markConnected();
        assertTrue(!book.isConnectionLost());
    }

    @Test
    void putCallRatioComputedFromBothVolumeTicks() {
        // There is no direct PCR tick in the TWS API — computed from ticks 29/30.
        TickerBook b = new MarketDataBook().book("MU");
        assertNull(b.putCallRatio(), "no volumes yet");
        b.callOptionVolume().update(2000L, Instant.now());
        assertNull(b.putCallRatio(), "put side missing");
        b.putOptionVolume().update(1000L, Instant.now());
        assertEquals(0.5, b.putCallRatio());
        b.callOptionVolume().update(0L, Instant.now());
        assertNull(b.putCallRatio(), "zero call volume must not divide");
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
