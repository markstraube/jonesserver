package com.straube.jones.accounting.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionDto {
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

    public TransactionDto() {}

    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public String getPositionId() { return positionId; }
    public void setPositionId(String positionId) { this.positionId = positionId; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public Double getPortfolioValue() { return portfolioValue; }
    public void setPortfolioValue(Double portfolioValue) { this.portfolioValue = portfolioValue; }

    public Double getCash() { return cash; }
    public void setCash(Double cash) { this.cash = cash; }
}
