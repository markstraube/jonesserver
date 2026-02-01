package com.straube.jones.trader.filter;


/**
 * Beschreibt den Zustand des Gesamtmarktes (z. B. DAX oder S&P 500). Swing-Trading funktioniert besser, wenn
 * der Gesamtmarkt steigt.
 */
public class MarketTrendFilter
{

    /**
     * Aktueller Schlusskurs des Marktindex. Quelle: Börse
     */
    private double indexClose;

    /**
     * 200-Tage-Durchschnitt des Index. Bedeutung: Langfristiger Trendindikator. Berechnung: Durchschnitt der
     * letzten 200 Schlusskurse
     */
    private double sma200;

    /**
     * 50-Tage-Durchschnitt des Index. Bedeutung: Mittelfristiger Trendindikator.
     */
    private double sma50;

    /**
     * Zeigt, ob der 50-Tage-Durchschnitt aktuell steigt. Berechnung: sma50_heute > sma50_vor_5_Tagen
     */
    private boolean sma50Rising;
}
