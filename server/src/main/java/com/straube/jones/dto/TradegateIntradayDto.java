package com.straube.jones.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TradegateIntradayDto {

    private String isin;
    private String symbol = "";

    @JsonProperty("bid")
    private BigDecimal bid = BigDecimal.ZERO;

    @JsonProperty("ask")
    private BigDecimal ask = BigDecimal.ZERO;

    @JsonProperty("bidsize")
    private Integer bidSize = 0;

    @JsonProperty("asksize")
    private Integer askSize = 0;

    @JsonProperty("delta")
    private BigDecimal delta = BigDecimal.ZERO;

    @JsonProperty("stueck")
    private Long stueck = 0L;

    @JsonProperty("umsatz")
    private BigDecimal umsatz = BigDecimal.ZERO;

    @JsonProperty("avg")
    private BigDecimal avg = BigDecimal.ZERO;

    @JsonProperty("executions")
    private Integer executions = 0;

    @JsonProperty("last")
    private BigDecimal last = BigDecimal.ZERO;

    @JsonProperty("high")
    private BigDecimal high = BigDecimal.ZERO;

    @JsonProperty("low")
    private BigDecimal low = BigDecimal.ZERO;

    @JsonProperty("close")
    private BigDecimal close = BigDecimal.ZERO;

    @JsonProperty("timestamp")
    private java.sql.Timestamp timestamp = new java.sql.Timestamp(0);

    public String getIsin() {
        return isin;
    }

    public void setIsin(String isin) {
        this.isin = isin;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getBid() {
        return bid;
    }

    public void setBid(BigDecimal bid) {
        this.bid = bid;
    }

    public BigDecimal getAsk() {
        return ask;
    }

    public void setAsk(BigDecimal ask) {
        this.ask = ask;
    }

    public Integer getBidSize() {
        return bidSize;
    }

    public void setBidSize(Integer bidSize) {
        this.bidSize = bidSize;
    }

    public Integer getAskSize() {
        return askSize;
    }

    public void setAskSize(Integer askSize) {
        this.askSize = askSize;
    }

    public BigDecimal getDelta() {
        return delta;
    }

    public void setDelta(BigDecimal delta) {
        this.delta = delta;
    }

    public Long getStueck() {
        return stueck;
    }

    public void setStueck(Long stueck) {
        this.stueck = stueck;
    }

    public BigDecimal getUmsatz() {
        return umsatz;
    }

    public void setUmsatz(BigDecimal umsatz) {
        this.umsatz = umsatz;
    }

    public BigDecimal getAvg() {
        return avg;
    }

    public void setAvg(BigDecimal avg) {
        this.avg = avg;
    }

    public Integer getExecutions() {
        return executions;
    }

    public void setExecutions(Integer executions) {
        this.executions = executions;
    }

    public BigDecimal getLast() {
        return last;
    }

    public void setLast(BigDecimal last) {
        this.last = last;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public void setHigh(BigDecimal high) {
        this.high = high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public void setLow(BigDecimal low) {
        this.low = low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public void setClose(BigDecimal close) {
        this.close = close;
    }

    public java.sql.Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(java.sql.Timestamp timestamp) {
        this.timestamp = timestamp;
    }
}
