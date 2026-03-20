package com.straube.jones.trader.dto;


import java.time.LocalDate;

/**
 * Repräsentiert einen Handelstag einer Aktie. Diese Daten kommen direkt von der Börse / Marktdaten-API.
 */
public class DailyPrice implements PricePoint
{

    /** Handelstag */
    private LocalDate date;

    /** Eröffnungskurs */
    private double open;

    /** Tageshoch */
    private double high;

    /** Tagestief */
    private double low;

    /** Schlusskurs */
    private double close;

    /** Gehandeltes Volumen (Stückzahl) */
    private long volume;

    /** Währung des Handelstags (z.B. EUR, USD) */
    private String currency;

    /**
     * Adjustierter Schlusskurs. Berücksichtigt Dividenden, Splits usw. Wird für Indikatoren und
     * Trendberechnungen verwendet.
     */
    private double adjClose;

    public DailyPrice()
    {}


    public DailyPrice(double open,
                      double high,
                      double low,
                      double close,
                      double adjClose,
                      long volume,
                      String currency)
    {
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.adjClose = adjClose;
        this.volume = volume;
        this.currency = currency;
    }


    public LocalDate getDate()
    {
        return date;
    }


    public void setDate(LocalDate date)
    {
        this.date = date;
    }


    public double getOpen()
    {
        return open;
    }


    public void setOpen(double open)
    {
        this.open = open;
    }


    public double getHigh()
    {
        return high;
    }


    public void setHigh(double high)
    {
        this.high = high;
    }


    public double getLow()
    {
        return low;
    }


    public void setLow(double low)
    {
        this.low = low;
    }


    public double getClose()
    {
        return close;
    }


    public void setClose(double close)
    {
        this.close = close;
    }


    public long getVolume()
    {
        return volume;
    }


    public void setVolume(long volume)
    {
        this.volume = volume;
    }


    public double getAdjClose()
    {
        return adjClose;
    }


    public void setAdjClose(double adjClose)
    {
        this.adjClose = adjClose;
    }


    public String getCurrency()
    {
        return currency;
    }


    public void setCurrency(String currency)
    {
        this.currency = currency;
    }

    /**
     * Gibt den adjustierten Schlusskurs zurück (für Indikatorberechnungen).
     *
     * @see PricePoint
     */
    @Override
    public double getCloseValue()
    {
        return adjClose;
    }
}
