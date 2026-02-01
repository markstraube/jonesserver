package com.straube.jones.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.straube.jones.dataprovider.stocks.StockItem;
import java.util.List;
import java.util.ArrayList;

/**
 * Response for stock search by name
 */
public class ShareSearchResponse
{
    @JsonProperty("query")
    private String query;

    @JsonProperty("matches_found")
    private Integer matchesFound;

    @JsonProperty("stocks")
    private List<StockMatch> stocks;

    @JsonProperty("status")
    private String status;

    @JsonProperty("message")
    private String message;

    public ShareSearchResponse()
    {
        // Default constructor for JSON deserialization
        this.stocks = new ArrayList<>();
    }


    // Success factory method
    public static ShareSearchResponse success(String query, List<StockMatch> matches)
    {
        ShareSearchResponse response = new ShareSearchResponse();
        response.query = query;
        response.matchesFound = matches.size();
        response.stocks = matches;
        response.status = "success";
        response.message = matches.isEmpty() ? "No stocks found matching the query"
                        : matches.size() + " stock(s) found matching the query";
        return response;
    }


    // Error factory method
    public static ShareSearchResponse error(String query, String errorMessage)
    {
        ShareSearchResponse response = new ShareSearchResponse();
        response.query = query;
        response.matchesFound = 0;
        response.stocks = new ArrayList<>();
        response.status = "error";
        response.message = errorMessage;
        return response;
    }

    // Inner class for stock match details
    public static class StockMatch
    {
        @JsonProperty("isin")
        private String isin;

        @JsonProperty("name")
        private String name;

        @JsonProperty("country_code")
        private String countryCode;

        @JsonProperty("industry")
        private String industry;

        @JsonProperty("currency")
        private String currency;

        @JsonProperty("capitalization")
        private Double capitalization;

        @JsonProperty("dividend_yield")
        private Double dividendYield;

        @JsonProperty("similarity_score")
        private Double similarityScore;

        public StockMatch()
        {
            // Default constructor for JSON deserialization
        }


        public StockMatch(StockItem item, Double similarityScore)
        {
            this.isin = item.getISIN();
            this.name = item.getName();
            this.countryCode = item.getCountryCode();
            this.industry = item.getIndustry();
            this.currency = item.getCurrency();
            this.capitalization = item.getCapitalization();
            this.dividendYield = item.getDividendYield();
            this.similarityScore = similarityScore;
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


        public String getName()
        {
            return name;
        }


        public void setName(String name)
        {
            this.name = name;
        }


        public String getCountryCode()
        {
            return countryCode;
        }


        public void setCountryCode(String countryCode)
        {
            this.countryCode = countryCode;
        }


        public String getIndustry()
        {
            return industry;
        }


        public void setIndustry(String industry)
        {
            this.industry = industry;
        }


        public String getCurrency()
        {
            return currency;
        }


        public void setCurrency(String currency)
        {
            this.currency = currency;
        }


        public Double getCapitalization()
        {
            return capitalization;
        }


        public void setCapitalization(Double capitalization)
        {
            this.capitalization = capitalization;
        }


        public Double getDividendYield()
        {
            return dividendYield;
        }


        public void setDividendYield(Double dividendYield)
        {
            this.dividendYield = dividendYield;
        }


        public Double getSimilarityScore()
        {
            return similarityScore;
        }


        public void setSimilarityScore(Double similarityScore)
        {
            this.similarityScore = similarityScore;
        }
    }

    // Getters and Setters
    public String getQuery()
    {
        return query;
    }


    public void setQuery(String query)
    {
        this.query = query;
    }


    public Integer getMatchesFound()
    {
        return matchesFound;
    }


    public void setMatchesFound(Integer matchesFound)
    {
        this.matchesFound = matchesFound;
    }


    public List<StockMatch> getStocks()
    {
        return stocks;
    }


    public void setStocks(List<StockMatch> stocks)
    {
        this.stocks = stocks;
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
