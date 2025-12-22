package com.straube.jones.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StockData
{
    @JsonProperty("market_capitalization")
    private String marketCapitalization;
    
    @JsonProperty("price_performance_6_months")
    private String pricePerformance6Months;
    
    @JsonProperty("price_outlook_5_days")
    private String priceOutlook5Days;
    
    @JsonProperty("analyst_opinions")
    private String analystOpinions;

    public StockData()
    {
    }

    public StockData(String marketCapitalization, String pricePerformance6Months, 
                     String priceOutlook5Days, String analystOpinions)
    {
        this.marketCapitalization = marketCapitalization;
        this.pricePerformance6Months = pricePerformance6Months;
        this.priceOutlook5Days = priceOutlook5Days;
        this.analystOpinions = analystOpinions;
    }

    public String getMarketCapitalization()
    {
        return marketCapitalization;
    }

    public void setMarketCapitalization(String marketCapitalization)
    {
        this.marketCapitalization = marketCapitalization;
    }

    public String getPricePerformance6Months()
    {
        return pricePerformance6Months;
    }

    public void setPricePerformance6Months(String pricePerformance6Months)
    {
        this.pricePerformance6Months = pricePerformance6Months;
    }

    public String getPriceOutlook5Days()
    {
        return priceOutlook5Days;
    }

    public void setPriceOutlook5Days(String priceOutlook5Days)
    {
        this.priceOutlook5Days = priceOutlook5Days;
    }

    public String getAnalystOpinions()
    {
        return analystOpinions;
    }

    public void setAnalystOpinions(String analystOpinions)
    {
        this.analystOpinions = analystOpinions;
    }
}
