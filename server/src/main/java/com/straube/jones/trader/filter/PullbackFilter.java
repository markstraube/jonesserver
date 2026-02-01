package com.straube.jones.trader.filter;


/**
 * Zeigt, ob sich die Aktie gerade in einem gesunden Rücksetzer befindet.
 */
public class PullbackFilter
{

    /**
     * 20-Tage-Exponentieller Durchschnitt. Bedeutung: Reagiert schneller auf Kursänderungen als SMA.
     */
    private double ema20;

    /**
     * Prozentualer Abstand des aktuellen Kurses zur EMA20. Berechnung: (Close - EMA20) / EMA20 × 100 Negative
     * Werte = Kurs unter EMA
     */
    private double distanceToEma20Percent;

    /**
     * Relative-Stärke-Index (RSI). Bedeutung: Zeigt, ob eine Aktie überkauft oder überverkauft ist. Skala:
     * 0–100 Berechnung: Verhältnis durchschnittlicher Aufwärts- zu Abwärtsbewegungen über 14 Tage
     */
    private double rsi14;

    public double getEma20()
    {
        return ema20;
    }


    public void setEma20(double ema20)
    {
        this.ema20 = ema20;
    }


    public double getDistanceToEma20Percent()
    {
        return distanceToEma20Percent;
    }


    public void setDistanceToEma20Percent(double distanceToEma20Percent)
    {
        this.distanceToEma20Percent = distanceToEma20Percent;
    }


    public double getRsi14()
    {
        return rsi14;
    }


    public void setRsi14(double rsi14)
    {
        this.rsi14 = rsi14;
    }
}
