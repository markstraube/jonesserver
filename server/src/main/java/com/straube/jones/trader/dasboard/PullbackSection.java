package com.straube.jones.trader.dasboard;


public class PullbackSection
{

    /** Abstand zur EMA20 in Prozent */
    private double distanceToEma20Percent;

    /** RSI-Wert */
    private double rsi;

    /** Ist der Pullback ideal für einen Einstieg? */
    private boolean pullbackValid;

    /** Erklärung */
    private String explanation;

    public double getDistanceToEma20Percent()
    {
        return distanceToEma20Percent;
    }


    public void setDistanceToEma20Percent(double distanceToEma20Percent)
    {
        this.distanceToEma20Percent = distanceToEma20Percent;
    }


    public double getRsi()
    {
        return rsi;
    }


    public void setRsi(double rsi)
    {
        this.rsi = rsi;
    }


    public boolean isPullbackValid()
    {
        return pullbackValid;
    }


    public void setPullbackValid(boolean pullbackValid)
    {
        this.pullbackValid = pullbackValid;
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
