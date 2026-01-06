package com.straube.jones.trader.collectors;


import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.straube.jones.db.DayCounter;
import com.straube.jones.service.IndicatorService;
import com.straube.jones.service.MarketDataService;
import com.straube.jones.trader.dto.DailyPrice;
import com.straube.jones.trader.dto.IndicatorDto;
import com.straube.jones.trader.indicators.IndicatorCalculator;

/**
 * Collector für technische Indikatoren.
 * Berechnet Indikatoren basierend auf historischen Marktdaten.
 */
@Service
public class IndicatorCollector
{
    private static final Logger logger = LoggerFactory.getLogger(IndicatorCollector.class);

    private final MarketDataService marketDataService;
    private final IndicatorCalculator indicatorCalculator;
    private final IndicatorService indicatorService;

    public IndicatorCollector(MarketDataService marketDataService,
                              IndicatorCalculator indicatorCalculator,
                              IndicatorService indicatorService)
    {
        this.marketDataService = marketDataService;
        this.indicatorCalculator = indicatorCalculator;
        this.indicatorService = indicatorService;
    }


    /**
     * Sammelt und berechnet Indikatoren für den angegebenen Zeitraum.
     * 
     * @param symbol Das Aktiensymbol
     * @param startDay Start-Tag (DayCounter oder Timestamp)
     * @param endDay End-Tag (DayCounter oder Timestamp)
     * @return Liste der berechneten Indikatoren
     */
    public List<IndicatorDto> collect(String symbol, long startDay, long endDay)
    {
        // Normalisiere Eingabe auf DayCounter (int)
        long endDayCounter = endDay > 100000 ? DayCounter.get(endDay) : endDay;
        long startDayCounter = startDay > 100000 ? DayCounter.get(startDay) : startDay;

        // Hole Marktdaten bis zum End-Tag
        // MarketDataService liefert Daten absteigend sortiert (neueste zuerst)
        List<DailyPrice> prices = marketDataService.getMarketData(symbol, endDayCounter);

        if (prices == null || prices.isEmpty())
        { return new ArrayList<>(); }

        // Berechne Indikatoren für die gesamte verfügbare Historie
        // Dies ist notwendig, um korrekte Werte für EMA, RSI etc. zu erhalten (Initialisierung)
        List<IndicatorDto> allIndicators = indicatorCalculator.calculateIndicators(symbol, prices);

        // Filtere das Ergebnis auf den gewünschten Zeitraum
        return allIndicators.stream().filter(dto -> {
            long dtoDay = DayCounter.get(dto.getDate());
            return dtoDay >= startDayCounter && dtoDay <= endDayCounter;
        }).collect(Collectors.toList());
    }


    /**
     * Updates indicators for all symbols from their last known day counter to today.
     * If no indicators exist for a symbol, starts from the MAX(dayCounter) of price data.
     */
    @Scheduled(cron = "${indicator.collector.schedule.cron:0 30 6 * * ?}")
    public void updateIndicators()
    {
        logger.info("Starting scheduled update of indicators...");
        long today = DayCounter.get(LocalDate.now());

        // Get MAX(cDayCounter) for each symbol from tIndicators table
        Map<String, Long> maxDayCounterIndicators = indicatorService.getMaxDayCounterPerSymbol();

        // Get MAX(cDayCounter) for each symbol from tPriceData table (fallback)
        Map<String, Long> maxDayCounterPriceData = marketDataService.getMaxDayCounterPerSymbol();

        List<String> symbols = marketDataService.getAllSymbols();
        List<IndicatorDto> allIndicators = new ArrayList<>();

        for (String symbol : symbols)
        {
            // Determine start day: use max from indicators + 1, or fallback to max from price data
            Long maxDayIndicators = maxDayCounterIndicators.get(symbol);
            Long maxDayPriceData = maxDayCounterPriceData.get(symbol);

            long startDay;
            if (maxDayIndicators != null)
            {
                // Indicators exist, continue from next day
                startDay = maxDayIndicators + 1;
            }
            else if (maxDayPriceData != null)
            {
                // No indicators exist, use max day from price data
                startDay = maxDayPriceData;
            }
            else
            {
                // No data available at all, skip this symbol
                logger.warn("No price data available for symbol: {}", symbol);
                continue;
            }

            // Skip if we're already up to date
            if (startDay > today)
            {
                logger.debug("Symbol {} is already up to date (startDay: {}, today: {})",
                             symbol,
                             startDay,
                             today);
                continue;
            }

            logger.info("Processing symbol: {} from day {} to {}", symbol, startDay, today);

            // Collect indicators for the date range
            List<IndicatorDto> indicators = collect(symbol, startDay, today);

            if (!indicators.isEmpty())
            {
                allIndicators.addAll(indicators);

                // Batch save every 500 records to manage memory
                if (allIndicators.size() >= 500)
                {
                    logger.info("Saving batch of {} indicators...", allIndicators.size());
                    indicatorService.upsertIndicators(allIndicators);
                    allIndicators.clear();
                }
            }
        }

        // Save remaining indicators
        if (!allIndicators.isEmpty())
        {
            logger.info("Saving final batch of {} indicators...", allIndicators.size());
            indicatorService.upsertIndicators(allIndicators);
        }

        logger.info("Finished scheduled update of indicators.");
    }


    public static void main(String[] args)
    {
        // Setup Database Connection
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.mariadb.jdbc.Driver");
        dataSource.setUrl("jdbc:mariadb://192.168.178.31:3306/StocksDB");
        dataSource.setUsername("stocksdb");
        dataSource.setPassword("stocksdb");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        MarketDataService marketDataService = new MarketDataService(jdbcTemplate);
        IndicatorService indicatorService = new IndicatorService(jdbcTemplate);
        List<String> symbols = marketDataService.getAllSymbols();
        IndicatorCalculator indicatorCalculator = new IndicatorCalculator();
        IndicatorCollector collector = new IndicatorCollector(marketDataService,
                                                              indicatorCalculator,
                                                              indicatorService);

        collector.updateIndicators();
    }
}
