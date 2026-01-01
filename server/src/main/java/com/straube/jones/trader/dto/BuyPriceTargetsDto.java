package com.straube.jones.trader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO für Kaufpreis-Ziele
 */
@Schema(description = "Kaufpreis-Ziele für verschiedene Zeithorizonte bis RSI < 30 erreicht wird")
public class BuyPriceTargetsDto
{
    @Schema(description = "Aktueller Preis", example = "449.72")
    private double currentPrice;

    @Schema(description = "Zielpreis nach 5 Tagen", example = "444.50")
    private double target5Days;

    @Schema(description = "Zielpreis nach 10 Tagen", example = "437.20")
    private double target10Days;

    @Schema(description = "Zielpreis nach 20 Tagen", example = "423.80")
    private double target20Days;

    @Schema(description = "Zielpreis nach 30 Tagen (RSI < 30 erreicht)", example = "410.50")
    private double target30Days;

    @Schema(description = "Benötigter durchschnittlicher täglicher Rückgang in Prozent", example = "0.47")
    private double requiredDailyDecline;

    @Schema(description = "Einschätzung der Volatilität", example = "Moderat - normale Preisbewegungen")
    private String volatilityAssessment;

    // Getter und Setter

    public double getCurrentPrice()
    {
        return currentPrice;
    }

    public void setCurrentPrice(double currentPrice)
    {
        this.currentPrice = currentPrice;
    }

    public double getTarget5Days()
    {
        return target5Days;
    }

    public void setTarget5Days(double target5Days)
    {
        this.target5Days = target5Days;
    }

    public double getTarget10Days()
    {
        return target10Days;
    }

    public void setTarget10Days(double target10Days)
    {
        this.target10Days = target10Days;
    }

    public double getTarget20Days()
    {
        return target20Days;
    }

    public void setTarget20Days(double target20Days)
    {
        this.target20Days = target20Days;
    }

    public double getTarget30Days()
    {
        return target30Days;
    }

    public void setTarget30Days(double target30Days)
    {
        this.target30Days = target30Days;
    }

    public double getRequiredDailyDecline()
    {
        return requiredDailyDecline;
    }

    public void setRequiredDailyDecline(double requiredDailyDecline)
    {
        this.requiredDailyDecline = requiredDailyDecline;
    }

    public String getVolatilityAssessment()
    {
        return volatilityAssessment;
    }

    public void setVolatilityAssessment(String volatilityAssessment)
    {
        this.volatilityAssessment = volatilityAssessment;
    }
}
