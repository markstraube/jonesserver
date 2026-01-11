package com.straube.jones.trader.indicators;


import java.util.List;

import com.straube.jones.trader.dto.DailyPrice;

public class ROCcalculator
{

    /**
     * Berechnet den Rate of Change (ROC) Indikator.
     * Interpretation des ROC
     * ROC > 0: Preis ist gestiegen (positives Momentum)
     * ROC < 0: Preis ist gefallen (negatives Momentum)
     * ROC nahe 0: Kaum Veränderung
     * Für eine Momentumstrategie typischerweise Aktien mit hohen positiven ROC-Werten (z.B. ROC > 10% oder im oberen Quartil aller betrachteten Aktien).
     * Typische Perioden: 12 Tage (kurzfristig), 25 Tage (mittelfristig) oder 200 Tage (langfristig) - je nach Ihrer Strategie.
     * 
     * @param prices Liste der DailyPrices, absteigend sortiert (Index 0 = neuester Preis)
     * @param period Der Zeitraum für den Vergleich (n-Perioden)
     * @return Der ROC-Wert oder null, wenn nicht genügend Daten vorhanden sind.
     */
    public static Double calculateROC(List<DailyPrice> prices, int period)
    {
        // Wir benötigen mindestens den aktuellen Preis (Index 0) und den Preis vor 'period' Tagen (Index period)
        // Daher muss die Größe der Liste mindestens period + 1 sein.
        if (prices == null || prices.size() < period + 1)
        { return null; }

        DailyPrice currentPriceData = prices.get(0);
        DailyPrice pastPriceData = prices.get(period);

        double currentPrice = currentPriceData.getAdjClose();
        double pastPrice = pastPriceData.getAdjClose();

        // Division durch Null vermeiden
        if (pastPrice == 0)
        { return 0.0; }

        // ROC Formel: ((Price(t) - Price(t-n)) / Price(t-n)) * 100
        return ((currentPrice - pastPrice) / pastPrice) * 100.0;
    }
}
