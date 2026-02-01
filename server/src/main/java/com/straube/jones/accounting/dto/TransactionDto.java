package com.straube.jones.accounting.dto;


import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionDto
{
    // Erweiterte Felder für "closed" Positionen
    private String saleDate;
    private Double salePrice;
    private String status;
    private String createdAt;
    private String updatedAt;

    public String getSaleDate()
    {
        return saleDate;
    }


    public void setSaleDate(String saleDate)
    {
        this.saleDate = saleDate;
    }


    public Double getSalePrice()
    {
        return salePrice;
    }


    public void setSalePrice(Double salePrice)
    {
        this.salePrice = salePrice;
    }


    public String getStatus()
    {
        return status;
    }


    public void setStatus(String status)
    {
        this.status = status;
    }


    public String getCreatedAt()
    {
        return createdAt;
    }


    public void setCreatedAt(String createdAt)
    {
        this.createdAt = createdAt;
    }


    public String getUpdatedAt()
    {
        return updatedAt;
    }


    public void setUpdatedAt(String updatedAt)
    {
        this.updatedAt = updatedAt;
    }

    // Request fields
    private String isin;
    private String symbol;
    private String stockName;
    private Integer quantity;
    private Double price;
    private String positionId;

    // Response fields
    private String transactionId;
    private Double portfolioValue;
    private Double cash;

    public TransactionDto()
    {}


    public String getIsin()
    {
        return isin;
    }


    public void setIsin(String isin)
    {
        this.isin = isin;
    }


    public String getSymbol()
    {
        return symbol;
    }


    public void setSymbol(String symbol)
    {
        this.symbol = symbol;
    }


    public String getStockName()
    {
        return stockName;
    }


    public void setStockName(String stockName)
    {
        this.stockName = stockName;
    }


    public Integer getQuantity()
    {
        return quantity;
    }


    public void setQuantity(Integer quantity)
    {
        this.quantity = quantity;
    }


    public Double getPrice()
    {
        return price;
    }


    public void setPrice(Double price)
    {
        this.price = price;
    }


    public String getPositionId()
    {
        return positionId;
    }


    public void setPositionId(String positionId)
    {
        this.positionId = positionId;
    }


    public String getTransactionId()
    {
        return transactionId;
    }


    public void setTransactionId(String transactionId)
    {
        this.transactionId = transactionId;
    }


    public Double getPortfolioValue()
    {
        return portfolioValue;
    }


    public void setPortfolioValue(Double portfolioValue)
    {
        this.portfolioValue = portfolioValue;
    }


    public Double getCash()
    {
        return cash;
    }


    public void setCash(Double cash)
    {
        this.cash = cash;
    }


    public static java.util.List<TransactionDto> fromJson(String jsonPrefs)
    {
        java.util.List<TransactionDto> result = new java.util.ArrayList<>();
        try
        {
            org.json.JSONArray arr = new org.json.JSONArray(jsonPrefs);
            for (int i = 0; i < arr.length(); i++ )
            {
                org.json.JSONObject obj = arr.getJSONObject(i);
                if ("active".equalsIgnoreCase(obj.optString("status")))
                {
                    TransactionDto dto = new TransactionDto();
                    dto.setIsin(obj.optString("isin", null));
                    dto.setSymbol(obj.optString("symbol", null));
                    dto.setStockName(obj.optString("stockName", null));
                    dto.setQuantity(obj.has("quantity") ? obj.optInt("quantity") : null);
                    dto.setPrice(obj.has("price") ? obj.optDouble("price") : null);
                    dto.setPositionId(obj.optString("positionId", null));
                    dto.setTransactionId(obj.optString("transactionId", null));
                    dto.setPortfolioValue(obj.has("portfolioValue") ? obj.optDouble("portfolioValue") : null);
                    dto.setCash(obj.has("cash") ? obj.optDouble("cash") : null);
                    result.add(dto);
                }
            }
        }
        catch (Exception e)
        {
            // Fehlerbehandlung: leere Liste zurückgeben oder Logging
        }
        return result;
    }
}
