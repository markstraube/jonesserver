package com.trading.marketdata.analysis;

import com.trading.marketdata.model.OptionsData;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Max pain over the SCANNED strike window: the candidate settlement strike minimizing the
 * total intrinsic payout to option holders, sum over strikes K of
 * callOI(K)*max(S-K,0) + putOI(K)*max(K-S,0), evaluated per expiry.
 *
 * Scope honesty: the scan covers the near-the-money window, not the entire chain — deep
 * wings shift true max pain and are invisible here. For the purpose this field serves
 * (locating the intraday pinning zone around spot on expiry-heavy days) the window is
 * where virtually all of the payout GRADIENT lives; treat the value as a pinning
 * reference, not as the exchange-wide max-pain print. The full-chain figure, when a
 * fallback source provides it, lives in OptionsData.maxPain — deliberately a separate
 * field.
 */
public final class MaxPain {

    private MaxPain() {}

    /**
     * Max pain of the NEAREST expiry present in the profile (the pinning-relevant board),
     * or null when the profile is empty. Candidate settlement values are the scanned
     * strikes themselves — the payout function is piecewise linear, its minimum sits on a
     * strike.
     */
    public static Double nearestExpiry(List<OptionsData.OiLevel> oiProfile) {
        if (oiProfile == null || oiProfile.isEmpty()) return null;
        String nearest = oiProfile.stream()
                .map(OptionsData.OiLevel::expiry)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder()) // yyyyMMdd sorts chronologically as text
                .orElse(null);
        if (nearest == null) return null;
        List<OptionsData.OiLevel> board = oiProfile.stream()
                .filter(l -> nearest.equals(l.expiry()) && l.strike() != null)
                .toList();
        if (board.isEmpty()) return null;

        Double bestStrike = null;
        double bestPayout = Double.MAX_VALUE;
        for (OptionsData.OiLevel candidate : board) {
            double s = candidate.strike();
            double payout = 0;
            for (OptionsData.OiLevel l : board) {
                double k = l.strike();
                long callOi = l.callOpenInterest() == null ? 0 : l.callOpenInterest();
                long putOi = l.putOpenInterest() == null ? 0 : l.putOpenInterest();
                payout += callOi * Math.max(s - k, 0) + putOi * Math.max(k - s, 0);
            }
            // Strict < : on a payout tie the LOWER strike wins deterministically (candidates
            // arrive in scan order, which is not guaranteed sorted).
            if (payout < bestPayout || (payout == bestPayout && bestStrike != null && s < bestStrike)) {
                bestPayout = payout;
                bestStrike = s;
            }
        }
        return bestStrike;
    }
}
