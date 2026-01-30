package com.straube.jones.accounting.dto;

public class PerformanceDto {
    private String date;
    private Double budget;
    private Double portfolio;
    private Double cash;
    private Long dayCounter;

    public PerformanceDto() {}

    public PerformanceDto(String date, Double budget, Double portfolio, Double cash, long dayCounter) {
        this.date = date;
        this.budget = budget;
        this.portfolio = portfolio;
        this.cash = cash;
        this.dayCounter = dayCounter;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public Double getBudget() { return budget; }
    public void setBudget(Double budget) { this.budget = budget; }

    public Double getPortfolio() { return portfolio; }
    public void setPortfolio(Double portfolio) { this.portfolio = portfolio; }

    public Double getCash() { return cash; }
    public void setCash(Double cash) { this.cash = cash; }

    public Long getDayCounter() { return dayCounter; }
    public void setDayCounter(Long dayCounter) { this.dayCounter = dayCounter; }
}
