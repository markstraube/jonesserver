package com.straube.jones.trader.dto;


import com.straube.jones.trader.dasboard.TradeStatus;

/**
 * Kompakte Darstellung für die Watchlist / Tabellenansicht.
 */
public class SwingTradeOverviewDto
{

    /** ISIN */
    private String isin;

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

    /** MACD-Wert */
    private Double macdValue;

    /** MACD-Signal */
    private Double macdSignal;

    /** Handelsvolumen */
    private Double volume;

    /** Sektor (aus OnVista) */
    private String sector;

    /** Land (aus OnVista) */
    private String country;

    /** Marktkapitalisierung (aus OnVista) */
    private Double marketCap;

    /** Abstand zur Unterstützung in Prozent */
    private double distanceToSupportPercent;

    /** Chance-Risiko-Verhältnis */
    private double chanceRiskRatio;

    /** Tage bis zu den nächsten Quartalszahlen */
    private Integer daysUntilEarnings;

    /** Datum der letzten Aktualisierung */
    private String lastUpdated;

    public String getIsin() {
        return isin;
    }

    public void setIsin(String isin) {
        this.isin = isin;
    }

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

    public Double getMacdValue() {
        return macdValue;
    }

    public void setMacdValue(Double macdValue) {
        this.macdValue = macdValue;
    }

    public Double getMacdSignal() {
        return macdSignal;
    }

    public void setMacdSignal(Double macdSignal) {
        this.macdSignal = macdSignal;
    }

    public Double getVolume() {
        return volume;
    }

    public void setVolume(Double volume) {
        this.volume = volume;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Double getMarketCap() {
        return marketCap;
    }

    public void setMarketCap(Double marketCap) {
        this.marketCap = marketCap;
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
