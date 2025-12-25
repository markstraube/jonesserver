package com.straube.jones.trader;

import java.util.ArrayList;
import java.util.List;

import com.straube.jones.trader.dasboard.TradeStatus;
import com.straube.jones.trader.dto.DailyPrice;
import com.straube.jones.trader.dto.SwingTradeCandidate;
import com.straube.jones.trader.filter.*;
import com.straube.jones.trader.service.MovingAverageService;
import com.straube.jones.trader.service.RiskRewardService;
import com.straube.jones.trader.service.RsiService;
import com.straube.jones.trader.service.SupportDetectionService;
import com.straube.jones.trader.service.TrendStructureService;
import com.straube.jones.trader.service.VolumeAnalysisService;

/**
 * Baut einen SwingTradeCandidate aus Kursdaten und Marktdaten.
 * Enthält die komplette Bewertungslogik.
 */
public class SwingTradeCandidateBuilder {

    private final MovingAverageService maService = new MovingAverageService();
    private final RsiService rsiService = new RsiService();
    private final VolumeAnalysisService volumeService = new VolumeAnalysisService();
    private final SupportDetectionService supportService = new SupportDetectionService();
    private final RiskRewardService riskRewardService = new RiskRewardService();
    private final TrendStructureService trendStructureService = new TrendStructureService();

    /**
     * Baut einen Swing-Trading-Kandidaten.
     *
     * @param symbol Aktiensymbol (z. B. AAPL)
     * @param prices Historische Tagesdaten (neuester Tag zuerst!)
     */
    public SwingTradeCandidate build(String symbol, List<DailyPrice> prices) {

        List<String> notes = new ArrayList<>();
        TradeStatus status = TradeStatus.GREEN;

        DailyPrice today = prices.get(0);

        // ─────────────────────────────
        // STUFE 0 – Liquidität (vereinfacht)
        // ─────────────────────────────
        long avgVolume20 = volumeService.calculateAverageVolume(prices, 20);
        if (avgVolume20 < 500_000) {
            notes.add("Zu geringes Handelsvolumen");
            status = TradeStatus.RED;
        }
        
        LiquidityFilter liquidity = new LiquidityFilter();
        liquidity.setLastPrice(today.getClose());
        liquidity.setAverageDailyVolume20d(avgVolume20);

        // ─────────────────────────────
        // STUFE 2 – Trend
        // ─────────────────────────────
        double sma50 = maService.calculateSMA(prices, 50);
        double sma200 = maService.calculateSMA(prices, 200);

        boolean trendOk = today.getClose() > sma50 && sma50 > sma200;
        if (!trendOk) {
            notes.add("Kein stabiler Aufwärtstrend");
            status = TradeStatus.RED;
        }
        
        StockTrendFilter trend = new StockTrendFilter();
        trend.setClosePrice(today.getClose());
        trend.setSma50(sma50);
        trend.setSma200(sma200);
        trend.setSma50Rising(true);

        // ─────────────────────────────
        // STUFE 3 – Pullback
        // ─────────────────────────────
        double ema20 = maService.calculateEMA(prices, 20);
        double distanceToEma =
                (today.getClose() - ema20) / ema20 * 100;

        double rsi = rsiService.calculateRSI(prices, 14);

        boolean pullbackOk =
                distanceToEma < 0 &&
                distanceToEma > -5 &&
                rsi >= 40 &&
                rsi <= 60;

        if (!pullbackOk && status == TradeStatus.GREEN) {
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
        VolumeFilter volume = new VolumeFilter();
        volume.setAverageVolume20d(avgVolume20);

        // ─────────────────────────────
        // STUFE 5 – Support
        // ─────────────────────────────
        double supportLevel = supportService.findRecentSupport(prices, 20);
        double supportDistance =
                supportService.distanceToSupportPercent(today.getClose(), supportLevel);

        if (supportDistance > 3) {
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
        double stop = supportLevel * 0.99;   // 1 % unter Support
        double target = entry * 1.06;   // 6 % Ziel (vereinfachtes Beispiel)

        double risk = riskRewardService.calculateRisk(entry, stop);
        double reward = riskRewardService.calculateReward(entry, target);
        double crv = riskRewardService.calculateCRV(reward, risk);

        if (crv < 2.0) {
            notes.add("CRV unter 2.0");
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
