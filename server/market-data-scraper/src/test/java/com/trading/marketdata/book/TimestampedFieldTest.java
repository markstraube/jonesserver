package com.trading.marketdata.book;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimestampedFieldTest {

    private static final Instant T0 = Instant.parse("2026-07-10T14:00:00Z");
    private static final Instant T1 = T0.plusSeconds(10);
    private static final Instant T2 = T0.plusSeconds(20);

    @Test
    void neverSeenFieldIsEmpty() {
        TimestampedField<Double> f = new TimestampedField<>();
        TimestampedField.Stamped<Double> s = f.get();
        assertNull(s.value());
        assertNull(s.lastChangedAt());
        assertNull(s.lastSeenAt());
        assertFalse(s.invalidated());
        assertNull(s.ageSeconds(T0));
        assertNull(s.changedAgeSeconds(T0));
        assertFalse(s.isPresent());
    }

    @Test
    void firstUpdateSetsBothTimestamps() {
        TimestampedField<Double> f = new TimestampedField<>();
        f.update(101.5, T0);
        TimestampedField.Stamped<Double> s = f.get();
        assertEquals(101.5, s.value());
        assertEquals(T0, s.lastChangedAt());
        assertEquals(T0, s.lastSeenAt());
    }

    @Test
    void sameValueTickMovesOnlyLastSeenAt() {
        TimestampedField<Double> f = new TimestampedField<>();
        f.update(101.5, T0);
        f.update(101.5, T1);
        TimestampedField.Stamped<Double> s = f.get();
        assertEquals(101.5, s.value());
        assertEquals(T0, s.lastChangedAt(), "same-value tick must not move lastChangedAt");
        assertEquals(T1, s.lastSeenAt(), "same-value tick must move lastSeenAt");
    }

    @Test
    void changedValueTickMovesBothTimestamps() {
        TimestampedField<Double> f = new TimestampedField<>();
        f.update(101.5, T0);
        f.update(102.0, T1);
        TimestampedField.Stamped<Double> s = f.get();
        assertEquals(102.0, s.value());
        assertEquals(T1, s.lastChangedAt());
        assertEquals(T1, s.lastSeenAt());
    }

    @Test
    void calmMarketVsDeadFeedAreDistinguishable() {
        // The core of the design: old lastChangedAt + fresh lastSeenAt = calm market,
        // old lastChangedAt + old lastSeenAt = dead feed.
        TimestampedField<Long> calm = new TimestampedField<>();
        calm.update(500L, T0);
        calm.update(500L, T2); // feed alive, value still

        TimestampedField<Long> dead = new TimestampedField<>();
        dead.update(500L, T0); // nothing since

        Instant now = T2.plusSeconds(5);
        assertEquals(5L, calm.get().ageSeconds(now));
        assertEquals(25L, calm.get().changedAgeSeconds(now));
        assertEquals(25L, dead.get().ageSeconds(now));
        assertEquals(25L, dead.get().changedAgeSeconds(now));
    }

    @Test
    void invalidateKeepsValueAndAgesButSetsFlag() {
        TimestampedField<Double> f = new TimestampedField<>();
        f.update(99.0, T0);
        f.invalidate();
        TimestampedField.Stamped<Double> s = f.get();
        assertTrue(s.invalidated());
        assertEquals(99.0, s.value(), "invalidation must not clear the last known value");
        assertEquals(T0, s.lastChangedAt());
        assertEquals(T0, s.lastSeenAt(), "invalidation must freeze ages, not reset them");
    }

    @Test
    void updateAfterInvalidationClearsTheFlag() {
        TimestampedField<Double> f = new TimestampedField<>();
        f.update(99.0, T0);
        f.invalidate();
        f.update(99.0, T1); // even a same-value tick proves the feed is back
        TimestampedField.Stamped<Double> s = f.get();
        assertFalse(s.invalidated());
        assertEquals(T0, s.lastChangedAt());
        assertEquals(T1, s.lastSeenAt());
    }

    @Test
    void invalidateOnNeverSeenFieldIsHarmless() {
        TimestampedField<Double> f = new TimestampedField<>();
        f.invalidate();
        TimestampedField.Stamped<Double> s = f.get();
        assertTrue(s.invalidated());
        assertNull(s.value());
        assertNull(s.lastSeenAt());
    }

    @Test
    void nullTickIsSeenButNotAChange() {
        // A fresh field's value is null, so update(null) is a same-value tick: seen, unchanged.
        TimestampedField<Long> f = new TimestampedField<>();
        f.update(null, T0);
        assertNull(f.get().lastChangedAt());
        assertEquals(T0, f.get().lastSeenAt());
        f.update(7L, T1); // null -> value is a real change
        assertEquals(T1, f.get().lastChangedAt());
        assertEquals(T1, f.get().lastSeenAt());
    }
}
