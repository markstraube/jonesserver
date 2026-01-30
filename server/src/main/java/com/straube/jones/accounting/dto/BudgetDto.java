package com.straube.jones.accounting.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BudgetDto {
    private Double budget;
    private Double portfolio;
    private Double cash;
    private String date;

    public BudgetDto() {}

    public BudgetDto(Double budget, Double portfolio, Double cash, String date) {
        this.budget = budget;
        this.portfolio = portfolio;
        this.cash = cash;
        this.date = date;
    }

    public Double getBudget() { return budget; }
    public void setBudget(Double budget) { this.budget = budget; }

    public Double getPortfolio() { return portfolio; }
    public void setPortfolio(Double portfolio) { this.portfolio = portfolio; }

    public Double getCash() { return cash; }
    public void setCash(Double cash) { this.cash = cash; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
}
