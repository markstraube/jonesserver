package com.straube.jones.trader.dto;

import java.util.List;

import com.straube.jones.trader.dasboard.EventSection;
import com.straube.jones.trader.dasboard.PullbackSection;
import com.straube.jones.trader.dasboard.RiskSection;
import com.straube.jones.trader.dasboard.SupportSection;
import com.straube.jones.trader.dasboard.TradeStatus;
import com.straube.jones.trader.dasboard.TrendSection;

/**
 * Detailansicht eines Swing-Trading-Kandidaten.
 */
public class SwingTradeDetailDto {

    private String symbol;
    private String companyName;

    /** Aktueller Kurs */
    private double lastPrice;

    /** Ampelstatus */
    private TradeStatus status;

    /** Begründungen für den Status */
    private List<String> notes;

    // ───────── Trend ─────────
    private TrendSection trend;

    // ───────── Pullback ─────────
    private PullbackSection pullback;

    // ───────── Support ─────────
    private SupportSection support;

    // ───────── Risiko ─────────
    private RiskSection risk;

    // ───────── Events ─────────
    private EventSection events;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public double getLastPrice() { return lastPrice; }
    public void setLastPrice(double lastPrice) { this.lastPrice = lastPrice; }
    public TradeStatus getStatus() { return status; }
    public void setStatus(TradeStatus status) { this.status = status; }
    public List<String> getNotes() { return notes; }
    public void setNotes(List<String> notes) { this.notes = notes; }
    public TrendSection getTrend() { return trend; }
    public void setTrend(TrendSection trend) { this.trend = trend; }
    public PullbackSection getPullback() { return pullback; }
    public void setPullback(PullbackSection pullback) { this.pullback = pullback; }
    public SupportSection getSupport() { return support; }
    public void setSupport(SupportSection support) { this.support = support; }
    public RiskSection getRisk() { return risk; }
    public void setRisk(RiskSection risk) { this.risk = risk; }
    public EventSection getEvents() { return events; }
    public void setEvents(EventSection events) { this.events = events; }
}
