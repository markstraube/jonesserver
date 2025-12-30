package com.straube.jones.trader.dto;


public class SignalDetail
{
    private String signal;
    private double strength;
    private String explanation;

    public String getSignal()
    {
        return signal;
    }


    public void setSignal(String signal)
    {
        this.signal = signal;
    }


    public double getStrength()
    {
        return strength;
    }


    public void setStrength(double strength)
    {
        this.strength = strength;
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
