package com.straube.jones.trader.dto;


public class RatingDto
{
    private String symbol;
    private String shortTerm;
    private String midTerm;
    private String longTerm;
    private Long date;

    public String getSymbol()
    {
        return symbol;
    }


    public void setSymbol(String symbol)
    {
        this.symbol = symbol;
    }


    public String getShortTerm()
    {
        return shortTerm;
    }


    public void setShortTerm(String shortTerm)
    {
        this.shortTerm = shortTerm;
    }


    public String getMidTerm()
    {
        return midTerm;
    }


    public void setMidTerm(String midTerm)
    {
        this.midTerm = midTerm;
    }


    public String getLongTerm()
    {
        return longTerm;
    }


    public void setLongTerm(String longTerm)
    {
        this.longTerm = longTerm;
    }


    public Long getDate()
    {
        return date;
    }


    public void setDate(Long date)
    {
        this.date = date;
    }
}
