package com.straube.jones.trader.indicators;


import java.util.List;

import com.straube.jones.trader.dto.DailyPrice;

/**
 * Berechnet den Volume Weighted Moving Average (VWMA).
 *
 * <p>
 * Der VWMA ist ein gleitender Durchschnitt, bei dem jeder Kurs mit dem entsprechenden Handelsvolumen
 * gewichtet wird. Hohe Volumentage haben damit einen stärkeren Einfluss auf den Durchschnitt als
 * volumenarme Tage.
 * </p>
 *
 * <p>
 * Formel: VWMA(n) = Σ(Price_i × Volume_i) / Σ(Volume_i) für i = t-(n-1) bis t
 * </p>
 *
 * <p>
 * Interpretation:
 * <ul>
 * <li>VWMA > SMA: Das Volumen konzentriert sich auf höheren Kursniveaus → bullisches Zeichen</li>
 * <li>VWMA &lt; SMA: Das Volumen konzentriert sich auf niedrigeren Kursniveaus → bearisches Zeichen</li>
 * <li>Kurs kreuzt VWMA von unten: mögliches Kaufsignal</li>
 * <li>Kurs kreuzt VWMA von oben: mögliches Verkaufssignal</li>
 * </ul>
 * </p>
 *
 * <p>
 * Typische Perioden: 5, 10, 20, 30 Tage.
 * </p>
 */
public class VWMAcalculator
{

    /**
     * Berechnet den VWMA für den aktuellsten Datenpunkt (Index 0).
     *
     * @param prices Liste der DailyPrices, absteigend sortiert (Index 0 = neuester Preis)
     * @param period Anzahl der Perioden (z.B. 5, 10, 20, 30)
     * @return VWMA-Wert oder {@code null} wenn nicht genug Daten vorhanden sind oder das Gesamtvolumen 0 ist
     */
    public static Double calculateVWMA(List<DailyPrice> prices, int period)
    {
        if (prices == null || prices.size() < period || period <= 0)
        {
            return null;
        }

        double sumPriceVolume = 0.0;
        long sumVolume = 0;

        for (int i = 0; i < period; i++)
        {
            DailyPrice dp = prices.get(i);
            sumPriceVolume += dp.getAdjClose() * dp.getVolume();
            sumVolume += dp.getVolume();
        }

        if (sumVolume == 0)
        {
            return null;
        }

        return sumPriceVolume / sumVolume;
    }


    /**
     * Berechnet den VWMA für alle Datenpunkte einer aufsteigend sortierten Preisliste (chronologisch).
     * Wird von {@code IndicatorCalculator} intern verwendet.
     *
     * @param prices aufsteigend sortierte Liste (Index 0 = ältester Preis)
     * @param period Anzahl der Perioden
     * @return Array mit VWMA-Werten (null wo nicht genug Daten)
     */
    public static Double[] calculateVWMAArray(List<DailyPrice> prices, int period)
    {
        Double[] result = new Double[prices.size()];

        double sumPriceVolume = 0.0;
        long sumVolume = 0;

        for (int i = 0; i < prices.size(); i++)
        {
            DailyPrice dp = prices.get(i);
            sumPriceVolume += dp.getAdjClose() * dp.getVolume();
            sumVolume += dp.getVolume();

            if (i >= period)
            {
                // Ältesten Wert entfernen
                DailyPrice old = prices.get(i - period);
                sumPriceVolume -= old.getAdjClose() * old.getVolume();
                sumVolume -= old.getVolume();
                result[i] = (sumVolume == 0) ? null : sumPriceVolume / sumVolume;
            }
            else if (i == period - 1)
            {
                result[i] = (sumVolume == 0) ? null : sumPriceVolume / sumVolume;
            }
            else
            {
                result[i] = null;
            }
        }

        return result;
    }
}
