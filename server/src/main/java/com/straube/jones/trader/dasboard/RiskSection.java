package com.straube.jones.trader.dasboard;


public class RiskSection
{

    /** Geplanter Einstieg */
    private double entryPrice;

    /** Stop-Loss */
    private double stopLossPrice;

    /** Zielpreis */
    private double targetPrice;

    /** Risiko pro Aktie */
    private double riskPerShare;

    /** Erwarteter Gewinn */
    private double rewardPerShare;

    /** Chance-Risiko-Verhältnis */
    private double chanceRiskRatio;

    /** Einschätzung in Klartext */
    private String explanation;

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


    public String getExplanation()
    {
        return explanation;
    }


    public void setExplanation(String explanation)
    {
        this.explanation = explanation;
    }
}
