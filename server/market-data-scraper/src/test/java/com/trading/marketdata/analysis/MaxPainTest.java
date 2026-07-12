package com.trading.marketdata.analysis;

import com.trading.marketdata.model.OptionsData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MaxPainTest {

    private static OptionsData.OiLevel l(String expiry, double strike, long callOi, long putOi) {
        return new OptionsData.OiLevel(expiry, strike, callOi, putOi);
    }

    @Test
    void minimizesTotalIntrinsicPayout() {
        // Heavy call OI at 100, heavy put OI at 120: settling low hurts puts, settling high
        // hurts calls; 110 minimizes (payouts: @100→20k puts, @110→10k+10k? compute:
        // S=100: call 0, put (120-100)*1000=20000; S=110: call (110-100)*1000=10000, put 10000 → 20000;
        // S=120: call 20000, put 0 → 20000 — tie! Add mid OI to break: put 500 @ 110.
        var profile = List.of(
                l("20301218", 100, 1000, 0),
                l("20301218", 110, 0, 500),
                l("20301218", 120, 0, 1000));
        // S=100: put110 5000 + put120 20000 = 25000; S=110: call100 10000 + put120 10000 = 20000;
        // S=120: call100 20000 + put110 5000 = 25000 → min at 110
        assertEquals(110.0, MaxPain.nearestExpiry(profile));
    }

    @Test
    void usesOnlyNearestExpiryBoard() {
        var profile = List.of(
                l("20301218", 100, 1000, 1000),          // nearest board
                l("20310115", 500, 999_999, 0));          // far board must not distort
        assertEquals(100.0, MaxPain.nearestExpiry(profile));
    }

    @Test
    void tieBreaksToLowerStrike() {
        var profile = List.of(
                l("20301218", 100, 1000, 1000),
                l("20301218", 110, 1000, 1000)); // symmetric → payout tie
        assertEquals(100.0, MaxPain.nearestExpiry(profile));
    }

    @Test
    void emptyAndNullSafe() {
        assertNull(MaxPain.nearestExpiry(null));
        assertNull(MaxPain.nearestExpiry(List.of()));
    }
}
