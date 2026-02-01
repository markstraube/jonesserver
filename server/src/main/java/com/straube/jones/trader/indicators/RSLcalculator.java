package com.straube.jones.trader.indicators;


import java.util.List;

import com.straube.jones.trader.dto.DailyPrice;

/**
 * Berechnet die Relative Stärke nach Levy (RSL) im Vergleich zu einem Benchmark.
 **/
public class RSLcalculator
{
    /**
     * Relative Stärke nach Levy (RSL) Indikator. period: 6 Monate (ca. 126 Handelstage): Standard in vielen
     * Momentum-Studien 12 Monate (ca. 252 Handelstage): Für längerfristiges Momentum 3 Monate (ca. 63
     * Handelstage): Für kurzfristigeres Momentum Benchmark-Vergleich: benchmarkPrices Für deutsche Aktien:
     * DAX oder HDAX Für US-Aktien: S&P 500 Für globale Portfolios: MSCI World Beispiel: Aktien auswählen mit
     * hohem Momentum if (rsl > 1.05 && // 5% Outperformance vs. Benchmark adx > 25 && // Starker Trend roc >
     * 10) // Positive kurzfristige Entwicklung { // Kaufsignal }
     *
     * @param stockPrices Liste der Aktienpreise, absteigend sortiert
     * @param benchmarkPrices Liste der Benchmark-Preise, absteigend sortiert
     * @param period Der Vergleichszeitraum
     * @return Der RSL-Wert oder null
     */
    public static Double calculateRSLevy(List<DailyPrice> stockPrices,
                                         List<DailyPrice> benchmarkPrices,
                                         int period)
    {
        if (stockPrices == null || benchmarkPrices == null
                        || stockPrices.size() < period + 1
                        || benchmarkPrices.size() < period + 1)
        { return null; }

        double currentStock = stockPrices.get(0).getAdjClose();
        double pastStock = stockPrices.get(period).getAdjClose();
        double currentBenchmark = benchmarkPrices.get(0).getAdjClose();
        double pastBenchmark = benchmarkPrices.get(period).getAdjClose();

        if (pastStock == 0 || pastBenchmark == 0 || currentBenchmark == 0)
        { return null; }

        // Relative Performance: (Aktien-Performance) / (Benchmark-Performance)
        double stockPerformance = currentStock / pastStock;
        double benchmarkPerformance = currentBenchmark / pastBenchmark;

        return stockPerformance / benchmarkPerformance;
    }
}
