package com.straube.jones.trader.filter;


/**
 * Enthält den geplanten Trade und dessen Risiko.
 */
public class RiskRewardFilter
{

    /**
     * Geplanter Einstiegspreis. Wird manuell oder algorithmisch festgelegt.
     */
    private double entryPrice;

    /**
     * Stop-Loss-Preis. Bedeutung: Maximales akzeptiertes Verlustniveau.
     */
    private double stopLossPrice;

    /**
     * Zielpreis (Take-Profit).
     */
    private double targetPrice;

    /**
     * Risiko pro Aktie in Euro. Berechnung: Entry - Stop-Loss
     */
    private double riskPerShare;

    /**
     * Erwarteter Gewinn pro Aktie in Euro. Berechnung: Target - Entry
     */
    private double rewardPerShare;

    /**
     * Chance-Risiko-Verhältnis (CRV). Berechnung: Reward / Risk
     */
    private double chanceRiskRatio;

    public double getEntryPrice()
    {
        return entryPrice;
    }


    public void setEntryPrice(double entryPrice)
    {
        this.entryPrice = entryPrice;
    }


    public double getStopLossPrice()
    {
        return stopLossPrice;
    }


    public void setStopLossPrice(double stopLossPrice)
    {
        this.stopLossPrice = stopLossPrice;
    }


    public double getTargetPrice()
    {
        return targetPrice;
    }


    public void setTargetPrice(double targetPrice)
    {
        this.targetPrice = targetPrice;
    }


    public double getRiskPerShare()
    {
        return riskPerShare;
    }


    public void setRiskPerShare(double riskPerShare)
    {
        this.riskPerShare = riskPerShare;
    }


    public double getRewardPerShare()
    {
        return rewardPerShare;
    }


    public void setRewardPerShare(double rewardPerShare)
    {
        this.rewardPerShare = rewardPerShare;
    }


    public double getChanceRiskRatio()
    {
        return chanceRiskRatio;
    }


    public void setChanceRiskRatio(double chanceRiskRatio)
    {
        this.chanceRiskRatio = chanceRiskRatio;
    }
}
