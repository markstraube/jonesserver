package com.straube.jones.dto;


import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Response DTO for the {@code GET /api/stocks/intraday} endpoint.
 *
 * <p>Encapsulates all intraday price data for a single calendar day retrieved from the
 * {@code tTradegateIntraday} table.  The response is structured for direct use in front-end
 * chart libraries:
 * <ul>
 *   <li>A {@link IntradayHeader header} object with pre-calculated day statistics (open, close,
 *       high, low, delta, …).</li>
 *   <li>A chronologically ordered {@link IntradayDataPoint data} array of price snapshots
 *       which can be plotted directly as a time-series.</li>
 * </ul>
 *
 * <p><b>Column name mapping used throughout this class:</b>
 * <table border="1">
 *   <tr><th>DB column</th><th>JSON field</th></tr>
 *   <tr><td>cStueck</td><td>volume</td></tr>
 *   <tr><td>cUmsatz</td><td>revenue</td></tr>
 * </table>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntradayResponse
{
    /** ISIN of the requested stock. */
    private String isin;

    /** Calendar date of the requested trading day in ISO-8601 format (e.g. {@code "2026-03-15"}). */
    private String date;

    /**
     * The reduction interval that was applied, e.g. {@code "1M"} for 1-minute buckets.
     * Omitted from JSON when no reduction was requested.
     */
    private String reduce;

    /** Pre-calculated day statistics. */
    private IntradayHeader header;

    /**
     * Time-ordered list of intraday price snapshots (or aggregated buckets when
     * {@code reduce} is set).
     */
    private List<IntradayDataPoint> data;


    // -----------------------------------------------------------------------
    // Getters & Setters
    // -----------------------------------------------------------------------

    public String getIsin()
    {
        return isin;
    }


    public void setIsin(String isin)
    {
        this.isin = isin;
    }


    public String getDate()
    {
        return date;
    }


    public void setDate(String date)
    {
        this.date = date;
    }


    public String getReduce()
    {
        return reduce;
    }


    public void setReduce(String reduce)
    {
        this.reduce = reduce;
    }


    public IntradayHeader getHeader()
    {
        return header;
    }


    public void setHeader(IntradayHeader header)
    {
        this.header = header;
    }


    public List<IntradayDataPoint> getData()
    {
        return data;
    }


    public void setData(List<IntradayDataPoint> data)
    {
        this.data = data;
    }


    // =======================================================================
    // Inner class: IntradayHeader
    // =======================================================================

    /**
     * Day-level summary statistics calculated over all intraday records returned in this
     * response.
     *
     * <p>The cumulative end-of-day fields ({@code previousClose}, {@code volume},
     * {@code executions}, {@code delta}) are taken directly from the <em>last</em> database
     * record of the day, because Tradegate stores these as running totals that accumulate
     * since market open.
     */
    public static class IntradayHeader
    {
        /** First traded price of the day (cLast of the first intraday record). */
        private BigDecimal open;

        /** Last traded price of the day (cLast of the last intraday record). */
        private BigDecimal close;

        /** Intraday high: maximum cLast value across all records of the day. */
        private BigDecimal high;

        /**
         * Unix timestamp in milliseconds of the record with the highest traded price.
         * When multiple records share the same maximum, the first occurrence is used.
         */
        @JsonProperty("high-timestamp")
        private Long highTimestamp;

        /** Intraday low: minimum cLast value across all records of the day. */
        private BigDecimal low;

        /**
         * Unix timestamp in milliseconds of the record with the lowest traded price.
         * When multiple records share the same minimum, the first occurrence is used.
         */
        @JsonProperty("low-timestamp")
        private Long lowTimestamp;

        /**
         * Previous trading day's closing price (cClose of the last intraday record of the
         * day).  This is the constant reference value that Tradegate uses to compute the
         * {@code delta} percentage.
         */
        @JsonProperty("previous-close")
        private BigDecimal previousClose;

        /**
         * Cumulative traded volume in pieces (cStueck of the last intraday record of the
         * day – running total since market open on this day).
         */
        private Long volume;

        /**
         * Total number of individual order executions (cExecutions of the last intraday
         * record of the day – running total since market open on this day).
         */
        private Integer executions;

        /**
         * Price delta relative to the previous close expressed in percent (cDelta of the
         * last intraday record of the day).
         */
        private BigDecimal delta;


        // --- Getters & Setters ---

        public BigDecimal getOpen()
        {
            return open;
        }


        public void setOpen(BigDecimal open)
        {
            this.open = open;
        }


        public BigDecimal getClose()
        {
            return close;
        }


        public void setClose(BigDecimal close)
        {
            this.close = close;
        }


        public BigDecimal getHigh()
        {
            return high;
        }


        public void setHigh(BigDecimal high)
        {
            this.high = high;
        }


        public Long getHighTimestamp()
        {
            return highTimestamp;
        }


        public void setHighTimestamp(Long highTimestamp)
        {
            this.highTimestamp = highTimestamp;
        }


        public BigDecimal getLow()
        {
            return low;
        }


        public void setLow(BigDecimal low)
        {
            this.low = low;
        }


        public Long getLowTimestamp()
        {
            return lowTimestamp;
        }


        public void setLowTimestamp(Long lowTimestamp)
        {
            this.lowTimestamp = lowTimestamp;
        }


        public BigDecimal getPreviousClose()
        {
            return previousClose;
        }


        public void setPreviousClose(BigDecimal previousClose)
        {
            this.previousClose = previousClose;
        }


        public Long getVolume()
        {
            return volume;
        }


        public void setVolume(Long volume)
        {
            this.volume = volume;
        }


        public Integer getExecutions()
        {
            return executions;
        }


        public void setExecutions(Integer executions)
        {
            this.executions = executions;
        }


        public BigDecimal getDelta()
        {
            return delta;
        }


        public void setDelta(BigDecimal delta)
        {
            this.delta = delta;
        }
    }


    // =======================================================================
    // Inner class: IntradayDataPoint
    // =======================================================================

    /**
     * A single intraday price snapshot or an aggregated time bucket.
     *
     * <p><b>Raw mode</b> (no {@code reduce} parameter): each instance corresponds to one
     * database row fetched from {@code tTradegateIntraday}.
     *
     * <p><b>Reduced mode</b> ({@code reduce} parameter is set): all raw data points that
     * fall within the same time bucket are aggregated.  Every numeric field is the
     * arithmetic mean of the values in the bucket.  The {@code timestamp} is the arithmetic
     * mean of all contained timestamps, rounded to the nearest second (millisecond precision
     * in the JSON, rounded to a whole-second value).
     */
    public static class IntradayDataPoint
    {
        /**
         * Unix timestamp in milliseconds identifying this price snapshot.
         * In reduced mode this is the rounded average of the bucket's timestamps.
         */
        private Long timestamp;

        /** Last traded price (cLast). */
        private BigDecimal last;

        /** Best bid price at this moment (cBid). */
        private BigDecimal bid;

        /** Best ask price at this moment (cAsk). */
        private BigDecimal ask;

        /** Volume-weighted average price reported by Tradegate (cAvg). */
        private BigDecimal avg;

        /**
         * Cumulative traded volume in pieces since market open (mapped from cStueck).
         * In reduced mode this is the rounded average of cStueck within the bucket.
         */
        private Long volume;

        /**
         * Cumulative traded turnover in order currency since market open
         * (mapped from cUmsatz).  In reduced mode this is the average within the bucket.
         */
        private BigDecimal revenue;

        /**
         * Current price delta relative to the previous close in percent (cDelta).
         * In reduced mode this is the average within the bucket.
         */
        private BigDecimal delta;


        // --- Getters & Setters ---

        public Long getTimestamp()
        {
            return timestamp;
        }


        public void setTimestamp(Long timestamp)
        {
            this.timestamp = timestamp;
        }


        public BigDecimal getLast()
        {
            return last;
        }


        public void setLast(BigDecimal last)
        {
            this.last = last;
        }


        public BigDecimal getBid()
        {
            return bid;
        }


        public void setBid(BigDecimal bid)
        {
            this.bid = bid;
        }


        public BigDecimal getAsk()
        {
            return ask;
        }


        public void setAsk(BigDecimal ask)
        {
            this.ask = ask;
        }


        public BigDecimal getAvg()
        {
            return avg;
        }


        public void setAvg(BigDecimal avg)
        {
            this.avg = avg;
        }


        public Long getVolume()
        {
            return volume;
        }


        public void setVolume(Long volume)
        {
            this.volume = volume;
        }


        public BigDecimal getRevenue()
        {
            return revenue;
        }


        public void setRevenue(BigDecimal revenue)
        {
            this.revenue = revenue;
        }


        public BigDecimal getDelta()
        {
            return delta;
        }


        public void setDelta(BigDecimal delta)
        {
            this.delta = delta;
        }
    }
}
