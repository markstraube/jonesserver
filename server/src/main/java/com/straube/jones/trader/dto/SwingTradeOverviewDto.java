package com.straube.jones.trader.dto;


import com.straube.jones.trader.dasboard.TradeStatus;

/**
 * Kompakte Darstellung für die Watchlist / Tabellenansicht.
 */
public class SwingTradeOverviewDto
{

    /** Aktiensymbol (z. B. AAPL) */
    private String symbol;

    /** Name des Unternehmens */
    private String companyName;

    /** Aktueller Schlusskurs */
    private double lastPrice;

    /** Gesamtbewertung des Setups */
    private TradeStatus status; // GREEN / YELLOW / RED

    /** Kurztext für Laien (z. B. "Guter Trend, Pullback läuft") */
    private String statusSummary;

    /** RSI-Wert (Momentum) */
    private double rsi;

    /** Abstand zur Unterstützung in Prozent */
    private double distanceToSupportPercent;

    /** Chance-Risiko-Verhältnis */
    private double chanceRiskRatio;

    /** Tage bis zu den nächsten Quartalszahlen */
    private Integer daysUntilEarnings;

    /** Datum der letzten Aktualisierung */
    private String lastUpdated;

    public String getSymbol()
    {
        return symbol;
    }


    public void setSymbol(String symbol)
    {
        this.symbol = symbol;
    }


    public String getCompanyName()
    {
        return companyName;
    }


    public void setCompanyName(String companyName)
    {
        this.companyName = companyName;
    }


    public double getLastPrice()
    {
        return lastPrice;
    }


    public void setLastPrice(double lastPrice)
    {
        this.lastPrice = lastPrice;
    }


    public TradeStatus getStatus()
    {
        return status;
    }


    public void setStatus(TradeStatus status)
    {
        this.status = status;
    }


    public String getStatusSummary()
    {
        return statusSummary;
    }


    public void setStatusSummary(String statusSummary)
    {
        this.statusSummary = statusSummary;
    }


    public double getRsi()
    {
        return rsi;
    }


    public void setRsi(double rsi)
    {
        this.rsi = rsi;
    }


    public double getDistanceToSupportPercent()
    {
        return distanceToSupportPercent;
    }


    public void setDistanceToSupportPercent(double distanceToSupportPercent)
    {
        this.distanceToSupportPercent = distanceToSupportPercent;
    }


    public double getChanceRiskRatio()
    {
        return chanceRiskRatio;
    }


    public void setChanceRiskRatio(double chanceRiskRatio)
    {
        this.chanceRiskRatio = chanceRiskRatio;
    }


    public Integer getDaysUntilEarnings()
    {
        return daysUntilEarnings;
    }


    public void setDaysUntilEarnings(Integer daysUntilEarnings)
    {
        this.daysUntilEarnings = daysUntilEarnings;
    }


    public String getLastUpdated()
    {
        return lastUpdated;
    }


    public void setLastUpdated(String lastUpdated)
    {
        this.lastUpdated = lastUpdated;
    }
}
