package com.trading.marketdata.analysis;

import com.trading.marketdata.analysis.IntradayVolumeCurve.Point;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.trading.marketdata.analysis.IntradayVolumeCurve.SESSION_SECONDS;
import static org.junit.jupiter.api.Assertions.*;

class IntradayVolumeCurveTest {

    /** A synthetic day with LINEAR cumulative volume: share(t) = t / session. */
    private static List<Point> linearDay(long finalVolume) {
        List<Point> day = new ArrayList<>();
        for (int s = 0; s <= SESSION_SECONDS; s += 300) {
            day.add(new Point(s, Math.round(finalVolume * (s / (double) SESSION_SECONDS))));
        }
        return day;
    }

    /** A U-shaped day: half the volume in the first hour, 30% in the last hour. */
    private static List<Point> uShapedDay() {
        List<Point> day = new ArrayList<>();
        day.add(new Point(0, 0));
        day.add(new Point(3600, 500_000));                    // heavy open
        day.add(new Point(SESSION_SECONDS - 3600, 700_000));  // quiet middle
        day.add(new Point(SESSION_SECONDS, 1_000_000));       // heavy close
        return day;
    }

    @Test
    void linearDaysYieldLinearCurve() {
        var curve = IntradayVolumeCurve.build(
                List.of(linearDay(1_000_000), linearDay(5_000_000)), 2);
        assertNotNull(curve);
        assertEquals(2, curve.daysUsed());
        assertEquals(0.5, curve.shareAt(SESSION_SECONDS / 2), 0.01);
        assertEquals(0.25, curve.shareAt(SESSION_SECONDS / 4), 0.01);
        assertEquals(1.0, curve.shareAt(SESSION_SECONDS), 1e-9);
    }

    @Test
    void uShapeIsPreservedNotLinearized() {
        var curve = IntradayVolumeCurve.build(List.of(uShapedDay(), uShapedDay()), 2);
        assertNotNull(curve);
        // After the first hour, HALF the day's volume is on the tape — the whole point of
        // the empirical curve vs. a linear time fraction (which would claim ~15%).
        assertEquals(0.5, curve.shareAt(3600), 0.01);
        double linearFraction = 3600 / (double) SESSION_SECONDS;
        assertTrue(curve.shareAt(3600) > linearFraction * 2);
    }

    @Test
    void partialDaysAreExcluded() {
        // A recording that stopped at noon must not contribute (its shares would be
        // computed against half a day's volume). Only 1 qualifying day < minDays 2 → null.
        List<Point> partial = List.of(new Point(0, 0), new Point(9000, 800_000));
        assertNull(IntradayVolumeCurve.build(List.of(linearDay(1_000_000), partial), 2));
        // With minDays 1 the qualifying day alone builds the curve.
        assertNotNull(IntradayVolumeCurve.build(List.of(linearDay(1_000_000), partial), 1));
    }

    @Test
    void thinHistoryReturnsNull() {
        assertNull(IntradayVolumeCurve.build(List.of(linearDay(1_000_000)), 2));
        assertNull(IntradayVolumeCurve.build(List.of(), 1));
    }

    @Test
    void clampsOutsideSession() {
        var curve = IntradayVolumeCurve.build(List.of(linearDay(1_000_000), linearDay(1_000_000)), 2);
        assertEquals(0.0, curve.shareAt(-100), 1e-9);
        assertEquals(1.0, curve.shareAt(SESSION_SECONDS + 999), 1e-9);
    }

    @Test
    void zeroFinalVolumeDaysAreSkipped() {
        List<Point> dead = List.of(new Point(0, 0), new Point(SESSION_SECONDS, 0));
        assertNull(IntradayVolumeCurve.build(List.of(dead, dead), 2));
    }
}
