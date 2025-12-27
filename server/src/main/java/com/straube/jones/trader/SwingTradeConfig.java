package com.straube.jones.trader;


public class SwingTradeConfig
{
    private long minAverageVolume = 500_000;
    private int smaFastPeriod = 50;
    private int smaSlowPeriod = 200;
    private int emaPeriod = 20;
    private double maxPullbackDistance = -5.0; // -5%
    private int rsiPeriod = 14;
    private double minRsi = 40.0;
    private double maxRsi = 60.0;
    private int supportSearchPeriod = 20;
    private double maxSupportDistance = 3.0; // 3%
    private double stopLossBuffer = 0.01; // 1% below support
    private double targetProfitFactor = 1.06; // 6% target
    private double minCrv = 2.0;

    public long getMinAverageVolume()
    {
        return minAverageVolume;
    }


    public void setMinAverageVolume(long minAverageVolume)
    {
        this.minAverageVolume = minAverageVolume;
    }


    public int getSmaFastPeriod()
    {
        return smaFastPeriod;
    }


    public void setSmaFastPeriod(int smaFastPeriod)
    {
        this.smaFastPeriod = smaFastPeriod;
    }


    public int getSmaSlowPeriod()
    {
        return smaSlowPeriod;
    }


    public void setSmaSlowPeriod(int smaSlowPeriod)
    {
        this.smaSlowPeriod = smaSlowPeriod;
    }


    public int getEmaPeriod()
    {
        return emaPeriod;
    }


    public void setEmaPeriod(int emaPeriod)
    {
        this.emaPeriod = emaPeriod;
    }


    public double getMaxPullbackDistance()
    {
        return maxPullbackDistance;
    }


    public void setMaxPullbackDistance(double maxPullbackDistance)
    {
        this.maxPullbackDistance = maxPullbackDistance;
    }


    public int getRsiPeriod()
    {
        return rsiPeriod;
    }


    public void setRsiPeriod(int rsiPeriod)
    {
        this.rsiPeriod = rsiPeriod;
    }


    public double getMinRsi()
    {
        return minRsi;
    }


    public void setMinRsi(double minRsi)
    {
        this.minRsi = minRsi;
    }


    public double getMaxRsi()
    {
        return maxRsi;
    }


    public void setMaxRsi(double maxRsi)
    {
        this.maxRsi = maxRsi;
    }


    public int getSupportSearchPeriod()
    {
        return supportSearchPeriod;
    }


    public void setSupportSearchPeriod(int supportSearchPeriod)
    {
        this.supportSearchPeriod = supportSearchPeriod;
    }


    public double getMaxSupportDistance()
    {
        return maxSupportDistance;
    }


    public void setMaxSupportDistance(double maxSupportDistance)
    {
        this.maxSupportDistance = maxSupportDistance;
    }


    public double getStopLossBuffer()
    {
        return stopLossBuffer;
    }


    public void setStopLossBuffer(double stopLossBuffer)
    {
        this.stopLossBuffer = stopLossBuffer;
    }


    public double getTargetProfitFactor()
    {
        return targetProfitFactor;
    }


    public void setTargetProfitFactor(double targetProfitFactor)
    {
        this.targetProfitFactor = targetProfitFactor;
    }


    public double getMinCrv()
    {
        return minCrv;
    }


    public void setMinCrv(double minCrv)
    {
        this.minCrv = minCrv;
    }
}
