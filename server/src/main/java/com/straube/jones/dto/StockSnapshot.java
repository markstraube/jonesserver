package com.straube.jones.dto;


import java.math.BigDecimal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class StockSnapshot
{
    private Price price;
    private Ranges ranges;
    private Volume volume;
    private Market market;
    private Earnings earnings;
    private AnalystEstimates analystEstimates;

    public Price getPrice()
    {
        return price;
    }


    public void setPrice(Price price)
    {
        this.price = price;
    }


    public Ranges getRanges()
    {
        return ranges;
    }


    public void setRanges(Ranges ranges)
    {
        this.ranges = ranges;
    }


    public Volume getVolume()
    {
        return volume;
    }


    public void setVolume(Volume volume)
    {
        this.volume = volume;
    }


    public Market getMarket()
    {
        return market;
    }


    public void setMarket(Market market)
    {
        this.market = market;
    }


    public Earnings getEarnings()
    {
        return earnings;
    }


    public void setEarnings(Earnings earnings)
    {
        this.earnings = earnings;
    }


    public AnalystEstimates getAnalystEstimates()
    {
        return analystEstimates;
    }


    public void setAnalystEstimates(AnalystEstimates analystEstimates)
    {
        this.analystEstimates = analystEstimates;
    }


    @Override
    public String toString()
    {
        try
        {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            return mapper.writeValueAsString(this);
        }
        catch (JsonProcessingException e)
        {
            return super.toString();
        }
    }

    public static class Price
    {
        private BigDecimal previousClose;
        private BigDecimal open;
        private BidAsk bid;
        private BidAsk ask;

        public BigDecimal getPreviousClose()
        {
            return previousClose;
        }


        public void setPreviousClose(BigDecimal previousClose)
        {
            this.previousClose = previousClose;
        }


        public BigDecimal getOpen()
        {
            return open;
        }


        public void setOpen(BigDecimal open)
        {
            this.open = open;
        }


        public BidAsk getBid()
        {
            return bid;
        }


        public void setBid(BidAsk bid)
        {
            this.bid = bid;
        }


        public BidAsk getAsk()
        {
            return ask;
        }


        public void setAsk(BidAsk ask)
        {
            this.ask = ask;
        }
    }

    public static class BidAsk
    {
        private BigDecimal price;
        private Integer size;

        public BidAsk()
        {}


        public BidAsk(BigDecimal price, Integer size)
        {
            this.price = price;
            this.size = size;
        }


        public BigDecimal getPrice()
        {
            return price;
        }


        public void setPrice(BigDecimal price)
        {
            this.price = price;
        }


        public Integer getSize()
        {
            return size;
        }


        public void setSize(Integer size)
        {
            this.size = size;
        }
    }

    public static class Ranges
    {
        private Range day;
        private Range week52;

        public Range getDay()
        {
            return day;
        }


        public void setDay(Range day)
        {
            this.day = day;
        }


        public Range getWeek52()
        {
            return week52;
        }


        public void setWeek52(Range week52)
        {
            this.week52 = week52;
        }
    }

    public static class Range
    {
        private BigDecimal low;
        private BigDecimal high;

        public Range()
        {}


        public Range(BigDecimal low, BigDecimal high)
        {
            this.low = low;
            this.high = high;
        }


        public BigDecimal getLow()
        {
            return low;
        }


        public void setLow(BigDecimal low)
        {
            this.low = low;
        }


        public BigDecimal getHigh()
        {
            return high;
        }


        public void setHigh(BigDecimal high)
        {
            this.high = high;
        }
    }

    public static class Volume
    {
        private Long current;
        private Long average;

        public Long getCurrent()
        {
            return current;
        }


        public void setCurrent(Long current)
        {
            this.current = current;
        }


        public Long getAverage()
        {
            return average;
        }


        public void setAverage(Long average)
        {
            this.average = average;
        }
    }

    public static class Market
    {
        private BigDecimal marketCapIntraday;
        private BigDecimal beta5YMonthly;
        private BigDecimal peRatioTTM;
        private BigDecimal epsTTM;

        public BigDecimal getMarketCapIntraday()
        {
            return marketCapIntraday;
        }


        public void setMarketCapIntraday(BigDecimal marketCapIntraday)
        {
            this.marketCapIntraday = marketCapIntraday;
        }


        public BigDecimal getBeta5YMonthly()
        {
            return beta5YMonthly;
        }


        public void setBeta5YMonthly(BigDecimal beta5YMonthly)
        {
            this.beta5YMonthly = beta5YMonthly;
        }


        public BigDecimal getPeRatioTTM()
        {
            return peRatioTTM;
        }


        public void setPeRatioTTM(BigDecimal peRatioTTM)
        {
            this.peRatioTTM = peRatioTTM;
        }


        public BigDecimal getEpsTTM()
        {
            return epsTTM;
        }


        public void setEpsTTM(BigDecimal epsTTM)
        {
            this.epsTTM = epsTTM;
        }
    }

    public static class Earnings
    {
        private String earningsDate;

        public String getEarningsDate()
        {
            return earningsDate;
        }


        public void setEarningsDate(String earningsDate)
        {
            this.earningsDate = earningsDate;
        }
    }

    public static class AnalystEstimates
    {
        private BigDecimal oneYearTargetEstimate;

        public BigDecimal getOneYearTargetEstimate()
        {
            return oneYearTargetEstimate;
        }


        public void setOneYearTargetEstimate(BigDecimal oneYearTargetEstimate)
        {
            this.oneYearTargetEstimate = oneYearTargetEstimate;
        }
    }
}
