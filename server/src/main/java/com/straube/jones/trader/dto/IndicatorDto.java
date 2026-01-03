package com.straube.jones.trader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO für technische Indikatoren einer Aktie an einem bestimmten Datum.
 * Enthält verschiedene technische Analysewerte wie Bollinger Bänder, RSI, MACD, 
 * gleitende Durchschnitte und Unterstützungs-/Widerstandsniveaus.
 */
@Schema(description = "Technische Indikatoren einer Aktie zu einem bestimmten Datum")
public class IndicatorDto {
    
    @Schema(description = "Aktiensymbol (z.B. AAPL, MSFT)", example = "AAPL")
    private String symbol;
    
    @Schema(description = "Datum als Unix-Timestamp in Millisekunden", example = "1735689600000")
    private Long date;
    
    @Schema(description = "Unteres Bollinger Band (15-Tage-Periode)", example = "142.50")
    private Double bb15low;
    
    @Schema(description = "Mittleres Bollinger Band / SMA 15 (15-Tage-Periode)", example = "150.00")
    private Double bb15mid;
    
    @Schema(description = "Oberes Bollinger Band (15-Tage-Periode)", example = "157.50")
    private Double bb15high;
    
    @Schema(description = "Relative Strength Index (RSI) - Wert zwischen 0-100", example = "45.6")
    private Double rsi;
    
    @Schema(description = "Handelsvolumen", example = "75000000")
    private Double volume;
    
    @Schema(description = "MACD-Wert (Moving Average Convergence Divergence)", example = "1.23")
    private Double macdValue;
    
    @Schema(description = "MACD-Signal-Linie", example = "0.98")
    private Double macdSignal;
    
    @Schema(description = "RSI30-Wahrscheinlichkeit für 5-Tage-Horizont", example = "0.12")
    private Double rsi30Days5;
    
    @Schema(description = "RSI30-Wahrscheinlichkeit für 10-Tage-Horizont", example = "0.25")
    private Double rsi30Days10;
    
    @Schema(description = "RSI30-Wahrscheinlichkeit für 20-Tage-Horizont", example = "0.42")
    private Double rsi30Days20;
    
    @Schema(description = "RSI30-Wahrscheinlichkeit für 30-Tage-Horizont", example = "0.58")
    private Double rsi30Days30;
    
    @Schema(description = "Gesamtwahrscheinlichkeit für RSI unter 30", example = "0.34")
    private Double rsi30probability;
    
    @Schema(description = "Simple Moving Average über 5 Tage", example = "148.30")
    private Double sma5;
    
    @Schema(description = "Simple Moving Average über 10 Tage", example = "147.80")
    private Double sma10;
    
    @Schema(description = "Simple Moving Average über 20 Tage", example = "146.50")
    private Double sma20;
    
    @Schema(description = "Simple Moving Average über 30 Tage", example = "145.20")
    private Double sma30;
    
    @Schema(description = "Exponential Moving Average über 5 Tage", example = "148.60")
    private Double ema5;
    
    @Schema(description = "Exponential Moving Average über 10 Tage", example = "148.10")
    private Double ema10;
    
    @Schema(description = "Exponential Moving Average über 20 Tage", example = "147.20")
    private Double ema20;
    
    @Schema(description = "Exponential Moving Average über 30 Tage", example = "146.50")
    private Double ema30;
    
    @Schema(description = "Unterstützungsniveau (Support)", example = "140.00")
    private Double support;
    
    @Schema(description = "Widerstandsniveau (Resistance)", example = "160.00")
    private Double resistance;

    // Getters and Setters
    
    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Long getDate() {
        return date;
    }

    public void setDate(Long date) {
        this.date = date;
    }

    public Double getBb15low() {
        return bb15low;
    }

    public void setBb15low(Double bb15low) {
        this.bb15low = bb15low;
    }

    public Double getBb15mid() {
        return bb15mid;
    }

    public void setBb15mid(Double bb15mid) {
        this.bb15mid = bb15mid;
    }

    public Double getBb15high() {
        return bb15high;
    }

    public void setBb15high(Double bb15high) {
        this.bb15high = bb15high;
    }

    public Double getRsi() {
        return rsi;
    }

    public void setRsi(Double rsi) {
        this.rsi = rsi;
    }

    public Double getVolume() {
        return volume;
    }

    public void setVolume(Double volume) {
        this.volume = volume;
    }

    public Double getMacdValue() {
        return macdValue;
    }

    public void setMacdValue(Double macdValue) {
        this.macdValue = macdValue;
    }

    public Double getMacdSignal() {
        return macdSignal;
    }

    public void setMacdSignal(Double macdSignal) {
        this.macdSignal = macdSignal;
    }

    public Double getRsi30Days5() {
        return rsi30Days5;
    }

    public void setRsi30Days5(Double rsi30Days5) {
        this.rsi30Days5 = rsi30Days5;
    }

    public Double getRsi30Days10() {
        return rsi30Days10;
    }

    public void setRsi30Days10(Double rsi30Days10) {
        this.rsi30Days10 = rsi30Days10;
    }

    public Double getRsi30Days20() {
        return rsi30Days20;
    }

    public void setRsi30Days20(Double rsi30Days20) {
        this.rsi30Days20 = rsi30Days20;
    }

    public Double getRsi30Days30() {
        return rsi30Days30;
    }

    public void setRsi30Days30(Double rsi30Days30) {
        this.rsi30Days30 = rsi30Days30;
    }

    public Double getRsi30probability() {
        return rsi30probability;
    }

    public void setRsi30probability(Double rsi30probability) {
        this.rsi30probability = rsi30probability;
    }

    public Double getSma5() {
        return sma5;
    }

    public void setSma5(Double sma5) {
        this.sma5 = sma5;
    }

    public Double getSma10() {
        return sma10;
    }

    public void setSma10(Double sma10) {
        this.sma10 = sma10;
    }

    public Double getSma20() {
        return sma20;
    }

    public void setSma20(Double sma20) {
        this.sma20 = sma20;
    }

    public Double getSma30() {
        return sma30;
    }

    public void setSma30(Double sma30) {
        this.sma30 = sma30;
    }

    public Double getEma5() {
        return ema5;
    }

    public void setEma5(Double ema5) {
        this.ema5 = ema5;
    }

    public Double getEma10() {
        return ema10;
    }

    public void setEma10(Double ema10) {
        this.ema10 = ema10;
    }

    public Double getEma20() {
        return ema20;
    }

    public void setEma20(Double ema20) {
        this.ema20 = ema20;
    }

    public Double getEma30() {
        return ema30;
    }

    public void setEma30(Double ema30) {
        this.ema30 = ema30;
    }

    public Double getSupport() {
        return support;
    }

    public void setSupport(Double support) {
        this.support = support;
    }

    public Double getResistance() {
        return resistance;
    }

    public void setResistance(Double resistance) {
        this.resistance = resistance;
    }
}
