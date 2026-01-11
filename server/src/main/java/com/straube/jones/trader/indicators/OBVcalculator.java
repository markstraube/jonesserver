package com.straube.jones.trader.indicators;


import java.util.ArrayList;
import java.util.List;

import com.straube.jones.trader.dto.DailyPrice;

public class OBVcalculator
{
    /**
     * OBV Ergebnis
     */
    public static class OBVResult
    {
        public double obv;
        public double obvEMA; // Geglättetes OBV für Trend
        public String signal;
        public boolean hasDivergence; // Divergenz erkannt?

        @Override
        public String toString()
        {
            return String.format("OBV: %.0f%nOBV-EMA: %.0f%nSignal: %s%nDivergenz: %s",
                                 obv,
                                 obvEMA,
                                 signal,
                                 hasDivergence ? "JA" : "NEIN");
        }
    }

    /**
     * Berechnet On-Balance Volume (OBV)
     * Kombiniert Preis und Volumen zur Trendbestätigung
     * 
     * @param prices Liste der Preise (Index 0 = neuestes Datum, absteigend sortiert)
     * @return OBVResult mit OBV-Wert, EMA und Signalinterpretation
     */
    public static OBVResult calculateOBV(List<DailyPrice> prices)
    {
        OBVResult result = new OBVResult();

        if (prices == null || prices.size() < 20)
        {
            result.signal = "Nicht genug Daten";
            result.hasDivergence = false;
            return result;
        }

        // Berechne OBV von alt nach neu
        List<Double> obvValues = new ArrayList<>();
        double obv = 0; // Startwert bei 0

        // Beginne beim ältesten Preis (höchster Index)
        // Der erste OBV-Wert ist immer 0, da wir keinen Vortag haben
        obvValues.add(obv);

        // Iteriere von alt nach neu (rückwärts durch die Liste)
        for (int i = prices.size() - 2; i >= 0; i-- )
        {
            DailyPrice current = prices.get(i);
            DailyPrice previous = prices.get(i + 1);

            double currentPrice = current.getAdjClose();
            double previousPrice = previous.getAdjClose();
            long volume = current.getVolume();

            if (currentPrice > previousPrice)
            {
                obv += volume;
            }
            else if (currentPrice < previousPrice)
            {
                obv -= volume;
            }
            // Bei gleichem Preis: OBV bleibt unverändert

            obvValues.add(obv);
        }

        // Der letzte Wert in obvValues ist der neueste OBV
        result.obv = obvValues.get(obvValues.size() - 1);

        // Berechne EMA(20) des OBV für Trendanalyse
        if (obvValues.size() >= 20)
        {
            result.obvEMA = calculateEMAFromDoubles(obvValues, 20);
        }
        else
        {
            result.obvEMA = result.obv;
        }

        // Analysiere Divergenzen und Trends
        analyzeDivergenceAndSignal(prices, obvValues, result);

        return result;
    }


    /**
     * Analysiert OBV-Divergenzen und generiert Handelssignale
     */
    private static void analyzeDivergenceAndSignal(List<DailyPrice> prices,
                                                   List<Double> obvValues,
                                                   OBVResult result)
    {
        int lookback = Math.min(20, prices.size() - 1);

        // Preisbewegung der letzten 20 Tage
        double currentPrice = prices.get(0).getAdjClose();
        double pastPrice = prices.get(lookback).getAdjClose();
        double priceChange = ((currentPrice - pastPrice) / pastPrice) * 100;

        // OBV-Bewegung der letzten 20 Tage
        // obvValues ist von alt nach neu, letzter Wert = neuester
        int obvIndex = obvValues.size() - 1;
        int obvPastIndex = obvValues.size() - 1 - lookback;

        double currentOBV = obvValues.get(obvIndex);
        double pastOBV = obvPastIndex >= 0 ? obvValues.get(obvPastIndex) : 0;

        boolean priceUp = priceChange > 2; // Preis um >2% gestiegen
        boolean priceDown = priceChange < -2; // Preis um >2% gefallen
        boolean obvUp = currentOBV > result.obvEMA;
        boolean obvRising = currentOBV > pastOBV;

        // Divergenz-Erkennung
        result.hasDivergence = false;

        // Bullische Divergenz: Preis fällt, aber OBV steigt
        if (priceDown && obvRising)
        {
            result.signal = "BULLISCHE DIVERGENZ - Preis fällt, aber OBV steigt (Potenzielle Trendwende nach oben)";
            result.hasDivergence = true;
        }
        // Bearische Divergenz: Preis steigt, aber OBV fällt
        else if (priceUp && !obvRising && currentOBV < pastOBV)
        {
            result.signal = "BEARISCHE DIVERGENZ - Preis steigt, aber OBV fällt (Schwacher Trend, Vorsicht!)";
            result.hasDivergence = true;
        }
        // Bestätigte Trends
        else if (priceUp && obvUp && obvRising)
        {
            result.signal = "BULLISCH - Preis und OBV steigen gemeinsam (Starker bestätigter Aufwärtstrend)";
        }
        else if (priceDown && !obvUp && !obvRising)
        {
            result.signal = "BEARISCH - Preis und OBV fallen gemeinsam (Bestätigter Abwärtstrend)";
        }
        else if (Math.abs(priceChange) < 2)
        {
            result.signal = "NEUTRAL - Seitwärtsbewegung, kein klarer Trend";
        }
        else
        {
            result.signal = "GEMISCHT - Unklare Signallage";
        }
    }


    /**
     * Hilfsmethode: Berechnet EMA aus einer Liste von Double-Werten
     */
    private static double calculateEMAFromDoubles(List<Double> values, int period)
    {
        if (values.size() < period)
        { return values.get(values.size() - 1); }

        double multiplier = 2.0 / (period + 1);

        // Starte mit SMA der ersten 'period' Werte
        double sum = 0;
        for (int i = 0; i < period; i++ )
        {
            sum += values.get(i);
        }
        double ema = sum / period;

        // Berechne EMA für die restlichen Werte
        for (int i = period; i < values.size(); i++ )
        {
            ema = (values.get(i) - ema) * multiplier + ema;
        }

        return ema;
    }
}
