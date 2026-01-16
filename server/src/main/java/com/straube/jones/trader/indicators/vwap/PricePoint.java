package com.straube.jones.trader.indicators.vwap;


import java.time.LocalDateTime;

/**
     * Represents a single price point with timestamp
     */
public class PricePoint
{
    private final LocalDateTime timestamp;
    private final double high;
    private final double low;
    private final double close;
    private final long volume;

    public PricePoint(LocalDateTime timestamp, double high, double low, double close, long volume)
    {
        this.timestamp = timestamp;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }


    // Constructor for when you only have close price
    public PricePoint(LocalDateTime timestamp, double close, long volume)
    {
        this(timestamp, close, close, close, volume);
    }


    public double getTypicalPrice()
    {
        return (high + low + close) / 3.0;
    }


    public LocalDateTime getTimestamp()
    {
        return timestamp;
    }


    public double getHigh()
    {
        return high;
    }


    public double getLow()
    {
        return low;
    }


    public double getClose()
    {
        return close;
    }


    public long getVolume()
    {
        return volume;
    }
}
