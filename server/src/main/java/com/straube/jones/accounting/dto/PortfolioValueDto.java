package com.straube.jones.accounting.dto;


public class PortfolioValueDto
{
    private Double portfolioValue;

    public PortfolioValueDto()
    {}


    public PortfolioValueDto(Double portfolioValue)
    {
        this.portfolioValue = portfolioValue;
    }


    public Double getPortfolioValue()
    {
        return portfolioValue;
    }


    public void setPortfolioValue(Double portfolioValue)
    {
        this.portfolioValue = portfolioValue;
    }
}
