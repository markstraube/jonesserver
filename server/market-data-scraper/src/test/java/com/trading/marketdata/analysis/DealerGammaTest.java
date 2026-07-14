package com.trading.marketdata.analysis;

import com.trading.marketdata.model.OptionsData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DealerGammaTest {

    private static OptionsData.OiLevel l(double strike, long callOi, long putOi,
                                         Double callGamma, Double putGamma) {
        return new OptionsData.OiLevel("20301218", strike, callOi, putOi, callGamma, putGamma);
    }

    private static final double SPOT = 100.0;
    /** gamma × OI × 100 × spot × spot×0.01 — at spot 100: gamma × OI × 10_000 */
    private static final double UNIT = 100 * SPOT * (SPOT * 0.01);

    @Test
    void signConventionCallsPositivePutsNegative() {
        var p = DealerGamma.compute(List.of(
                l(100, 1000, 0, 0.05, null),   // dealers long calls → +
                l(110, 0, 1000, null, 0.05)),  // dealers short puts → −
                SPOT);
        assertEquals(0.05 * 1000 * UNIT, p.wallGexUsdPer1Pct(), 1e-6); // wall = larger |gex| → tie? equal magnitudes
        assertEquals(0.0, p.netGexUsdPer1Pct(), 1e-6);                  // symmetric → net zero
        assertEquals(1.0, p.gammaCoverage(), 1e-9);
    }

    @Test
    void flipLevelInterpolatesBetweenStrikes() {
        // Cumulative: +30k at 100, then −60k at 110 → crossing between; prevCum=+30k,
        // cum=−30k → fraction 0.5 → flip at 105.
        var p = DealerGamma.compute(List.of(
                l(100, 600, 0, 0.05, null),
                l(110, 0, 1200, null, 0.05)),
                SPOT);
        assertEquals(105.0, p.flipLevel(), 1e-6);
    }

    @Test
    void noCrossingMeansNullFlip() {
        var p = DealerGamma.compute(List.of(
                l(100, 500, 0, 0.05, null),
                l(110, 500, 0, 0.04, null)),
                SPOT);
        assertNull(p.flipLevel());
        assertTrue(p.netGexUsdPer1Pct() > 0);
    }

    @Test
    void coverageIsOiWeightedAndRowsWithoutGammaInvisible() {
        var p = DealerGamma.compute(List.of(
                l(100, 1000, 0, 0.05, null),  // covered: 1000 OI
                l(110, 3000, 0, null, null)), // no gamma: 3000 OI invisible
                SPOT);
        assertEquals(0.25, p.gammaCoverage(), 1e-9);
        assertEquals(0.05 * 1000 * UNIT, p.netGexUsdPer1Pct(), 1e-6); // uncovered row contributes 0
    }

    @Test
    void wallIsLargestAbsoluteGex() {
        var p = DealerGamma.compute(List.of(
                l(100, 200, 0, 0.05, null),    // +100k-ish
                l(110, 0, 5000, null, 0.06)),  // −3M — the wall despite being negative
                SPOT);
        assertEquals(110.0, p.wallStrike(), 1e-9);
        assertTrue(p.wallGexUsdPer1Pct() < 0);
    }

    @Test
    void nullSafety() {
        assertNull(DealerGamma.compute(null, SPOT));
        assertNull(DealerGamma.compute(List.of(), SPOT));
        assertNull(DealerGamma.compute(List.of(l(100, 1000, 0, 0.05, null)), null));
        assertNull(DealerGamma.compute(List.of(l(100, 1000, 0, 0.05, null)), 0.0));
        // Rows exist but none carries a gamma → null, no pretend profile
        assertNull(DealerGamma.compute(List.of(l(100, 1000, 1000, null, null)), SPOT));
    }

    @Test
    void wallCoverageExposesConcentratedGaps() {
        // The live case: a strike whose dominant side has no gamma. 3.8k covered calls show
        // +GEX; 14.7k puts are invisible. Global coverage looks acceptable, wallCoverage
        // does not — the gate the consumer needs.
        var p = DealerGamma.compute(List.of(
                l(900, 3790, 14729, 0.004, null),   // wall: only 3790 of 18519 covered
                l(930, 2000, 2000, 0.004, 0.004)),  // fully covered strike
                SPOT);
        assertEquals(900.0, p.wallStrike(), 1e-9);
        assertEquals(3790.0 / 18519.0, p.wallCoverage(), 1e-9);
        assertTrue(p.gammaCoverage() > p.wallCoverage(), "global coverage hides the wall gap");
    }

    @Test
    void wallCoverageIsOneWhenWallFullyCovered() {
        // Asymmetric OI at 100 — a SYMMETRIC fully-covered strike nets to zero GEX (calls
        // and puts cancel exactly) and loses the wall to any nonzero neighbor, which is
        // correct wall semantics: the wall is the largest hedging-flow imbalance, not the
        // largest OI pile.
        var p = DealerGamma.compute(List.of(
                l(100, 1000, 500, 0.05, 0.05),
                l(110, 10, 0, 0.01, null)),
                SPOT);
        assertEquals(100.0, p.wallStrike(), 1e-9);
        assertEquals(1.0, p.wallCoverage(), 1e-9);
    }
}
