package com.straube.jones.dto;


import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response for the last known share price of a stock
 */
public class LastSharePriceResponse
{
    @JsonProperty("isin")
    private String isin;

    @JsonProperty("last_price")
    private Double lastPrice;

    @JsonProperty("timestamp")
    private Long timestamp;

    @JsonProperty("date")
    private String date;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("status")
    private String status;

    @JsonProperty("message")
    private String message;

    public LastSharePriceResponse()
    {
        // Default constructor for JSON deserialization
    }


    // Success factory method
    public static LastSharePriceResponse success(String isin, Double lastPrice, Long timestamp, String date)
    {
        LastSharePriceResponse response = new LastSharePriceResponse();
        response.isin = isin;
        response.lastPrice = lastPrice;
        response.timestamp = timestamp;
        response.date = date;
        response.currency = "EUR";
        response.status = "success";
        response.message = "Last share price retrieved successfully";
        return response;
    }


    // Not found factory method
    public static LastSharePriceResponse notFound(String isin)
    {
        LastSharePriceResponse response = new LastSharePriceResponse();
        response.isin = isin;
        response.lastPrice = null;
        response.timestamp = null;
        response.date = null;
        response.currency = null;
        response.status = "not_found";
        response.message = "No price data found for ISIN: " + isin;
        return response;
    }


    // Error factory method
    public static LastSharePriceResponse error(String isin, String errorMessage)
    {
        LastSharePriceResponse response = new LastSharePriceResponse();
        response.isin = isin;
        response.lastPrice = null;
        response.timestamp = null;
        response.date = null;
        response.currency = null;
        response.status = "error";
        response.message = errorMessage;
        return response;
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


    public Double getLastPrice()
    {
        return lastPrice;
    }


    public void setLastPrice(Double lastPrice)
    {
        this.lastPrice = lastPrice;
    }


    public Long getTimestamp()
    {
        return timestamp;
    }


    public void setTimestamp(Long timestamp)
    {
        this.timestamp = timestamp;
    }


    public String getDate()
    {
        return date;
    }


    public void setDate(String date)
    {
        this.date = date;
    }


    public String getCurrency()
    {
        return currency;
    }


    public void setCurrency(String currency)
    {
        this.currency = currency;
    }


    public String getStatus()
    {
        return status;
    }


    public void setStatus(String status)
    {
        this.status = status;
    }


    public String getMessage()
    {
        return message;
    }


    public void setMessage(String message)
    {
        this.message = message;
    }
}
