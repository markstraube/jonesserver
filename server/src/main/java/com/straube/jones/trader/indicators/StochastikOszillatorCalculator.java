package com.straube.jones.trader.indicators;


import java.util.List;

import com.straube.jones.trader.dto.DailyPrice;

public class StochastikOszillatorCalculator
{
    public static class StochasticResult
    {
        public double k; // %K (Fast Stochastic)
        public double d; // %D (Signal line / Slow Stochastic)
        public String signal;
        public boolean isOverbought;
        public boolean isOversold;

        @Override
        public String toString()
        {
            return String.format("%%K: %.2f | %%D: %.2f | Signal: %s", k, d, signal);
        }
    }

    /**
     * Berechnet den Stochastik-Oszillator (%K und %D). Standard-Einstellung: %K(14,3) - 14 Perioden für %K, 3
     * Perioden für %D
     * 
     * @param prices Liste der DailyPrices, absteigend sortiert (Index 0 = neuester Preis)
     * @param kPeriod Periode für %K (üblicherweise 14)
     * @param dPeriod Periode für %D SMA (üblicherweise 3)
     * @return StochasticResult oder null bei fehlenden Daten
     */
    public static StochasticResult calculateStochastikOszillator(List<DailyPrice> prices,
                                                                 int kPeriod,
                                                                 int dPeriod)
    {
        // Wir benötigen genug Daten für:
        // - %K für heute (benötigt kPeriod Tage)
        // - %K für die letzten (dPeriod-1) Tage
        // - Also insgesamt: kPeriod + dPeriod - 1
        if (prices == null || prices.size() < kPeriod + dPeriod - 1)
        { return null; }

        // 1. Berechne %K Werte für die letzten dPeriod Tage
        double[] kValues = new double[dPeriod];

        for (int i = 0; i < dPeriod; i++ )
        {
            kValues[i] = calculateK(prices, i, kPeriod);

            // Validierung
            if (Double.isNaN(kValues[i]) || Double.isInfinite(kValues[i]))
            { return null; }
        }

        // 2. Berechne %D (Simple Moving Average der %K Werte)
        double sumK = 0;
        for (double val : kValues)
        {
            sumK += val;
        }
        double currentD = sumK / dPeriod;
        double currentK = kValues[0]; // Der aktuellste %K (Index 0 = neuester)

        // 3. Erstelle Ergebnis
        StochasticResult result = new StochasticResult();
        result.k = currentK;
        result.d = currentD;
        result.isOverbought = currentK > 80;
        result.isOversold = currentK < 20;

        // 4. Interpretiere Signal
        interpretSignal(result, kValues);

        return result;
    }


    /**
     * Überladene Methode mit Standard-Parametern: %K(14), %D(3)
     */
    public static StochasticResult calculateStochastikOszillator(List<DailyPrice> prices)
    {
        return calculateStochastikOszillator(prices, 14, 3);
    }


    /**
     * Berechnet %K für einen bestimmten Zeitpunkt.
     * 
     * @param prices Preisliste
     * @param startIndex Start-Index (0 = neuester)
     * @param period Anzahl der Perioden zurück
     * @return %K Wert
     */
    private static double calculateK(List<DailyPrice> prices, int startIndex, int period)
    {
        double currentClose = prices.get(startIndex).getAdjClose();

        double lowestLow = Double.MAX_VALUE;
        double highestHigh = Double.MIN_VALUE;

        // Finde Highest High und Lowest Low im Zeitraum
        for (int i = startIndex; i < startIndex + period; i++ )
        {
            DailyPrice p = prices.get(i);

            // Verwende die tatsächlichen High/Low Werte
            // Stochastik verwendet traditionell unadjustierte High/Low mit adjustiertem Close
            double low = p.getLow();
            double high = p.getHigh();

            if (low < lowestLow)
            {
                lowestLow = low;
            }
            if (high > highestHigh)
            {
                highestHigh = high;
            }
        }

        // Schutz vor Division durch Null (extrem seltener Fall)
        double range = highestHigh - lowestLow;
        if (range == 0 || range < 0.0001)
        {
            return 50.0; // Neutraler Wert
        }

        // Stochastik-Formel: %K = 100 * (Close - LowestLow) / (HighestHigh - LowestLow)
        return 100.0 * (currentClose - lowestLow) / range;
    }


    /**
     * Interpretiert die Stochastik-Werte und generiert Handelssignale.
     */
    private static void interpretSignal(StochasticResult result, double[] kValues)
    {
        boolean overbought = result.k > 80;
        boolean oversold = result.k < 20;

        // Crossover-Erkennung: Hat %K die %D-Linie gekreuzt?
        // kValues[0] = aktuell, kValues[1] = gestern
        boolean bullishCrossover = false;
        boolean bearishCrossover = false;

        if (kValues.length >= 2)
        {
            // Vorheriger %D (berechnet aus kValues[1] und älteren Werten)
            // Vereinfachte Annahme: %D ändert sich langsam
            double previousK = kValues[1];

            // Bullish: %K kreuzt %D von unten nach oben
            bullishCrossover = (result.k > result.d) && (previousK <= result.d);

            // Bearish: %K kreuzt %D von oben nach unten
            bearishCrossover = (result.k < result.d) && (previousK >= result.d);
        }

        // Signal-Generierung
        if (oversold && bullishCrossover)
        {
            result.signal = "STARKES KAUFSIGNAL - Überverkauft & Bullish Crossover";
        }
        else if (oversold && result.k > result.d)
        {
            result.signal = "KAUFSIGNAL - Überverkauft & %K über %D";
        }
        else if (overbought && bearishCrossover)
        {
            result.signal = "STARKES VERKAUFSSIGNAL - Überkauft & Bearish Crossover";
        }
        else if (overbought && result.k < result.d)
        {
            result.signal = "VERKAUFSSIGNAL - Überkauft & %K unter %D";
        }
        else if (overbought)
        {
            result.signal = "WARNUNG - Überkauft (>80)";
        }
        else if (oversold)
        {
            result.signal = "WARNUNG - Überverkauft (<20)";
        }
        else if (bullishCrossover)
        {
            result.signal = "BULLISH - %K kreuzt %D nach oben";
        }
        else if (bearishCrossover)
        {
            result.signal = "BEARISH - %K kreuzt %D nach unten";
        }
        else if (result.k > result.d)
        {
            result.signal = "NEUTRAL - Leicht bullish (%K > %D)";
        }
        else
        {
            result.signal = "NEUTRAL - Leicht bearish (%K < %D)";
        }
    }
}
