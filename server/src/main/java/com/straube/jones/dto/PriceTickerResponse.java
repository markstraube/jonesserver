package com.straube.jones.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for the Price Ticker API.
 * Contains stock price information scraped from Yahoo Finance.
 */
public class PriceTickerResponse
{
    @JsonProperty("isin")
    private String isin;
    
    @JsonProperty("symbolYahoo")
    private String symbolYahoo;
    
    @JsonProperty("currency")
    private String currency;
    
    @JsonProperty("prices")
    private List<PriceEntry> prices;
    
    @JsonProperty("source")
    private String source;
    
    @JsonProperty("retrievedAt")
    private String retrievedAt;
    
    public PriceTickerResponse()
    {
        this.prices = new ArrayList<>();
        this.source = "Yahoo Finance";
    }
    
    public PriceTickerResponse(String isin, String symbolYahoo, String currency)
    {
        this();
        this.isin = isin;
        this.symbolYahoo = symbolYahoo;
        this.currency = currency;
    }
    
    // Getters and Setters
    public String getIsin()
    {
        return isin;
    }
    
    public void setIsin(String isin)
    {
        this.isin = isin;
    }
    
    public String getSymbolYahoo()
    {
        return symbolYahoo;
    }
    
    public void setSymbolYahoo(String symbolYahoo)
    {
        this.symbolYahoo = symbolYahoo;
    }
    
    public String getCurrency()
    {
        return currency;
    }
    
    public void setCurrency(String currency)
    {
        this.currency = currency;
    }
    
    public List<PriceEntry> getPrices()
    {
        return prices;
    }
    
    public void setPrices(List<PriceEntry> prices)
    {
        this.prices = prices;
    }
    
    public String getSource()
    {
        return source;
    }
    
    public void setSource(String source)
    {
        this.source = source;
    }
    
    public String getRetrievedAt()
    {
        return retrievedAt;
    }
    
    public void setRetrievedAt(String retrievedAt)
    {
        this.retrievedAt = retrievedAt;
    }
}
