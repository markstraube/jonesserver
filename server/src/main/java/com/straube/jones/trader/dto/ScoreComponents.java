package com.straube.jones.trader.dto;


public class ScoreComponents
{
    private int trend;
    private int momentum;
    private int volume;
    private int volatility;
    private int riskReward;

    public int getTrend()
    {
        return trend;
    }


    public void setTrend(int trend)
    {
        this.trend = trend;
    }


    public int getMomentum()
    {
        return momentum;
    }


    public void setMomentum(int momentum)
    {
        this.momentum = momentum;
    }


    public int getVolume()
    {
        return volume;
    }


    public void setVolume(int volume)
    {
        this.volume = volume;
    }


    public int getVolatility()
    {
        return volatility;
    }


    public void setVolatility(int volatility)
    {
        this.volatility = volatility;
    }


    public int getRiskReward()
    {
        return riskReward;
    }


    public void setRiskReward(int riskReward)
    {
        this.riskReward = riskReward;
    }
}
