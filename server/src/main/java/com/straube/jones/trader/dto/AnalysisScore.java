package com.straube.jones.trader.dto;


public class AnalysisScore
{
    private int total;
    private String interpretation;
    private ScoreComponents components;

    public int getTotal()
    {
        return total;
    }


    public void setTotal(int total)
    {
        this.total = total;
    }


    public String getInterpretation()
    {
        return interpretation;
    }


    public void setInterpretation(String interpretation)
    {
        this.interpretation = interpretation;
    }


    public ScoreComponents getComponents()
    {
        return components;
    }


    public void setComponents(ScoreComponents components)
    {
        this.components = components;
    }
}
