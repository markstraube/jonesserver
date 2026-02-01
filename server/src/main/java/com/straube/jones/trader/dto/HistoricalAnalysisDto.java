package com.straube.jones.trader.dto;


import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO für historische Analyse-Daten
 */
@Schema(description = "Historische Analyse der Preisbewegungen der letzten 30 Tage")
public class HistoricalAnalysisDto
{
    @Schema(description = "Durchschnittliche tägliche Volatilität in Prozent", example = "3.5")
    private double avgDailyVolatility;

    @Schema(description = "Anzahl aufeinanderfolgender Verlusttage", example = "3")
    private int consecutiveLossDays;

    @Schema(description = "Durchschnittlicher Verlust an Verlusttagen in Prozent", example = "2.1")
    private double avgLossOnDownDays;

    @Schema(description = "Maximaler Rückgang (Drawdown) in der Periode in Prozent", example = "8.5")
    private double maxDrawdown;

    @Schema(description = "Durchschnittlicher Gewinn an Gewinntagen in Prozent", example = "1.8")
    private double avgGainOnUpDays;

    @Schema(description = "Gesamtanzahl der Verlusttage in den letzten 30 Tagen", example = "12")
    private int totalDownDays;

    @Schema(description = "Prozentuale Preisveränderung über 30 Tage", example = "-5.2")
    private double priceChange30Days;

    // Getter und Setter

    public double getAvgDailyVolatility()
    {
        return avgDailyVolatility;
    }


    public void setAvgDailyVolatility(double avgDailyVolatility)
    {
        this.avgDailyVolatility = avgDailyVolatility;
    }


    public int getConsecutiveLossDays()
    {
        return consecutiveLossDays;
    }


    public void setConsecutiveLossDays(int consecutiveLossDays)
    {
        this.consecutiveLossDays = consecutiveLossDays;
    }


    public double getAvgLossOnDownDays()
    {
        return avgLossOnDownDays;
    }


    public void setAvgLossOnDownDays(double avgLossOnDownDays)
    {
        this.avgLossOnDownDays = avgLossOnDownDays;
    }


    public double getMaxDrawdown()
    {
        return maxDrawdown;
    }


    public void setMaxDrawdown(double maxDrawdown)
    {
        this.maxDrawdown = maxDrawdown;
    }


    public double getAvgGainOnUpDays()
    {
        return avgGainOnUpDays;
    }


    public void setAvgGainOnUpDays(double avgGainOnUpDays)
    {
        this.avgGainOnUpDays = avgGainOnUpDays;
    }


    public int getTotalDownDays()
    {
        return totalDownDays;
    }


    public void setTotalDownDays(int totalDownDays)
    {
        this.totalDownDays = totalDownDays;
    }


    public double getPriceChange30Days()
    {
        return priceChange30Days;
    }


    public void setPriceChange30Days(double priceChange30Days)
    {
        this.priceChange30Days = priceChange30Days;
    }
}
