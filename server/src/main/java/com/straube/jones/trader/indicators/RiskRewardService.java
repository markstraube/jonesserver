package com.straube.jones.trader.indicators;


/**
 * Berechnet Risiko, Gewinn und CRV eines Trades.
 */
public class RiskRewardService
{

    public double calculateRisk(double entry, double stopLoss)
    {
        return entry - stopLoss;
    }


    public double calculateReward(double entry, double target)
    {
        return target - entry;
    }


    public double calculateCRV(double reward, double risk)
    {
        if (risk <= 0)
        { throw new IllegalArgumentException("Risiko muss > 0 sein"); }
        return reward / risk;
    }
}
