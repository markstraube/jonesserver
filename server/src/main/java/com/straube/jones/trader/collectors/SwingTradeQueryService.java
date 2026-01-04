package com.straube.jones.trader.collectors;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.straube.jones.service.CompanyService;
import com.straube.jones.service.MarketDataService;
import com.straube.jones.service.MockEventService;
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
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public SwingTradeQueryService(MarketDataService marketDataService,
                                  MockEventService eventService,
                                  CompanyService companyService,
                                  org.springframework.jdbc.core.JdbcTemplate jdbcTemplate)
    {
        this.marketDataService = marketDataService;
        this.eventService = eventService;
        this.companyService = companyService;
        this.candidateBuilder = new SwingTradeCandidateBuilder();
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<com.straube.jones.dto.OnVistaDto> getCandidates() {
        // Get max day counter for ratings
        String maxDaySql = "SELECT MAX(cDayCounter) FROM tRatings";
        Long maxDay = jdbcTemplate.queryForObject(maxDaySql, Long.class);
        
        if (maxDay == null) {
            return new ArrayList<>();
        }

        String sql = "select * from tOnVista where cIsin in (select distinct(cIsin) from tCompany where cSymbol in (select distinct(cSymbol) from tRatings where cDayCounter = ? and (cShort='BUY' or cMid='BUY' or cLong='BUY') )) order by cMarketCapitalization DESC";
        
        return jdbcTemplate.query(sql, new Object[]{maxDay}, (rs, rowNum) -> {
            com.straube.jones.dto.OnVistaDto dto = new com.straube.jones.dto.OnVistaDto();
            dto.setIsin(rs.getString("cIsin"));
            dto.setName(rs.getString("cName"));
            dto.setSymbol(rs.getString("cSymbol"));
            dto.setBranch(rs.getString("cBranch"));
            dto.setSector(rs.getString("cSector"));
            dto.setCountryCode(rs.getString("cCountryCode"));
            dto.setLast(rs.getObject("cLast") != null ? rs.getDouble("cLast") : null);
            dto.setExchange(rs.getString("cExchange"));
            dto.setDateLong(rs.getObject("cDateLong") != null ? rs.getDouble("cDateLong") : null);
            dto.setCurrency(rs.getString("cCurrency"));
            dto.setPerformance(rs.getObject("cPerformance") != null ? rs.getDouble("cPerformance") : null);
            dto.setPerf1Year(rs.getObject("cPerf1Year") != null ? rs.getDouble("cPerf1Year") : null);
            dto.setPerf6Months(rs.getObject("cPerf6Months") != null ? rs.getDouble("cPerf6Months") : null);
            dto.setPerf4Weeks(rs.getObject("cPerf4Weeks") != null ? rs.getDouble("cPerf4Weeks") : null);
            dto.setDividendYield(rs.getObject("cDividendYield") != null ? rs.getDouble("cDividendYield") : null);
            dto.setDividend(rs.getObject("cDividend") != null ? rs.getDouble("cDividend") : null);
            dto.setMarketCapitalization(rs.getObject("cMarketCapitalization") != null ? rs.getDouble("cMarketCapitalization") : null);
            dto.setRiskRating(rs.getObject("cRiskRating") != null ? rs.getDouble("cRiskRating") : null);
            dto.setEmployees(rs.getObject("cEmployees") != null ? rs.getDouble("cEmployees") : null);
            dto.setTurnover(rs.getObject("cTurnover") != null ? rs.getDouble("cTurnover") : null);
            dto.setUpdated(rs.getTimestamp("cUpdated"));
            return dto;
        });
    }



    public List<SwingTradeOverviewDto> getWatchlist(String statusFilter, Double minCrv, Double maxRsi)
    {
        // Get max day counter
        String maxDaySql = "SELECT MAX(cDayCounter) FROM tRatings";
        Long maxDay = jdbcTemplate.queryForObject(maxDaySql, Long.class);
        
        if (maxDay == null) return new ArrayList<>();

        // SQL to get all data
        String sql = "SELECT " +
                "o.cIsin, o.cName, o.cSymbol, o.cLast, o.cSector, o.cCountryCode, o.cMarketCapitalization, " +
                "i.cRSI, i.cMACDvalue, i.cMACDsignal, i.cVolume, i.cSupport, i.cResistance, " +
                "r.cShort, r.cMid, r.cLong " +
                "FROM tOnVista o " +
                "JOIN tCompany c ON o.cIsin = c.cIsin " +
                "JOIN tRatings r ON c.cSymbol = r.cSymbol " +
                "LEFT JOIN tIndicators i ON c.cSymbol = i.cSymbol AND i.cDayCounter = (SELECT MAX(cDayCounter) FROM tIndicators) " +
                "WHERE r.cDayCounter = ? " +
                "AND (r.cShort='BUY' OR r.cMid='BUY' OR r.cLong='BUY') " +
                "ORDER BY o.cMarketCapitalization DESC";

        List<SwingTradeOverviewDto> list = jdbcTemplate.query(sql, new Object[]{maxDay}, (rs, rowNum) -> {
            SwingTradeOverviewDto dto = new SwingTradeOverviewDto();
            dto.setIsin(rs.getString("cIsin"));
            dto.setSymbol(rs.getString("cSymbol"));
            dto.setCompanyName(rs.getString("cName"));
            dto.setLastPrice(rs.getDouble("cLast"));
            dto.setSector(rs.getString("cSector"));
            dto.setCountry(rs.getString("cCountryCode"));
            dto.setMarketCap(rs.getDouble("cMarketCapitalization"));
            
            dto.setRsi(rs.getDouble("cRSI"));
            dto.setMacdValue(rs.getObject("cMACDvalue") != null ? rs.getDouble("cMACDvalue") : null);
            dto.setMacdSignal(rs.getObject("cMACDsignal") != null ? rs.getDouble("cMACDsignal") : null);
            dto.setVolume(rs.getObject("cVolume") != null ? rs.getDouble("cVolume") : null);
            
            double support = rs.getDouble("cSupport");
            double resistance = rs.getDouble("cResistance");
            double last = rs.getDouble("cLast");
            
            if (last > 0) {
                dto.setDistanceToSupportPercent(((last - support) / last) * 100);
            }
            
            // Calculate CRV
            if (last > support && resistance > last) {
                double risk = last - support;
                double chance = resistance - last;
                if (risk > 0) {
                    dto.setChanceRiskRatio(chance / risk);
                }
            }
            
            // Determine status based on ratings
            String s = rs.getString("cShort");
            String m = rs.getString("cMid");
            String l = rs.getString("cLong");
            
            if ("BUY".equals(s) && "BUY".equals(m)) {
                dto.setStatus(com.straube.jones.trader.dasboard.TradeStatus.GREEN);
                dto.setStatusSummary("Strong Buy (Short/Mid)");
            } else if ("BUY".equals(m) || "BUY".equals(l)) {
                dto.setStatus(com.straube.jones.trader.dasboard.TradeStatus.YELLOW);
                dto.setStatusSummary("Buy (Mid/Long)");
            } else {
                dto.setStatus(com.straube.jones.trader.dasboard.TradeStatus.RED);
                dto.setStatusSummary("Watch");
            }
            
            dto.setLastUpdated(LocalDateTime.now().toString());
            
            return dto;
        });

        // Apply filters
        return list.stream()
            .filter(dto -> statusFilter == null || dto.getStatus().name().equalsIgnoreCase(statusFilter))
            .filter(dto -> minCrv == null || dto.getChanceRiskRatio() >= minCrv)
            .filter(dto -> maxRsi == null || dto.getRsi() <= maxRsi)
            .collect(java.util.stream.Collectors.toList());
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
