package com.straube.jones.trader.dto;

import java.util.List;

import com.straube.jones.trader.dasboard.TradeStatus;
import com.straube.jones.trader.filter.*;

/**
 * Vollständig bewerteter Swing-Trading-Kandidat.
 * Dieses Objekt wird direkt vom Dashboard konsumiert.
 */
public class SwingTradeCandidate {

    private String symbol;

    private LiquidityFilter liquidity;
    private StockTrendFilter trend;
    private PullbackFilter pullback;
    private VolumeFilter volume;
    private SupportFilter support;
    private RiskRewardFilter riskReward;
    private EventFilter events;

    private TradeStatus status;

    /**
     * Begründungen, warum ein Setup gelb oder rot ist.
     * Wichtig für Transparenz im Dashboard.
     */
    private List<String> notes;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public LiquidityFilter getLiquidity() { return liquidity; }
    public void setLiquidity(LiquidityFilter liquidity) { this.liquidity = liquidity; }
    public StockTrendFilter getTrend() { return trend; }
    public void setTrend(StockTrendFilter trend) { this.trend = trend; }
    public PullbackFilter getPullback() { return pullback; }
    public void setPullback(PullbackFilter pullback) { this.pullback = pullback; }
    public VolumeFilter getVolume() { return volume; }
    public void setVolume(VolumeFilter volume) { this.volume = volume; }
    public SupportFilter getSupport() { return support; }
    public void setSupport(SupportFilter support) { this.support = support; }
    public RiskRewardFilter getRiskReward() { return riskReward; }
    public void setRiskReward(RiskRewardFilter riskReward) { this.riskReward = riskReward; }
    public EventFilter getEvents() { return events; }
    public void setEvents(EventFilter events) { this.events = events; }
    public TradeStatus getStatus() { return status; }
    public void setStatus(TradeStatus status) { this.status = status; }
    public List<String> getNotes() { return notes; }
    public void setNotes(List<String> notes) { this.notes = notes; }
}
