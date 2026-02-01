package com.straube.jones.model;


import com.fasterxml.jackson.annotation.JsonProperty;

public class StockFundamentals
{
    @JsonProperty("ISIN")
    private String isin;

    @JsonProperty("WKN")
    private String wkn;

    @JsonProperty("SYMBOL")
    private String symbol;

    @JsonProperty("SYMBOL.YAHOO")
    private String symbolYahoo;

    @JsonProperty("SYMBOL.GOOGLE")
    private String symbolGoogle;

    @JsonProperty("company_basics")
    private CompanyBasics companyBasics;

    @JsonProperty("stock_data")
    private StockData stockData;

    public StockFundamentals()
    {}


    public StockFundamentals(String isin,
                             String wkn,
                             String symbolYahoo,
                             String symbolGoogle,
                             CompanyBasics companyBasics,
                             StockData stockData)
    {
        this.isin = isin;
        this.wkn = wkn;
        this.symbolYahoo = symbolYahoo;
        this.symbolGoogle = symbolGoogle;
        this.companyBasics = companyBasics;
        this.stockData = stockData;
    }


    public String getIsin()
    {
        return isin;
    }


    public void setIsin(String isin)
    {
        this.isin = isin;
    }


    public String getWkn()
    {
        return wkn;
    }


    public void setWkn(String wkn)
    {
        this.wkn = wkn;
    }


    public String getSymbol()
    {
        return symbol;
    }


    public void setSymbol(String symbol)
    {
        this.symbol = symbol;
    }


    public String getSymbolYahoo()
    {
        return symbolYahoo;
    }


    public void setSymbolYahoo(String symbolYahoo)
    {
        this.symbolYahoo = symbolYahoo;
    }


    public String getSymbolGoogle()
    {
        return symbolGoogle;
    }


    public void setSymbolGoogle(String symbolGoogle)
    {
        this.symbolGoogle = symbolGoogle;
    }


    public CompanyBasics getCompanyBasics()
    {
        return companyBasics;
    }


    public void setCompanyBasics(CompanyBasics companyBasics)
    {
        this.companyBasics = companyBasics;
    }


    public StockData getStockData()
    {
        return stockData;
    }


    public void setStockData(StockData stockData)
    {
        this.stockData = stockData;
    }
}
