package com.straube.jones.trader.indicators.vwap;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * VWAP (Volume Weighted Average Price) Analyzer for intraday stock trading analysis.
 * Calculates cumulative VWAP from market open and provides trading signals.
 */
public class VWAPAnalyzer
{

    private final List<PricePoint> priceHistory;
    private double cumulativeTPVolume; // Cumulative (Typical Price × Volume)
    private long cumulativeVolume;

    public VWAPAnalyzer()
    {
        this.priceHistory = new ArrayList<>();
        this.cumulativeTPVolume = 0.0;
        this.cumulativeVolume = 0;
    }


    /**
     * Add a new price point (called every minute)
     */
    public void addPricePoint(PricePoint point)
    {
        priceHistory.add(point);

        double typicalPrice = point.getTypicalPrice();
        cumulativeTPVolume += typicalPrice * point.getVolume();
        cumulativeVolume += point.getVolume();
    }


    /**
     * Add price point with only close price (simplified version)
     */
    public void addPricePoint(LocalDateTime timestamp, double close, long volume)
    {
        addPricePoint(new PricePoint(timestamp, close, volume));
    }


    /**
     * Calculate current VWAP and generate signal
     */
    public VWAPResult calculateVWAP()
    {
        if (cumulativeVolume == 0)
        { throw new IllegalStateException("No price data available"); }

        double vwap = cumulativeTPVolume / cumulativeVolume;
        double currentPrice = priceHistory.get(priceHistory.size() - 1).getClose();

        return new VWAPResult(vwap, currentPrice, cumulativeVolume);
    }


    /**
     * Reset for new trading day (call at market open)
     */
    public void resetForNewDay()
    {
        priceHistory.clear();
        cumulativeTPVolume = 0.0;
        cumulativeVolume = 0;
    }


    /**
     * Get all price points
     */
    public List<PricePoint> getPriceHistory()
    {
        return new ArrayList<>(priceHistory);
    }


    /**
     * Check if price is touching/near VWAP (within threshold)
     */
    public boolean isTouchingVWAP(double thresholdPercent)
    {
        VWAPResult result = calculateVWAP();
        return Math.abs(result.getDeviationPercent()) <= thresholdPercent;
    }


    /**
     * Analyze RKLB day-trading pattern
     */
    public DayTradingAnalysis analyzeDayTradingPattern()
    {
        if (priceHistory.isEmpty())
        { return null; }

        VWAPResult current = calculateVWAP();

        // Analyze opening behavior (first 30 minutes)
        int firstHalfHour = Math.min(30, priceHistory.size());
        double openPrice = priceHistory.get(0).getClose();
        double minPriceFirst30Min = Double.MAX_VALUE;

        for (int i = 0; i < firstHalfHour; i++ )
        {
            minPriceFirst30Min = Math.min(minPriceFirst30Min, priceHistory.get(i).getClose());
        }

        boolean morningDumpBelowOpen = minPriceFirst30Min < openPrice;
        double morningDumpPercent = ((minPriceFirst30Min - openPrice) / openPrice) * 100.0;

        // Count VWAP touches
        int vwapTouches = 0;
        for (PricePoint point : priceHistory)
        {
            double deviation = Math.abs(((point.getClose() - current.getVWAP()) / current.getVWAP()) * 100.0);
            if (deviation < 0.5)
            { // Within 0.5% of VWAP
                vwapTouches++ ;
            }
        }

        return new DayTradingAnalysis(morningDumpBelowOpen, morningDumpPercent, vwapTouches, current);
    }

    /**
     * Day trading pattern analysis result
     */
    public static class DayTradingAnalysis
    {
        private final boolean morningDumpOccurred;
        private final double morningDumpPercent;
        private final int vwapTouchCount;
        private final VWAPResult currentVWAP;

        public DayTradingAnalysis(boolean morningDump, double dumpPercent, int touches, VWAPResult vwap)
        {
            this.morningDumpOccurred = morningDump;
            this.morningDumpPercent = dumpPercent;
            this.vwapTouchCount = touches;
            this.currentVWAP = vwap;
        }


        public boolean hadMorningDump()
        {
            return morningDumpOccurred;
        }


        public double getMorningDumpPercent()
        {
            return morningDumpPercent;
        }


        public int getVWAPTouchCount()
        {
            return vwapTouchCount;
        }


        public VWAPResult getCurrentVWAP()
        {
            return currentVWAP;
        }


        @Override
        public String toString()
        {
            return String.format("Day Trading Analysis:\n" + "  Morning Dump: %s (%.2f%%)\n"
                            + "  VWAP Touches: %d\n"
                            + "  %s",
                                 morningDumpOccurred ? "YES" : "NO",
                                 morningDumpPercent,
                                 vwapTouchCount,
                                 currentVWAP);
        }
    }

    // Example usage
    public static void main(String[] args)
    {
        VWAPAnalyzer analyzer = new VWAPAnalyzer();

        // Simulate RKLB trading day (starting at 15:30 CET / Nasdaq open)
        LocalDateTime start = LocalDateTime.of(2026, 1, 16, 15, 30);

        // Example: Add minute-by-minute data
        analyzer.addPricePoint(start, 23.50, 500000);
        analyzer.addPricePoint(start.plusMinutes(1), 23.45, 450000); // Morning dump
        analyzer.addPricePoint(start.plusMinutes(2), 23.40, 600000); // Continues down
        analyzer.addPricePoint(start.plusMinutes(5), 23.48, 400000); // Recovery
        analyzer.addPricePoint(start.plusMinutes(10), 23.52, 350000); // Above VWAP

        // Get current VWAP
        VWAPResult result = analyzer.calculateVWAP();
        System.out.println(result);

        // Analyze day trading pattern
        DayTradingAnalysis analysis = analyzer.analyzeDayTradingPattern();
        if (analysis != null)
        {
            System.out.println("\n" + analysis);
        }
    }
}
