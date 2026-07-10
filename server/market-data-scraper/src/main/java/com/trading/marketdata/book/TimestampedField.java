package com.trading.marketdata.book;

import java.time.Instant;
import java.util.Objects;

/**
 * One last-value field of the market data Book, carrying the timestamp pair that is the core
 * of the Book design:
 *
 *   lastChangedAt — the value actually changed (or was set for the first time)
 *   lastSeenAt    — any tick for this field arrived, even one carrying the same value
 *
 * The pair distinguishes the two situations a request/response consumer of a tick stream can
 * never tell apart: old lastChangedAt + fresh lastSeenAt = calm market (healthy feed, value
 * simply isn't moving); old lastChangedAt + old lastSeenAt = dead feed. Ticks arrive only on
 * change from IBKR for most fields, so "seen" freshness is judged per group against a liveness
 * anchor, not per field in isolation — see the SubscriptionManager watchdog.
 *
 * Thread safety: designed for ONE writer thread per field (the EReader callback thread for
 * tick fields, the scanner thread for scan-result fields) and any number of concurrent
 * readers. State is a single immutable record behind one volatile reference, so readers can
 * never observe a torn value/timestamp combination; the writer's read-modify-write is safe
 * because it is the only writer.
 */
public final class TimestampedField<T> {

    /**
     * Immutable view of the field at one instant. {@code value} may be null (never seen).
     *
     * {@code notSubscribed}: IBKR reported (error 10090) that the market-data subscription
     * of the account does not cover this field's tick group — silence here is neither
     * stillness nor a dead feed, the data plainly cannot arrive. Cleared by the next tick
     * (which proves the opposite).
     */
    public record Stamped<T>(T value, Instant lastChangedAt, Instant lastSeenAt,
                             boolean invalidated, boolean notSubscribed) {
        public boolean isPresent() {
            return value != null;
        }

        /** Seconds since any tick arrived, or null when never seen. */
        public Long ageSeconds(Instant now) {
            return lastSeenAt == null ? null : Math.max(0, now.getEpochSecond() - lastSeenAt.getEpochSecond());
        }

        /** Seconds since the value last changed, or null when never seen. */
        public Long changedAgeSeconds(Instant now) {
            return lastChangedAt == null ? null : Math.max(0, now.getEpochSecond() - lastChangedAt.getEpochSecond());
        }
    }

    private static final Stamped<?> EMPTY = new Stamped<>(null, null, null, false, false);

    @SuppressWarnings("unchecked")
    private volatile Stamped<T> state = (Stamped<T>) EMPTY;

    /** Records a tick carrying {@code newValue} at {@code now}. Same value → only lastSeenAt
     *  moves; different value → both timestamps move. Any update clears invalidation AND
     *  notSubscribed: an arriving tick proves the feed speaks and the subscription covers
     *  this field. */
    public void update(T newValue, Instant now) {
        Stamped<T> cur = state;
        boolean changed = !Objects.equals(cur.value(), newValue);
        state = new Stamped<>(newValue, changed ? now : cur.lastChangedAt(), now, false, false);
    }

    public void update(T newValue) {
        update(newValue, Instant.now());
    }

    /**
     * Marks the last known value as no longer live (connection lost). The value and both
     * timestamps are deliberately KEPT — last known values remain readable, honestly aged;
     * only the flag says "the feed behind this is currently gone". The next update clears it.
     */
    public void invalidate() {
        Stamped<T> cur = state;
        if (!cur.invalidated()) {
            state = new Stamped<>(cur.value(), cur.lastChangedAt(), cur.lastSeenAt(), true, cur.notSubscribed());
        }
    }

    /**
     * IBKR error 10090: the account's market-data subscription does not cover this field's
     * tick group. Value and timestamps are kept (a pre-downgrade value may exist); the flag
     * tells readers that silence here is a SUBSCRIPTION gap, not market stillness and not a
     * dead feed — the liveness/staleness logic must not raise alarms for it. Cleared by the
     * next tick.
     */
    public void markNotSubscribed() {
        Stamped<T> cur = state;
        if (!cur.notSubscribed()) {
            state = new Stamped<>(cur.value(), cur.lastChangedAt(), cur.lastSeenAt(), cur.invalidated(), true);
        }
    }

    /** Atomic snapshot of value + timestamps + invalidation flag. */
    public Stamped<T> get() {
        return state;
    }

    /** Convenience: current value or null. */
    public T value() {
        return state.value();
    }
}
