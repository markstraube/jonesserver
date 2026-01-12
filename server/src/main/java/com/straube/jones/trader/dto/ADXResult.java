package com.straube.jones.trader.dto;


// Ergänzende Klasse für das Ergebnis
public class ADXResult
{
    double adx;
    double plusDI;
    double minusDI;
    String trendStrength;

    public double getAdx()
    {
        return adx;
    }

    public void setAdx(double adx)
    {
        this.adx = adx;
    }

    public double getPlusDI()
    {
        return plusDI;
    }

    public void setPlusDI(double plusDI)
    {
        this.plusDI = plusDI;
    }

    public double getMinusDI()
    {
        return minusDI;
    }

    public void setMinusDI(double minusDI)
    {
        this.minusDI = minusDI;
    }

    public String getTrendStrength()
    {
        return trendStrength;
    }

    public void setTrendStrength(String trendStrength)
    {
        this.trendStrength = trendStrength;
    }

    @Override
    public String toString()
    {
        return String.format("ADX: %.2f | +DI: %.2f | -DI: %.2f | Bewertung: %s",
                             adx,
                             plusDI,
                             minusDI,
                             trendStrength);
    }
}
