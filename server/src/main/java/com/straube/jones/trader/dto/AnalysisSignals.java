package com.straube.jones.trader.dto;


public class AnalysisSignals
{
    private SignalDetail shortTerm;
    private SignalDetail mediumTerm;
    private SignalDetail longTerm;

    public SignalDetail getShortTerm()
    {
        return shortTerm;
    }


    public void setShortTerm(SignalDetail shortTerm)
    {
        this.shortTerm = shortTerm;
    }


    public SignalDetail getMediumTerm()
    {
        return mediumTerm;
    }


    public void setMediumTerm(SignalDetail mediumTerm)
    {
        this.mediumTerm = mediumTerm;
    }


    public SignalDetail getLongTerm()
    {
        return longTerm;
    }


    public void setLongTerm(SignalDetail longTerm)
    {
        this.longTerm = longTerm;
    }
}
