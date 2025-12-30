package com.straube.jones.trader.dto;


public class AnalysisInsight
{
    private InsightType type;
    private String title;
    private String description;

    public InsightType getType()
    {
        return type;
    }


    public void setType(InsightType type)
    {
        this.type = type;
    }


    public String getTitle()
    {
        return title;
    }


    public void setTitle(String title)
    {
        this.title = title;
    }


    public String getDescription()
    {
        return description;
    }


    public void setDescription(String description)
    {
        this.description = description;
    }
}
