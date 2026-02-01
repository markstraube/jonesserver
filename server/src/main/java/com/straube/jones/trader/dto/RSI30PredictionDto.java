package com.straube.jones.trader.dto;


import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * DTO für RSI30 Prediction Response
 */
@Schema(description = "RSI30 Vorhersage-Ergebnis mit Wahrscheinlichkeiten und Kaufpreis-Zielen")
public class RSI30PredictionDto
{
    @Schema(description = "Symbol der Aktie", example = "TSLA")
    private String symbol;

    @Schema(description = "Zeitpunkt der Analyse (Java Timestamp)", example = "1735689600000")
    private long timestamp;

    @Schema(description = "Aktueller Preis der Aktie", example = "449.72")
    private double currentPrice;

    @Schema(description = "Aktueller RSI-Wert", example = "46.70")
    private double currentRsi;

    @Schema(description = "Wahrscheinlichkeit in Prozent, dass RSI < 30 in den nächsten 30 Tagen erreicht wird", example = "45.5")
    private double probabilityPercent;

    @Schema(description = "Textuelle Einschätzung der Wahrscheinlichkeit", example = "MITTEL - RSI < 30 möglich")
    private String assessment;

    @Schema(description = "Geschätzte Anzahl Tage bis RSI < 30 erreicht wird (-1 = unwahrscheinlich)", example = "20")
    private int estimatedDaysToRSI30;

    @Schema(description = "Liste der analysierten Faktoren")
    private List<String> factors;

    @Schema(description = "Historische Analyse-Details")
    private HistoricalAnalysisDto historicalAnalysis;

    @Schema(description = "Kaufpreis-Ziele für verschiedene Zeithorizonte")
    private BuyPriceTargetsDto buyPriceTargets;

    // Getter und Setter

    public String getSymbol()
    {
        return symbol;
    }


    public void setSymbol(String symbol)
    {
        this.symbol = symbol;
    }


    public long getTimestamp()
    {
        return timestamp;
    }


    public void setTimestamp(long timestamp)
    {
        this.timestamp = timestamp;
    }


    public double getCurrentPrice()
    {
        return currentPrice;
    }


    public void setCurrentPrice(double currentPrice)
    {
        this.currentPrice = currentPrice;
    }


    public double getCurrentRsi()
    {
        return currentRsi;
    }


    public void setCurrentRsi(double currentRsi)
    {
        this.currentRsi = currentRsi;
    }


    public double getProbabilityPercent()
    {
        return probabilityPercent;
    }


    public void setProbabilityPercent(double probabilityPercent)
    {
        this.probabilityPercent = probabilityPercent;
    }


    public String getAssessment()
    {
        return assessment;
    }


    public void setAssessment(String assessment)
    {
        this.assessment = assessment;
    }


    public int getEstimatedDaysToRSI30()
    {
        return estimatedDaysToRSI30;
    }


    public void setEstimatedDaysToRSI30(int estimatedDaysToRSI30)
    {
        this.estimatedDaysToRSI30 = estimatedDaysToRSI30;
    }


    public List<String> getFactors()
    {
        return factors;
    }


    public void setFactors(List<String> factors)
    {
        this.factors = factors;
    }


    public HistoricalAnalysisDto getHistoricalAnalysis()
    {
        return historicalAnalysis;
    }


    public void setHistoricalAnalysis(HistoricalAnalysisDto historicalAnalysis)
    {
        this.historicalAnalysis = historicalAnalysis;
    }


    public BuyPriceTargetsDto getBuyPriceTargets()
    {
        return buyPriceTargets;
    }


    public void setBuyPriceTargets(BuyPriceTargetsDto buyPriceTargets)
    {
        this.buyPriceTargets = buyPriceTargets;
    }
}
