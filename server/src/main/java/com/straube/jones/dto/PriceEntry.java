package com.straube.jones.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * Represents a single price entry from Yahoo Finance.
 * Can be regular market price, pre-market, or after-market.
 */
public class PriceEntry
{
    @JsonProperty("type")
    private PriceType type;
    
    @JsonProperty("price")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal price;
    
    @JsonProperty("changeAbsolute")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal changeAbsolute;
    
    @JsonProperty("changePercent")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal changePercent;
    
    @JsonProperty("timestamp")
    private String timestamp;
    
    @JsonProperty("qualifier")
    private String qualifier;
    
    public enum PriceType
    {
        REGULAR,
        PRE_MARKET,
        AFTER_MARKET
    }
    
    public PriceEntry()
    {
    }
    
    public PriceEntry(PriceType type, BigDecimal price, BigDecimal changeAbsolute, 
                     BigDecimal changePercent, String timestamp, String qualifier)
    {
        this.type = type;
        this.price = price;
        this.changeAbsolute = changeAbsolute;
        this.changePercent = changePercent;
        this.timestamp = timestamp;
        this.qualifier = qualifier;
    }
    
    // Getters and Setters
    public PriceType getType()
    {
        return type;
    }
    
    public void setType(PriceType type)
    {
        this.type = type;
    }
    
    public BigDecimal getPrice()
    {
        return price;
    }
    
    public void setPrice(BigDecimal price)
    {
        this.price = price;
    }
    
    public BigDecimal getChangeAbsolute()
    {
        return changeAbsolute;
    }
    
    public void setChangeAbsolute(BigDecimal changeAbsolute)
    {
        this.changeAbsolute = changeAbsolute;
    }
    
    public BigDecimal getChangePercent()
    {
        return changePercent;
    }
    
    public void setChangePercent(BigDecimal changePercent)
    {
        this.changePercent = changePercent;
    }
    
    public String getTimestamp()
    {
        return timestamp;
    }
    
    public void setTimestamp(String timestamp)
    {
        this.timestamp = timestamp;
    }
    
    public String getQualifier()
    {
        return qualifier;
    }
    
    public void setQualifier(String qualifier)
    {
        this.qualifier = qualifier;
    }
}
