package com.straube.jones.dataprovider.stocks;


import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

import com.straube.jones.dataprovider.DataUtil;
import com.straube.jones.dataprovider.DataUtil.TIMESPAN;

public class StockItem
{
    Long id;
    String ISIN;
    Integer exchangeId;
    String onVistaLink;
    String name;
    String finanzNetLink;
    String shortUrl;
    String arivaLink;
    String countryCode;
    Double capitalization;
    String industry;
    String perf4;
    String perf26;
    String perf52;
    String last;
    String currency;
    Double turnover;
    Double dividendYield;

    public StockItem()
    {
        id = 0L;
        ISIN = "";
        exchangeId = 1; // Default to Frankfurt
        onVistaLink = "";
        name = "";
        finanzNetLink = "";
        shortUrl = "";
        arivaLink = "";
        countryCode = "";
        capitalization = 0.0;
        industry = "";
        perf4 = "";
        perf26 = "";
        perf52 = "";
        last = "";
        currency = "$";
        turnover = 0.0;
        dividendYield = 0.0;
    }


    public StockItem(ResultSet rs, long id)
        throws SQLException
    {
        this.id = id;
        setISIN(rs.getString("cIsin"));
        name = rs.getString("cName");
        shortUrl = rs.getString("cRef");
        countryCode = rs.getString("cCountryCode");
        capitalization = Math.floor(rs.getDouble("cMarketCapitalization"));
        industry = rs.getString("cBranch");
        perf4 = String.format(Locale.US, "%.1f", rs.getDouble("cPerf4Weeks"));
        perf26 = String.format(Locale.US, "%.1f", rs.getDouble("cPerf6Months"));
        perf52 = String.format(Locale.US, "%.1f", rs.getDouble("cPerf1Year"));
        Double dblLast = rs.getDouble("cLast");
        last = String.format(Locale.US, "%.2f", dblLast);
        currency = rs.getString("cCurrency");
        dividendYield = rs.getDouble("cDividendYield");
        turnover = Math.floor(rs.getDouble("cTurnover"));
        onVistaLink = DataUtil.createOnVistaLink(ISIN, shortUrl);
        finanzNetLink = DataUtil.getChartImageWithFinanzNetLink(ISIN, TIMESPAN.YEAR1);
        arivaLink = DataUtil.getChartImageWithOnVistaLink(shortUrl, ISIN, TIMESPAN.MONTH1);
    }


    /**
     * @return the iSIN
     */
    public long getId()
    {
        return id;
    }


    /**
     * @return the iSIN
     */
    public String getISIN()
    {
        return ISIN;
    }


    /**
     * @param iSIN the iSIN to set
     */
    public void setISIN(String iSIN)
    {
        ISIN = iSIN;
        if (ISIN.startsWith("US"))
        {
            exchangeId = 15; // NYSE
        }
        else if (ISIN.startsWith("GB"))
        {
            exchangeId = 24; // London Stock Exchange
        }
        else
        {
            exchangeId = 5002; // Tradegate
        }
    }


    /**
     * @return the exchangeId
     */
    public Integer getExchangeId()
    {
        return exchangeId;
    }


    /**
     * @param exchangeId the exchangeId to set
     */
    public void setExchangeId(Integer exchangeId)
    {
        this.exchangeId = exchangeId;
    }


    /**
     * @return the link
     */
    public String getOnVistaLink()
    {
        return onVistaLink;
    }


    /**
     * @param link the link to set
     */
    public void setOnVistaLink(String link)
    {
        this.onVistaLink = link;
    }


    /**
     * @return the name
     */
    public String getName()
    {
        return name;
    }


    /**
     * @param name the name to set
     */
    public void setName(String name)
    {
        this.name = name;
    }


    /**
     * @return the name
     */
    public String getFinanzNetLink()
    {
        return finanzNetLink;
    }


    /**
     * @param name the name to set
     */
    public void setFinanzNetLink(String name)
    {
        this.name = finanzNetLink;
    }


    /**
     * @return the shortUrl
     */
    public String getShortUrl()
    {
        return shortUrl;
    }


    /**
     * @param shortUrl the shortUrl to set
     */
    public void setShortUrl(String shortUrl)
    {
        this.shortUrl = shortUrl;
    }


    /**
     * @return the image
     */
    public String getArivaLink()
    {
        return arivaLink;
    }


    /**
     * @param image the image to set
     */
    public void setArivaLink(String link)
    {
        this.arivaLink = link;
    }


    /**
     * @return the countryCode
     */
    public String getCountryCode()
    {
        return countryCode;
    }


    /**
     * @param countryCode the countryCode to set
     */
    public void setCountryCode(String countryCode)
    {
        this.countryCode = countryCode;
    }


    /**
     * @return the capitalization
     */
    public Double getCapitalization()
    {
        return capitalization;
    }


    /**
     * @param capitalization the capitalization to set
     */
    public void setCapitalization(Double capitalization)
    {
        this.capitalization = capitalization;
    }


    /**
     * @return the turnover
     */
    public Double getTurnover()
    {
        return turnover;
    }


    /**
     * @param set turnover
     */
    public void setTurnover(Double turnover)
    {
        this.turnover = turnover;
    }


    /**
     * @return the dividend
     */
    public Double getDividendYield()
    {
        return dividendYield;
    }


    /**
     * @param set dividend
     */
    public void setDividendYield(Double dividendYield)
    {
        this.dividendYield = dividendYield;
    }


    /**
     * @return the industry
     */
    public String getIndustry()
    {
        return industry;
    }


    /**
     * @param industry the industry to set
     */
    public void setIndustry(String industry)
    {
        this.industry = industry;
    }


    /**
     * @return the perf4
     */
    public String getPerf4()
    {
        return perf4;
    }


    /**
     * @param perf4 the perf4 to set
     */
    public void setPerf4(String perf4)
    {
        this.perf4 = perf4;
    }


    /**
     * @return the perf26
     */
    public String getPerf26()
    {
        return perf26;
    }


    /**
     * @param perf26 the perf26 to set
     */
    public void setPerf26(String perf26)
    {
        this.perf26 = perf26;
    }


    /**
     * @return the perf52
     */
    public String getPerf52()
    {
        return perf52;
    }


    /**
     * @param perf52 the perf52 to set
     */
    public void setPerf52(String perf52)
    {
        this.perf52 = perf52;
    }


    /**
     * @return the last
     */
    public String getLast()
    {
        return last;
    }


    /**
     * @param last the last to set
     */
    public void setLast(String last)
    {
        this.last = last;
    }


    /**
     * @return the currency
     */
    public String getCurrency()
    {
        return currency;
    }


    /**
     * @param currency the currency to set
     */
    public void setCurrency(String currency)
    {
        this.currency = currency;
    }
}
