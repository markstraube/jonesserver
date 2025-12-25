package com.straube.jones.trader.service;

import java.util.List;

/**
 * Erkennt Marktstruktur aus Hochs und Tiefs.
 */
public class TrendStructureService {

    /**
     * Vereinfacht:
     * Prüft, ob höhere Hochs und höhere Tiefs vorliegen.
     */
    public boolean isUptrend(
            double lastHigh,
            double previousHigh,
            double lastLow,
            double previousLow
    ) {
        return lastHigh > previousHigh
                && lastLow > previousLow;
    }
}
