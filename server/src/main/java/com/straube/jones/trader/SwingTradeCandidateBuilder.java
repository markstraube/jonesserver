package com.straube.jones.trader;


import java.util.ArrayList;
import java.util.List;

import com.straube.jones.trader.dasboard.TradeStatus;
import com.straube.jones.trader.dto.DailyPrice;
import com.straube.jones.trader.dto.SwingTradeCandidate;
import com.straube.jones.trader.filter.EventFilter;
import com.straube.jones.trader.filter.LiquidityFilter;
import com.straube.jones.trader.filter.PullbackFilter;
import com.straube.jones.trader.filter.RiskRewardFilter;
import com.straube.jones.trader.filter.StockTrendFilter;
import com.straube.jones.trader.filter.SupportFilter;
import com.straube.jones.trader.filter.VolumeFilter;
import com.straube.jones.trader.indicators.MovingAverageService;
import com.straube.jones.trader.indicators.RiskRewardService;
import com.straube.jones.trader.indicators.RsiService;
import com.straube.jones.trader.indicators.SupportDetectionService;
import com.straube.jones.trader.indicators.VolumeAnalysisService;

/**
 * Baut einen SwingTradeCandidate aus Kursdaten und Marktdaten.
 * Enthält die komplette Bewertungslogik.
 */
public class SwingTradeCandidateBuilder
{

    private final MovingAverageService maService = new MovingAverageService();
    private final RsiService rsiService = new RsiService();
    private final VolumeAnalysisService volumeService = new VolumeAnalysisService();
    private final SupportDetectionService supportService = new SupportDetectionService();
    private final RiskRewardService riskRewardService = new RiskRewardService();
    

    /**
     * Baut einen Swing-Trading-Kandidaten unter Verwendung von vorbrechneten Indikatoren.
     */
    public SwingTradeCandidate build(String symbol, List<DailyPrice> prices, com.straube.jones.trader.dto.IndicatorDto indicators, SwingTradeConfig config) {
        SwingTradeCandidate candidate = build(symbol, prices, config);
        
        if (indicators != null) {
            // Override calculated values with DB values
            if (indicators.getRsi() != null) {
                candidate.getPullback().setRsi14(indicators.getRsi());
            }
            
            if (indicators.getSupport() != null) {
                candidate.getSupport().setNearestSupportLevel(indicators.getSupport());
                // Recalculate distance
                double lastPrice = prices.get(0).getClose();
                double dist = ((lastPrice - indicators.getSupport()) / lastPrice) * 100;
                candidate.getSupport().setDistanceToSupportPercent(dist);
            }
            
            // Add MACD and Volume info if available in candidate (SwingTradeCandidate might need update)
            // Currently SwingTradeCandidate doesn't seem to have MACD field, but we can add it or put in notes.
        }
        
        return candidate;
    }

