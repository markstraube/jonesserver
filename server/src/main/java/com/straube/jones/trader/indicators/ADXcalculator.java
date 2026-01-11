package com.straube.jones.trader.indicators;


import java.util.ArrayList;
import java.util.List;

import com.straube.jones.trader.dto.DailyPrice;

public class ADXcalculator
{
    /**
     * Berechnet den Average Directional Index (ADX) Indikator.
     * Interpretation: Der ADX-Wert wird automatisch interpretiert:
     *  < 20: Schwacher/kein Trend
     *  20-25: Beginnender Trend
     *  25-50: Starker Trend
     *  50: Sehr starker Trend
     * 
     * @param prices Liste der DailyPrices, absteigend sortiert (Index 0 = neuester Preis)
     * @param period Der Zeitraum für die ADX-Berechnung (typischerweise 14)
     * @return Ein ADXResult-Objekt mit ADX, +DI, -DI und Trendbewertung
     */
    public static ADXResult calculateADX(List<DailyPrice> prices, int period)
    {
        ADXResult result = new ADXResult();

        // Mindestens period*2 + 1 Tage benötigt für aussagekräftigen ADX
        if (prices == null || prices.size() < period * 2 + 1)
        {
            result.adx = 0.0;
            result.plusDI = 0.0;
            result.minusDI = 0.0;
            result.trendStrength = "Nicht genug Daten";
            return result;
        }

        // Listen für TR, +DM und -DM
        List<Double> trueRanges = new ArrayList<>();
        List<Double> plusDM = new ArrayList<>();
        List<Double> minusDM = new ArrayList<>();

        // Berechne TR, +DM und -DM
        // Index 0 ist neuester Preis, also vergleichen wir i mit i+1 (älter)
        for (int i = 0; i < prices.size() - 1; i++ )
        {
            DailyPrice current = prices.get(i);
            DailyPrice previous = prices.get(i + 1);

            // True Range
            double high = current.getHigh();
            double low = current.getLow();
            double prevClose = previous.getAdjClose();

            double tr = Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
            trueRanges.add(tr);

            // Directional Movement
            double highDiff = current.getHigh() - previous.getHigh();
            double lowDiff = previous.getLow() - current.getLow();

            double plusDMValue = 0;
            double minusDMValue = 0;

            if (highDiff > lowDiff && highDiff > 0)
            {
                plusDMValue = highDiff;
            }
            if (lowDiff > highDiff && lowDiff > 0)
            {
                minusDMValue = lowDiff;
            }

            plusDM.add(plusDMValue);
            minusDM.add(minusDMValue);
        }

        // Erste Glättung (Summe der ersten period Werte)
        double smoothedTR = 0;
        double smoothedPlusDM = 0;
        double smoothedMinusDM = 0;

        for (int i = 0; i < period; i++ )
        {
            smoothedTR += trueRanges.get(i);
            smoothedPlusDM += plusDM.get(i);
            smoothedMinusDM += minusDM.get(i);
        }

        // Liste für DX-Werte
        List<Double> dxValues = new ArrayList<>();

        // Berechne geglättete Werte und DX für alle weiteren Perioden
        for (int i = period; i < trueRanges.size(); i++ )
        {
            // Wilder's Smoothing: Smoothed = Previous - (Previous/period) + Current
            smoothedTR = smoothedTR - (smoothedTR / period) + trueRanges.get(i);
            smoothedPlusDM = smoothedPlusDM - (smoothedPlusDM / period) + plusDM.get(i);
            smoothedMinusDM = smoothedMinusDM - (smoothedMinusDM / period) + minusDM.get(i);

            // Berechne +DI und -DI
            double plusDI = 0;
            double minusDI = 0;

            if (smoothedTR != 0)
            {
                plusDI = (smoothedPlusDM / smoothedTR) * 100;
                minusDI = (smoothedMinusDM / smoothedTR) * 100;
            }

            // Berechne DX
            double diSum = plusDI + minusDI;
            double dx = 0;

            if (diSum != 0)
            {
                dx = (Math.abs(plusDI - minusDI) / diSum) * 100;
            }

            dxValues.add(dx);
        }

        // Berechne ADX (geglätteter Durchschnitt der DX-Werte)
        if (dxValues.size() < period)
        {
            result.adx = 0.0;
            result.plusDI = 0.0;
            result.minusDI = 0.0;
            result.trendStrength = "Nicht genug Daten für ADX";
            return result;
        }

        // Erste ADX-Berechnung (Durchschnitt der ersten period DX-Werte)
        double adx = 0;
        for (int i = 0; i < period; i++ )
        {
            adx += dxValues.get(i);
        }
        adx = adx / period;

        // Weitere Glättung des ADX für alle verbleibenden Werte
        for (int i = period; i < dxValues.size(); i++ )
        {
            adx = ((adx * (period - 1)) + dxValues.get(i)) / period;
        }

        // Berechne finale +DI und -DI (letzte Werte)
        double finalPlusDI = 0;
        double finalMinusDI = 0;

        if (smoothedTR != 0)
        {
            finalPlusDI = (smoothedPlusDM / smoothedTR) * 100;
            finalMinusDI = (smoothedMinusDM / smoothedTR) * 100;
        }

        // Setze Ergebnisse
        result.adx = adx;
        result.plusDI = finalPlusDI;
        result.minusDI = finalMinusDI;

        // Interpretiere ADX-Wert
        if (adx < 20)
        {
            result.trendStrength = "Schwacher oder kein Trend";
        }
        else if (adx < 25)
        {
            result.trendStrength = "Beginnender Trend";
        }
        else if (adx < 50)
        {
            result.trendStrength = "Starker Trend";
        }
        else
        {
            result.trendStrength = "Sehr starker Trend";
        }

        return result;
    }

    // Ergänzende Klasse für das Ergebnis
    class ADXResult
    {
        double adx;
        double plusDI;
        double minusDI;
        String trendStrength;

        @Override
        public String toString()
        {
            return String.format("ADX: %.2f | +DI: %.2f | -DI: %.2f | Bewertung: %s",
                                 adx,
                                 plusDI,
                                 minusDI,
                                 trendStrength);
        }
    }
}
