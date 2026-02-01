package com.straube.jones.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import java.sql.Timestamp;

/**
 * DTO für OnVista Stammdaten
 */
public class OnVistaDto
{
    @JsonProperty("isin")
    private String isin;

    @JsonProperty("name")
    private String name;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("branch")
    private String branch;

    @JsonProperty("sector")
    private String sector;

    @JsonProperty("countryCode")
    private String countryCode;

    @JsonProperty("last")
    private Double last;

    @JsonProperty("exchange")
    private String exchange;

    @JsonProperty("dateLong")
    private Double dateLong;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("performance")
    private Double performance;

    @JsonProperty("perf1Year")
    private Double perf1Year;

    @JsonProperty("perf6Months")
    private Double perf6Months;

    @JsonProperty("perf4Weeks")
    private Double perf4Weeks;

    @JsonProperty("dividendYield")
    private Double dividendYield;

    @JsonProperty("dividend")
    private Double dividend;

    @JsonProperty("marketCapitalization")
    private Double marketCapitalization;

    @JsonProperty("riskRating")
    private Double riskRating;

    @JsonProperty("employees")
    private Double employees;

    @JsonProperty("turnover")
    private Double turnover;

    @JsonProperty("updated")
    private Timestamp updated;

    // Getters and Setters
    public String getIsin()
    {
        return isin;
    }


    public void setIsin(String isin)
    {
        this.isin = isin;
    }


    public String getName()
    {
        return name;
    }


    public void setName(String name)
    {
        this.name = name;
    }


    public String getSymbol()
    {
        return symbol;
    }


    public void setSymbol(String symbol)
    {
        this.symbol = symbol;
    }


    public String getBranch()
    {
        return branch;
    }


    public void setBranch(String branch)
    {
        this.branch = branch;
    }


    public String getSector()
    {
        return sector;
    }


    public void setSector(String sector)
    {
        this.sector = sector;
    }


    public String getCountryCode()
    {
        return countryCode;
    }


    public void setCountryCode(String countryCode)
    {
        this.countryCode = countryCode;
    }


    public Double getLast()
    {
        return last;
    }


    public void setLast(Double last)
    {
        this.last = last;
    }


    public String getExchange()
    {
        return exchange;
    }


    public void setExchange(String exchange)
    {
        this.exchange = exchange;
    }


    public Double getDateLong()
    {
        return dateLong;
    }


    public void setDateLong(Double dateLong)
    {
        this.dateLong = dateLong;
    }


    public String getCurrency()
    {
        return currency;
    }


    public void setCurrency(String currency)
    {
        this.currency = currency;
    }


    public Double getPerformance()
    {
        return performance;
    }


    public void setPerformance(Double performance)
    {
        this.performance = performance;
    }


    public Double getPerf1Year()
    {
        return perf1Year;
    }


    public void setPerf1Year(Double perf1Year)
    {
        this.perf1Year = perf1Year;
    }


    public Double getPerf6Months()
    {
        return perf6Months;
    }


    public void setPerf6Months(Double perf6Months)
    {
        this.perf6Months = perf6Months;
    }


    public Double getPerf4Weeks()
    {
        return perf4Weeks;
    }


    public void setPerf4Weeks(Double perf4Weeks)
    {
        this.perf4Weeks = perf4Weeks;
    }


    public Double getDividendYield()
    {
        return dividendYield;
    }


    public void setDividendYield(Double dividendYield)
    {
        this.dividendYield = dividendYield;
    }


    public Double getDividend()
    {
        return dividend;
    }


    public void setDividend(Double dividend)
    {
        this.dividend = dividend;
    }


    public Double getMarketCapitalization()
    {
        return marketCapitalization;
    }


    public void setMarketCapitalization(Double marketCapitalization)
    {
        this.marketCapitalization = marketCapitalization;
    }


    public Double getRiskRating()
    {
        return riskRating;
    }


    public void setRiskRating(Double riskRating)
    {
        this.riskRating = riskRating;
    }


    public Double getEmployees()
    {
        return employees;
    }


    public void setEmployees(Double employees)
    {
        this.employees = employees;
    }


    public Double getTurnover()
    {
        return turnover;
    }


    public void setTurnover(Double turnover)
    {
        this.turnover = turnover;
    }


    public Timestamp getUpdated()
    {
        return updated;
    }


    public void setUpdated(Timestamp updated)
    {
        this.updated = updated;
    }
}
