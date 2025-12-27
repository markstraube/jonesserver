package com.straube.jones.trader.service;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.straube.jones.trader.SwingTradeCandidateBuilder;
import com.straube.jones.trader.SwingTradeConfig;
import com.straube.jones.trader.dasboard.EventSection;
import com.straube.jones.trader.dasboard.PullbackSection;
import com.straube.jones.trader.dasboard.RiskSection;
import com.straube.jones.trader.dasboard.SupportSection;
import com.straube.jones.trader.dasboard.TrendSection;
import com.straube.jones.trader.dto.DailyPrice;
import com.straube.jones.trader.dto.SwingTradeCandidate;
import com.straube.jones.trader.dto.SwingTradeDetailDto;
import com.straube.jones.trader.dto.SwingTradeOverviewDto;
import com.straube.jones.trader.filter.EventFilter;
import com.straube.jones.trader.filter.PullbackFilter;
import com.straube.jones.trader.filter.RiskRewardFilter;
import com.straube.jones.trader.filter.StockTrendFilter;
import com.straube.jones.trader.filter.SupportFilter;

@Service
public class SwingTradeQueryService
{

    private final MarketDataService marketDataService;
    private final MockEventService eventService;
    private final CompanyService companyService;
    private final SwingTradeCandidateBuilder candidateBuilder;

    public SwingTradeQueryService(MarketDataService marketDataService,
                                  MockEventService eventService,
                                  CompanyService companyService)
    {
        this.marketDataService = marketDataService;
        this.eventService = eventService;
        this.companyService = companyService;
        this.candidateBuilder = new SwingTradeCandidateBuilder();
    }


    public List<SwingTradeOverviewDto> getWatchlist(String statusFilter, Double minCrv, Double maxRsi)
    {
        List<String> symbols = companyService.getAllSymbols();
        List<SwingTradeOverviewDto> result = new ArrayList<>();

        com.straube.jones.trader.SwingTradeConfig config = new com.straube.jones.trader.SwingTradeConfig();
        if (minCrv != null) {
            config.setMinCrv(minCrv);
        }
        if (maxRsi != null) {
            config.setMaxRsi(maxRsi);
        }

        for (String symbol : symbols)
        {
            List<DailyPrice> prices = marketDataService.getMarketData(symbol);
            SwingTradeCandidate candidate = candidateBuilder.build(symbol, prices, config);

            // Enrich with events
            EventFilter eventFilter = candidate.getEvents();
            eventFilter.setNextEarningsDate(eventService.getNextEarningsDate(symbol));
            eventFilter.setNextDividendDate(eventService.getNextDividendDate(symbol));
            long days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(),
                                                                   eventFilter.getNextEarningsDate());
            eventFilter.setTradingDaysUntilEarnings((int)days);

            if (statusFilter != null && !candidate.getStatus().name().equalsIgnoreCase(statusFilter))
            {
                continue;
            }
            if (minCrv != null && candidate.getRiskReward().getChanceRiskRatio() < minCrv)
            {
                continue;
            }
            if (maxRsi != null && candidate.getPullback().getRsi14() > maxRsi)
            {
                continue;
            }

            SwingTradeOverviewDto dto = new SwingTradeOverviewDto();
            dto.setSymbol(candidate.getSymbol());
            dto.setCompanyName(companyService.getCompanyName(symbol));
            dto.setLastPrice(prices.get(0).getClose());
            dto.setStatus(candidate.getStatus());
            dto.setStatusSummary(candidate.getNotes().isEmpty() ? "Sauberes Setup"
                            : String.join(", ", candidate.getNotes()));
            dto.setRsi(candidate.getPullback().getRsi14());
            dto.setDistanceToSupportPercent(candidate.getSupport().getDistanceToSupportPercent());
            dto.setChanceRiskRatio(candidate.getRiskReward().getChanceRiskRatio());
            dto.setDaysUntilEarnings(candidate.getEvents().getTradingDaysUntilEarnings());
            dto.setLastUpdated(LocalDateTime.now().toString());

            result.add(dto);
        }
        return result;
    }


    public Optional<SwingTradeDetailDto> getDetail(String symbol)
    {
        List<DailyPrice> prices = marketDataService.getMarketData(symbol);
        if (prices.isEmpty())
            return Optional.empty();

        SwingTradeCandidate candidate = candidateBuilder.build(symbol, prices, new SwingTradeConfig());

        // Enrich with events
        EventFilter eventFilter = candidate.getEvents();
        eventFilter.setNextEarningsDate(eventService.getNextEarningsDate(symbol));
        eventFilter.setNextDividendDate(eventService.getNextDividendDate(symbol));
        long days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(),
                                                               eventFilter.getNextEarningsDate());
        eventFilter.setTradingDaysUntilEarnings((int)days);

        SwingTradeDetailDto dto = new SwingTradeDetailDto();
        dto.setSymbol(candidate.getSymbol());
        dto.setCompanyName(companyService.getCompanyName(symbol));
        dto.setLastPrice(prices.get(0).getClose());
        dto.setStatus(candidate.getStatus());
        dto.setNotes(candidate.getNotes());

        // Map Trend
        TrendSection trend = new TrendSection();
        StockTrendFilter tf = candidate.getTrend();
        trend.setUptrend(tf.isUptrend());
        trend.setSma50(tf.getSma50());
        trend.setSma200(tf.getSma200());
        trend.setExplanation("Trend is " + (tf.isUptrend() ? "UP" : "DOWN"));
        dto.setTrend(trend);

        // Map Pullback
        PullbackSection pullback = new PullbackSection();
        PullbackFilter pf = candidate.getPullback();
        pullback.setDistanceToEma20Percent(pf.getDistanceToEma20Percent());
        pullback.setRsi(pf.getRsi14());
        pullback.setPullbackValid(true); // Simplified
        pullback.setExplanation("RSI is " + pf.getRsi14());
        dto.setPullback(pullback);

        // Map Support
        SupportSection support = new SupportSection();
        SupportFilter sf = candidate.getSupport();
        support.setSupportLevel(sf.getNearestSupportLevel());
        support.setDistanceToSupportPercent(sf.getDistanceToSupportPercent());
        support.setSupportType(sf.getSupportType());
        support.setExplanation("Support at " + sf.getNearestSupportLevel());
        dto.setSupport(support);

        // Map Risk
        RiskSection risk = new RiskSection();
        RiskRewardFilter rf = candidate.getRiskReward();
        risk.setEntryPrice(rf.getEntryPrice());
        risk.setStopLossPrice(rf.getStopLossPrice());
        risk.setTargetPrice(rf.getTargetPrice());
        risk.setRiskPerShare(rf.getRiskPerShare());
        risk.setRewardPerShare(rf.getRewardPerShare());
        risk.setChanceRiskRatio(rf.getChanceRiskRatio());
        risk.setExplanation("CRV: " + rf.getChanceRiskRatio());
        dto.setRisk(risk);

        // Map Events
        EventSection events = new EventSection();
        EventFilter ef = candidate.getEvents();
        events.setEarningsDate(ef.getNextEarningsDate());
        events.setDaysUntilEarnings(ef.getTradingDaysUntilEarnings());
        events.setWarning(ef.getTradingDaysUntilEarnings() < 5 ? "Earnings soon!" : null);
        dto.setEvents(events);

        return Optional.of(dto);
    }
}
