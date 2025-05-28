package com.straube.jones.cmd.db;

import java.time.LocalDate;
import java.util.Objects;

public class StockDataPoint implements Comparable<StockDataPoint> {
    private String isin;
    private LocalDate date;
    private double price;

    public StockDataPoint(String isin, LocalDate date, double price) {
        this.isin = isin;
        this.date = date;
        this.price = price;
    }

    public String getIsin() {
        return isin;
    }

    public LocalDate getDate() {
        return date;
    }

    public double getPrice() {
        return price;
    }

    @Override
    public String toString() {
        return "StockDataPoint{" +
                "isin='" + isin + '\'' +
                ", date=" + date +
                ", price=" + price +
                '}';
    }

    @Override
    public int compareTo(StockDataPoint other) {
        return this.date.compareTo(other.date);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StockDataPoint that = (StockDataPoint) o;
        return Double.compare(that.price, price) == 0 &&
                Objects.equals(isin, that.isin) &&
                Objects.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isin, date, price);
    }
}