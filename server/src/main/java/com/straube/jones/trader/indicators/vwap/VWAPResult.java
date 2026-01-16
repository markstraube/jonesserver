package com.straube.jones.trader.indicators.vwap;


/**
 * VWAP calculation result with analysis
 */
public class VWAPResult
{
    private final double vwap;
    private final double currentPrice;
    private final double deviationPercent;
    private final Signal signal;
    private final long totalVolume;

    public VWAPResult(double vwap, double currentPrice, long totalVolume)
    {
        this.vwap = vwap;
        this.currentPrice = currentPrice;
        this.totalVolume = totalVolume;
        this.deviationPercent = ((currentPrice - vwap) / vwap) * 100.0;
        this.signal = determineSignal();
    }


    private Signal determineSignal()
    {
        if (Math.abs(deviationPercent) < 0.5)
        {
            return Signal.AT_VWAP;
        }
        else if (deviationPercent > 2.0)
        {
            return Signal.FAR_ABOVE_VWAP; // Possible mean reversion
        }
        else if (deviationPercent > 0)
        {
            return Signal.ABOVE_VWAP; // Bullish
        }
        else if (deviationPercent < -2.0)
        {
            return Signal.FAR_BELOW_VWAP; // Possible mean reversion
        }
        else
        {
            return Signal.BELOW_VWAP; // Bearish
        }
    }


    public double getVWAP()
    {
        return vwap;
    }


    public double getCurrentPrice()
    {
        return currentPrice;
    }


    public double getDeviationPercent()
    {
        return deviationPercent;
    }


    public Signal getSignal()
    {
        return signal;
    }


    public long getTotalVolume()
    {
        return totalVolume;
    }


    @Override
    public String toString()
    {
        return String.format("VWAP: %.2f | Current: %.2f | Deviation: %.2f%% | Signal: %s | Volume: %,d",
                             vwap,
                             currentPrice,
                             deviationPercent,
                             signal,
                             totalVolume);
    }

    /**
     * Trading signals based on VWAP position
     */
    public enum Signal
    {
        FAR_ABOVE_VWAP, // > +2% - Overbought, expect reversion
        ABOVE_VWAP, // 0% to +2% - Bullish, buyers in control
        AT_VWAP, // ±0.5% - Equilibrium
        BELOW_VWAP, // 0% to -2% - Bearish, sellers in control
        FAR_BELOW_VWAP // < -2% - Oversold, expect reversion
    }

}
