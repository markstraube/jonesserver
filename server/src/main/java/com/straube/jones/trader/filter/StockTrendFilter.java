package com.straube.jones.trader.filter;

/**
 * Beschreibt, ob sich eine Aktie in einem stabilen Aufwärtstrend befindet.
 */
public class StockTrendFilter {

    /**
     * Aktueller Schlusskurs der Aktie.
     * Quelle: Börse
     */
    private double closePrice;

    /**
     * 50-Tage-Durchschnitt.
     * Wird häufig als Trendlinie genutzt.
     *
     * Berechnung:
     * Durchschnitt der letzten 50 Schlusskurse
     */
    private double sma50;

    /**
     * 200-Tage-Durchschnitt.
     * Langfristige Trendlinie.
     */
    private double sma200;

    /**
     * Gibt an, ob der mittelfristige Trend steigt.
     *
     * Berechnung:
     * sma50_heute > sma50_vor_5_Tagen
     */
    private boolean sma50Rising;

    /**
     * Letztes markantes Hoch im Kursverlauf.
     *
     * Quelle:
     * Aus Kursdaten abgeleitet (Swing-High)
     */
    private double lastSwingHigh;

    /**
     * Vorheriges markantes Hoch.
     */
    private double previousSwingHigh;

    /**
     * Letztes markantes Tief.
     */
    private double lastSwingLow;

    /**
     * Vorheriges markantes Tief.
     */
    private double previousSwingLow;
    public double getClosePrice() { return closePrice; }
    public void setClosePrice(double closePrice) { this.closePrice = closePrice; }
    public double getSma50() { return sma50; }
    public void setSma50(double sma50) { this.sma50 = sma50; }
    public double getSma200() { return sma200; }
    public void setSma200(double sma200) { this.sma200 = sma200; }
    public boolean isSma50Rising() { return sma50Rising; }
    public void setSma50Rising(boolean sma50Rising) { this.sma50Rising = sma50Rising; }
    public double getLastSwingHigh() { return lastSwingHigh; }
    public void setLastSwingHigh(double lastSwingHigh) { this.lastSwingHigh = lastSwingHigh; }
    public double getPreviousSwingHigh() { return previousSwingHigh; }
    public void setPreviousSwingHigh(double previousSwingHigh) { this.previousSwingHigh = previousSwingHigh; }
    
    public boolean isUptrend() {
        return closePrice > sma50 && sma50 > sma200;
    }}