    /**
     * Baut einen Swing-Trading-Kandidaten.
     *
     * @param symbol Aktiensymbol (z. B. AAPL)
     * @param prices Historische Tagesdaten (neuester Tag zuerst!)
     * @param config Konfiguration für die Analyseparameter
     */
    public SwingTradeCandidate build(String symbol, List<DailyPrice> prices, SwingTradeConfig config)
    {

        List<String> notes = new ArrayList<>();
        TradeStatus status = TradeStatus.GREEN;

        if (prices == null || prices.size() < 2) {
            SwingTradeCandidate candidate = new SwingTradeCandidate();
            candidate.setSymbol(symbol);
            candidate.setStatus(TradeStatus.RED);
            candidate.setNotes(List.of("Zu wenige historische Daten (min. 2 Tage)"));
            candidate.setEvents(new EventFilter());
            return candidate;
        }

        DailyPrice today = prices.get(0);

        // ─────────────────────────────
        // STUFE 0 – Liquidität (vereinfacht)
        // ─────────────────────────────
        long avgVolume20 = volumeService.calculateAverageVolume(prices, 20);
        if (avgVolume20 < config.getMinAverageVolume())
        {
            notes.add("Zu geringes Handelsvolumen");
            status = TradeStatus.RED;
        }

        LiquidityFilter liquidity = new LiquidityFilter();
        liquidity.setLastPrice(today.getClose());
        liquidity.setAverageDailyVolume20d(avgVolume20);

        // ─────────────────────────────
        // STUFE 2 – Trend
        // ─────────────────────────────
        double sma50 = maService.calculateSMA(prices, config.getSmaFastPeriod());
        double sma200 = maService.calculateSMA(prices, config.getSmaSlowPeriod());
        
        // Check if SMA50 is rising (compare with yesterday)
        double sma50Yesterday = maService.calculateSMA(prices.subList(1, prices.size()), config.getSmaFastPeriod());
        boolean sma50Rising = sma50 > sma50Yesterday;

        boolean trendOk = today.getClose() > sma50 && sma50 > sma200 && sma50Rising;
        if (!trendOk)
        {
            notes.add("Kein stabiler Aufwärtstrend (SMA50 > SMA200 & SMA50 steigend)");
            status = TradeStatus.RED;
        }

        StockTrendFilter trend = new StockTrendFilter();
        trend.setClosePrice(today.getClose());
        trend.setSma50(sma50);
        trend.setSma200(sma200);
        trend.setSma50Rising(sma50Rising);

        // ─────────────────────────────
        // STUFE 3 – Pullback
        // ─────────────────────────────
        double ema20 = maService.calculateEMA(prices, config.getEmaPeriod());
        double distanceToEma = (today.getClose() - ema20) / ema20 * 100;

        double rsi = rsiService.calculateRSI(prices, config.getRsiPeriod());

        boolean pullbackOk = distanceToEma < 0 && distanceToEma > config.getMaxPullbackDistance() 
                && rsi >= config.getMinRsi() && rsi <= config.getMaxRsi();

        if (!pullbackOk && status == TradeStatus.GREEN)
        {
            notes.add("Pullback noch nicht ideal");
            status = TradeStatus.YELLOW;
        }

        PullbackFilter pullback = new PullbackFilter();
        pullback.setEma20(ema20);
        pullback.setDistanceToEma20Percent(distanceToEma);
        pullback.setRsi14(rsi);

        // ─────────────────────────────
        // STUFE 4 - Volume Analysis
        // ─────────────────────────────
        // Check if volume is drying up during pullback
        boolean volumeDryingUp = today.getVolume() < avgVolume20;
        if (!volumeDryingUp && status == TradeStatus.GREEN) {
             notes.add("Volumen beim Pullback nicht rückläufig");
             // status = TradeStatus.YELLOW; // Optional: make it a warning
        }
        
        VolumeFilter volume = new VolumeFilter();
        volume.setAverageVolume20d(avgVolume20);

        // ─────────────────────────────
        // STUFE 5 – Support
        // ─────────────────────────────
        double supportLevel = supportService.findRecentSupport(prices, config.getSupportSearchPeriod());
        double supportDistance = supportService.distanceToSupportPercent(today.getClose(), supportLevel);

        if (supportDistance > config.getMaxSupportDistance())
        {
            notes.add("Kurs zu weit von Unterstützung entfernt");
            status = TradeStatus.YELLOW;
        }

        SupportFilter support = new SupportFilter();
        support.setNearestSupportLevel(supportLevel);
        support.setDistanceToSupportPercent(supportDistance);
        support.setSupportType("Recent Low");

        // ─────────────────────────────
        // STUFE 6 – Risiko & CRV (Beispielwerte)
        // ─────────────────────────────
        double entry = today.getClose();
        double stop = supportLevel * (1 - config.getStopLossBuffer()); 
        double target = entry * config.getTargetProfitFactor(); 

        double risk = riskRewardService.calculateRisk(entry, stop);
        double reward = riskRewardService.calculateReward(entry, target);
        double crv = riskRewardService.calculateCRV(reward, risk);

        if (crv < config.getMinCrv())
        {
            notes.add("CRV unter " + config.getMinCrv());
            status = TradeStatus.RED;
        }

        RiskRewardFilter riskReward = new RiskRewardFilter();
        riskReward.setEntryPrice(entry);
        riskReward.setStopLossPrice(stop);
        riskReward.setTargetPrice(target);
        riskReward.setRiskPerShare(risk);
        riskReward.setRewardPerShare(reward);
        riskReward.setChanceRiskRatio(crv);

        // ─────────────────────────────
        // Ergebnis bauen
        // ─────────────────────────────
        SwingTradeCandidate candidate = new SwingTradeCandidate();
        candidate.setSymbol(symbol);
        candidate.setStatus(status);
        candidate.setNotes(notes);

        candidate.setLiquidity(liquidity);
        candidate.setTrend(trend);
        candidate.setPullback(pullback);
        candidate.setVolume(volume);
        candidate.setSupport(support);
        candidate.setRiskReward(riskReward);

        // Initialize empty EventFilter to avoid NPE
        candidate.setEvents(new EventFilter());

        return candidate;
    }
}
