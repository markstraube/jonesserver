package com.trading.marketdata.analysis;

import com.trading.marketdata.model.OptionsData;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Dealer gamma exposure (GEX) over the SCANNED strike window, from per-contract model
 * gammas (tickOptionComputation) and open interest.
 *
 * ============================ ASSUMPTIONS — READ FIRST ============================
 * GEX is a THESIS about positioning, not a measurement. Every number below rests on:
 *
 * 1. DEALER-POSITIONING CONVENTION: customers net-BUY puts (hedging) and net-SELL calls
 *    (overwriting), so dealers are net LONG calls and net SHORT puts. Hence call OI
 *    contributes POSITIVE dealer gamma, put OI NEGATIVE. This is the standard published
 *    convention; it is wrong exactly where UA flow says it is (a day of aggressive
 *    customer call BUYING makes dealers SHORT those calls — cross-check positionInference
 *    before trusting the sign).
 * 2. OI IS YESTERDAY: struck once daily by the OCC. Today's 0DTE flow is invisible here —
 *    on expiry days the true intraday gamma landscape can differ wildly.
 * 3. SCANNED WINDOW ONLY: near-the-money strikes, where the gamma actually lives (gamma
 *    decays quickly away from spot) — the wings' contribution is small but not zero.
 * 4. GAMMAS AT CURRENT SPOT: the flip level below is where the per-strike GEX (evaluated
 *    at TODAY's spot) cumulatively crosses zero — the practical scanned-window
 *    approximation of the "gamma flip", not a full re-pricing of the surface at
 *    hypothetical spots.
 *
 * Units: gexUsdPer1PctMove = gamma × OI × 100 (multiplier) × spot × (spot × 0.01) — the
 * dollar delta-hedging flow a 1% spot move forces on dealers, per strike, signed
 * (positive = dealers BUY dips / SELL rips there → dampening; negative = they chase →
 * amplifying).
 * ==================================================================================
 */
public final class DealerGamma {

    private DealerGamma() {}

    /**
     * @param netGexUsdPer1Pct   sum of signed per-strike GEX — the regime number: positive =
     *                           dampening/pinning environment, negative = amplifying
     * @param flipLevel          strike level where cumulative GEX (ascending strikes) crosses
     *                           zero, linearly interpolated; null when it never crosses (the
     *                           whole window is one regime)
     * @param wallStrike         strike with the largest ABSOLUTE GEX — the magnet/pivot
     * @param wallGexUsdPer1Pct  its signed GEX
     * @param gammaCoverage      OI-weighted share of the window's open interest that carried a
     *                           model gamma — the honesty metric: 0.4 means 60% of the OI had
     *                           no greek tick and is invisible to every number here
     * @param wallCoverage       the SAME honesty metric restricted to the wall strike itself.
     *                           A global coverage of 0.64 can hide a wall whose dominant side
     *                           is entirely invisible — observed live: a 900 strike shown as
     *                           +10M dealer-long GEX from 3.8k covered calls while its 14.7k
     *                           puts (the window's largest single position) carried no gamma;
     *                           with them visible the strike was deeply NEGATIVE. Gate rule
     *                           for consumers: a wall with low wallCoverage names the right
     *                           LEVEL (the OI is real) but its GEX sign/magnitude is not
     *                           trustworthy
     * @param signModel          the positioning convention every sign here rests on — in the
     *                           report itself, not only in this javadoc, so the downstream
     *                           consumer cannot read GEX as a measurement
     * @param observedDealerPositions always false: no number in this profile comes from
     *                           observed dealer inventory; it is OI x model gamma under the
     *                           convention above
     * @param confidence         coverage-derived, deliberately capped at MEDIUM — a modeled
     *                           quantity under an unverifiable positioning assumption never
     *                           earns HIGH. MEDIUM requires BOTH coverages at/above
     *                           {@link #CONFIDENCE_COVERAGE_FLOOR} (the live 900-strike case
     *                           had global 0.64 but wall 0.21 — LOW, correctly)
     */
    public record Profile(
            Double netGexUsdPer1Pct,
            Double flipLevel,
            Double wallStrike,
            Double wallGexUsdPer1Pct,
            Double gammaCoverage,
            Double wallCoverage,
            String signModel,
            Boolean observedDealerPositions,
            String confidence
    ) {}

    /** The convention behind every sign in this profile (assumption 1 above). */
    public static final String SIGN_MODEL = "CUSTOMERS_LONG_PUTS_SHORT_CALLS";

    /** Both coverages must reach this floor for MEDIUM confidence. */
    public static final double CONFIDENCE_COVERAGE_FLOOR = 0.70;

    /** Null when spot is unusable or NO row carries a gamma — no pretend profiles. */
    public static Profile compute(List<OptionsData.OiLevel> oiProfile, Double spot) {
        if (oiProfile == null || oiProfile.isEmpty() || spot == null || spot <= 0) return null;

        // Aggregate per strike across expiry boards: the hedging flow at a price level is
        // the sum over every board pinning there.
        Map<Double, Double> gexByStrike = new TreeMap<>();
        Map<Double, long[]> oiByStrike = new TreeMap<>(); // [coveredOi, totalOi] per strike
        long coveredOi = 0, totalOi = 0;
        double contractUsdPer1Pct = 100 * spot * (spot * 0.01);

        for (OptionsData.OiLevel level : oiProfile) {
            if (level.strike() == null) continue;
            long callOi = level.callOpenInterest() == null ? 0 : level.callOpenInterest();
            long putOi = level.putOpenInterest() == null ? 0 : level.putOpenInterest();
            totalOi += callOi + putOi;
            long[] strikeOi = oiByStrike.computeIfAbsent(level.strike(), k -> new long[2]);
            strikeOi[1] += callOi + putOi;

            double gex = 0;
            boolean any = false;
            if (level.callGamma() != null && callOi > 0) {
                gex += level.callGamma() * callOi * contractUsdPer1Pct;  // dealers long calls
                coveredOi += callOi;
                strikeOi[0] += callOi;
                any = true;
            }
            if (level.putGamma() != null && putOi > 0) {
                gex -= level.putGamma() * putOi * contractUsdPer1Pct;    // dealers short puts
                coveredOi += putOi;
                strikeOi[0] += putOi;
                any = true;
            }
            if (any) {
                gexByStrike.merge(level.strike(), gex, Double::sum);
            }
        }
        if (gexByStrike.isEmpty() || totalOi <= 0) return null;

        double net = 0, wallGex = 0;
        Double wallStrike = null;
        for (Map.Entry<Double, Double> e : gexByStrike.entrySet()) {
            net += e.getValue();
            if (Math.abs(e.getValue()) > Math.abs(wallGex)) {
                wallGex = e.getValue();
                wallStrike = e.getKey();
            }
        }

        // Flip level: walk strikes ascending, cumulative GEX; the zero crossing between two
        // strikes is interpolated linearly. Crossing AT a strike returns that strike.
        Double flip = null;
        double cum = 0;
        Double prevStrike = null;
        double prevCum = 0;
        for (Map.Entry<Double, Double> e : gexByStrike.entrySet()) {
            prevCum = cum;
            cum += e.getValue();
            if (prevStrike != null && prevCum != 0 && Math.signum(prevCum) != Math.signum(cum) && cum != 0) {
                double fraction = Math.abs(prevCum) / (Math.abs(prevCum) + Math.abs(cum));
                flip = prevStrike + (e.getKey() - prevStrike) * fraction;
                break;
            }
            if (cum == 0) { // crossed exactly onto zero at this strike
                flip = e.getKey();
                break;
            }
            prevStrike = e.getKey();
        }

        Double wallCoverage = null;
        if (wallStrike != null) {
            long[] atWall = oiByStrike.get(wallStrike);
            if (atWall != null && atWall[1] > 0) {
                wallCoverage = (double) atWall[0] / atWall[1];
            }
        }

        double globalCoverage = (double) coveredOi / totalOi;
        String confidence = (globalCoverage >= CONFIDENCE_COVERAGE_FLOOR
                && wallCoverage != null && wallCoverage >= CONFIDENCE_COVERAGE_FLOOR)
                ? "MEDIUM" : "LOW";

        return new Profile(net, flip, wallStrike, wallGex, globalCoverage, wallCoverage,
                SIGN_MODEL, false, confidence);
    }
}
