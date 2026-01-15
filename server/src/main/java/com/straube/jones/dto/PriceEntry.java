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

    @JsonProperty("bid-price")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal bidPrice;

    @JsonProperty("ask-price")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal askPrice;

    @JsonProperty("high-price")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal highPrice;

    @JsonProperty("low-price")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal lowPrice;

    @JsonProperty("last-price")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal lastPrice;

    @JsonProperty("reference-price")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal referencePrice;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("exchange")
    private String exchange;

    public enum PriceType
    {
        REGULAR, PRE_MARKET, AFTER_MARKET
    }

    public PriceEntry()
    {}


    public PriceEntry(PriceType type,
                      BigDecimal bidPrice,
                      BigDecimal askPrice,
                      BigDecimal highPrice,
                      BigDecimal lowPrice,
                      BigDecimal lastPrice,
                      BigDecimal referencePrice,
                      String timestamp,
                      String exchange)
    {
        this.type = type;
        this.bidPrice = bidPrice;
        this.askPrice = askPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.lastPrice = lastPrice;
        this.referencePrice = referencePrice;
        this.timestamp = timestamp;
        this.exchange = exchange;
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


    public BigDecimal getBidPrice()
    {
        return bidPrice;
    }


    public void setBidPrice(BigDecimal bidPrice)
    {
        this.bidPrice = bidPrice;
    }


    public BigDecimal getAskPrice()
    {
        return askPrice;
    }


    public void setAskPrice(BigDecimal askPrice)
    {
        this.askPrice = askPrice;
    }


    public BigDecimal getHighPrice()
    {
        return highPrice;
    }


    public void setHighPrice(BigDecimal highPrice)
    {
        this.highPrice = highPrice;
    }


    public BigDecimal getLowPrice()
    {
        return lowPrice;
    }


    public void setLowPrice(BigDecimal lowPrice)
    {
        this.lowPrice = lowPrice;
    }


    public BigDecimal getLastPrice()
    {
        return lastPrice;
    }


    public void setLastPrice(BigDecimal lastPrice)
    {
        this.lastPrice = lastPrice;
    }


    public BigDecimal getReferencePrice()
    {
        return referencePrice;
    }


    public void setReferencePrice(BigDecimal referencePrice)
    {
        this.referencePrice = referencePrice;
    }
  

    public String getTimestamp()
    {
        return timestamp;
    }


    public void setTimestamp(String timestamp)
    {
        this.timestamp = timestamp;
    }


    public String getExchange()
    {
        return exchange;
    }


    public void setExchange(String exchange)
    {
        this.exchange = exchange;
    }
}
