package com.straube.jones.trader.service;

import java.util.List;
import com.straube.jones.trader.dto.DailyPrice;

/**
 * Ermittelt einfache Unterstützungsniveaus.
 */
public class SupportDetectionService {

    /**
     * Ermittelt das letzte markante Tief als Unterstützung.
     *
     * Vereinfachung:
     * Tiefster Kurs der letzten N Tage.
     */
    public double findRecentSupport(List<DailyPrice> prices, int lookbackDays) {
        return prices.stream()
                .limit(lookbackDays)
                .mapToDouble(DailyPrice::getLow)
                .min()
                .orElseThrow();
    }

    /**
     * Berechnet den Abstand zum Support in Prozent.
     */
    public double distanceToSupportPercent(double close, double support) {
        return (close - support) / support * 100;
    }
}
