package com.straube.jones.trader.filter;


/**
 * Basisdaten einer Aktie, um überhaupt handelbar zu sein. Diese Daten kommen überwiegend direkt von der Börse
 * oder einem Marktdatenanbieter.
 */
public class LiquidityFilter
{

    /**
     * Aktueller Aktienkurs in Euro. Quelle: Börse / Marktdaten-API
     */
    private double lastPrice;

    /**
     * Durchschnittlich gehandelte Stückzahl pro Tag über die letzten 20 Handelstage. Aussage: Zeigt, wie
     * liquide eine Aktie ist. Hohe Werte bedeuten: leicht kauf- und verkaufbar. Berechnung:
     * Summe(Tagesvolumen der letzten 20 Tage) / 20 Quelle: Tagesvolumen von der Börse
     */
    private long averageDailyVolume20d;

    /**
     * Durchschnittlicher Tagesumsatz in Euro (Preis × Volumen). Berechnung: Tagesumsatz = Schlusskurs ×
     * Tagesvolumen Durchschnitt über 20 Tage Quelle: Kurs + Volumen von der Börse
     */
    private double averageDailyTurnover20d;

    /**
     * Marktkapitalisierung des Unternehmens in Euro. Bedeutung: Grobe Größe und Stabilität des Unternehmens.
     * Berechnung: Aktienkurs × Anzahl ausstehender Aktien Quelle: Börse / Unternehmensdaten
     */
    private double marketCapitalization;

    public double getLastPrice()
    {
        return lastPrice;
    }


    public void setLastPrice(double lastPrice)
    {
        this.lastPrice = lastPrice;
    }


    public long getAverageDailyVolume20d()
    {
        return averageDailyVolume20d;
    }


    public void setAverageDailyVolume20d(long averageDailyVolume20d)
    {
        this.averageDailyVolume20d = averageDailyVolume20d;
    }


    public double getAverageDailyTurnover20d()
    {
        return averageDailyTurnover20d;
    }


    public void setAverageDailyTurnover20d(double averageDailyTurnover20d)
    {
        this.averageDailyTurnover20d = averageDailyTurnover20d;
    }


    public double getMarketCapitalization()
    {
        return marketCapitalization;
    }


    public void setMarketCapitalization(double marketCapitalization)
    {
        this.marketCapitalization = marketCapitalization;
    }
}
