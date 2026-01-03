package com.straube.jones.trader.collectors;


import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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

    private final MarketDataService marketDataService;
    private final IndicatorCalculator indicatorCalculator;

    public IndicatorCollector(MarketDataService marketDataService, IndicatorCalculator indicatorCalculator)
    {
        this.marketDataService = marketDataService;
        this.indicatorCalculator = indicatorCalculator;
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
        List<String> symbols = marketDataService.getAllSymbols();
        IndicatorCalculator indicatorCalculator = new IndicatorCalculator();
        IndicatorCollector collector = new IndicatorCollector(marketDataService, indicatorCalculator);

        for (String symbol : symbols)
        {
            long endDay = DayCounter.now();
            long startDay = endDay - 300; // Letzte 300 Tage

            System.out.println("Starte Indikator-Berechnung für " + symbol + "...");

            List<IndicatorDto> results = collector.collect(symbol, startDay, endDay);

            IndicatorService indicatorService = new IndicatorService(jdbcTemplate);
            // Speichere Ergebnisse in der Datenbank    
            indicatorService.upsertIndicators(results);

            System.out.println("Berechnung abgeschlossen. Anzahl Datensätze: " + results.size());
        }
    }
}
