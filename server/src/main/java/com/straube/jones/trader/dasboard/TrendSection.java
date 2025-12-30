package com.straube.jones.trader.dasboard;


import java.util.List;

public class TrendSection
{

    /** Befindet sich die Aktie in einem Aufwärtstrend? */
    private boolean uptrend;

    /** 50-Tage-Durchschnitt */
    private double sma50;

    /** 200-Tage-Durchschnitt */
    private double sma200;

    /** Klartext für Laien */
    private String explanation;

    public boolean isUptrend()
    {
        return uptrend;
    }


    public void setUptrend(boolean uptrend)
    {
        this.uptrend = uptrend;
    }


    public double getSma50()
    {
        return sma50;
    }


    public void setSma50(double sma50)
    {
        this.sma50 = sma50;
    }


    public double getSma200()
    {
        return sma200;
    }


    public void setSma200(double sma200)
    {
        this.sma200 = sma200;
    }


    public String getExplanation()
    {
        return explanation;
    }


    public void setExplanation(String explanation)
    {
        this.explanation = explanation;
    }
}
