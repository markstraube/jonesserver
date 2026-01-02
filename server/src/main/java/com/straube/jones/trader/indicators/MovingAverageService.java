package com.straube.jones.trader.indicators;


import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.straube.jones.db.DayCounter;
import com.straube.jones.service.MarketDataService;
import com.straube.jones.trader.dto.DailyPrice;

/**
 * Berechnet gleitende Durchschnitte aus historischen Schlusskursen.
 */
public class MovingAverageService
{

    /**
     * Simple Moving Average (SMA).
     *
     * Bedeutung:
     * Durchschnittlicher Schlusskurs der letzten N Tage.
     *
     * Formel:
     * SMA = (Summe aller Schlusskurse) / N
     */
    public double calculateSMA(List<DailyPrice> prices, int period)
    {
        return prices.stream()
                     .limit(period)
                     .mapToDouble(DailyPrice::getAdjClose)
                     .average()
                     .orElseThrow(() -> new IllegalArgumentException("Nicht genug Daten"));
    }


    /**
     * Exponential Moving Average (EMA).
     *
     * Bedeutung:
     * Gewichteter Durchschnitt, der jüngere Kurse stärker berücksichtigt.
     *
     * Vereinfachte Berechnung:
     * EMA_today = Close_today × k + EMA_yesterday × (1 − k)
     * mit k = 2 / (period + 1)
     */
    public double calculateEMA(List<DailyPrice> prices, int period)
    {
        double k = 2.0 / (period + 1);
        double ema = prices.get(period - 1).getAdjClose();

        for (int i = period - 2; i >= 0; i-- )
        {
            double price = prices.get(i).getAdjClose();
            ema = price * k + ema * (1 - k);
        }
        return ema;
    }

    /**
     * Konfiguration für Moving Averages
     */
    public static class MAConfig
    {
        // EMA Perioden
        public int emaShortPeriod = 12; // Standard: 12 Tage (kurzfristig)
        public int emaMediumPeriod = 26; // Standard: 26 Tage (mittelfristig)
        public int emaLongPeriod = 50; // Standard: 50 Tage (langfristig)

        // SMA Perioden
        public int smaShortPeriod = 20; // Standard: 20 Tage (kurzfristig)
        public int smaMediumPeriod = 50; // Standard: 50 Tage (mittelfristig)
        public int smaLongPeriod = 200; // Standard: 200 Tage (langfristig)

        // Preis-Quelle
        public PriceSource priceSource = PriceSource.ADJ_CLOSE;

        public enum PriceSource
        {
            ADJ_CLOSE, // Adjustierter Schlusskurs (empfohlen)
            CLOSE // Normaler Schlusskurs
        }

        // Standard-Konstruktor
        public MAConfig()
        {}


        // Konstruktor für Swing Trading (kürzere Perioden)
        public static MAConfig forSwingTrading()
        {
            MAConfig config = new MAConfig();
            config.emaShortPeriod = 8;
            config.emaMediumPeriod = 21;
            config.emaLongPeriod = 50;
            config.smaShortPeriod = 10;
            config.smaMediumPeriod = 30;
            config.smaLongPeriod = 100;
            return config;
        }


        // Konstruktor für Day Trading (sehr kurze Perioden)
        public static MAConfig forDayTrading()
        {
            MAConfig config = new MAConfig();
            config.emaShortPeriod = 5;
            config.emaMediumPeriod = 13;
            config.emaLongPeriod = 26;
            config.smaShortPeriod = 9;
            config.smaMediumPeriod = 20;
            config.smaLongPeriod = 50;
            return config;
        }


        // Konstruktor für Position Trading (längere Perioden)
        public static MAConfig forPositionTrading()
        {
            MAConfig config = new MAConfig();
            config.emaShortPeriod = 20;
            config.emaMediumPeriod = 50;
            config.emaLongPeriod = 100;
            config.smaShortPeriod = 50;
            config.smaMediumPeriod = 100;
            config.smaLongPeriod = 200;
            return config;
        }
    }

    /**
     * Ergebnis mit allen Moving Averages
     */
    public static class MAResult
    {
        // EMA Werte
        public double emaShort;
        public double emaMedium;
        public double emaLong;

        // SMA Werte
        public double smaShort;
        public double smaMedium;
        public double smaLong;

        // Aktueller Preis
        public double currentPrice;

        @Override
        public String toString()
        {
            return String.format("Moving Averages:%n" + "Aktueller Preis: $%.2f%n"
                            + "------------------------%n"
                            + "EMA Short:   $%.2f%n"
                            + "EMA Medium:  $%.2f%n"
                            + "EMA Long:    $%.2f%n"
                            + "------------------------%n"
                            + "SMA Short:   $%.2f%n"
                            + "SMA Medium:  $%.2f%n"
                            + "SMA Long:    $%.2f",
                                 currentPrice,
                                 emaShort,
                                 emaMedium,
                                 emaLong,
                                 smaShort,
                                 smaMedium,
                                 smaLong);
        }
    }

    /**
     * Crossover-Signal
     */
    public enum CrossoverSignal
    {
        STRONG_BUY, // Mehrere bullische Crossovers
        BUY, // Ein bullischer Crossover
        NEUTRAL, // Keine klaren Signale
        SELL, // Ein bearischer Crossover
        STRONG_SELL // Mehrere bearische Crossovers
    }

    /**
     * Crossover-Analyse Ergebnis
     */
    public static class CrossoverAnalysis
    {
        public CrossoverSignal signal;
        public List<String> crossovers;
        public String recommendation;
        public MAResult currentMA;

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("=== CROSSOVER-ANALYSE ===\n");
            sb.append(String.format("Signal: %s%n", signal));
            sb.append(String.format("Empfehlung: %s%n%n", recommendation));

            if (!crossovers.isEmpty())
            {
                sb.append("Erkannte Crossovers:\n");
                for (String crossover : crossovers)
                {
                    sb.append(String.format("  • %s%n", crossover));
                }
                sb.append("\n");
            }

            sb.append(currentMA.toString());
            return sb.toString();
        }
    }

    /**
     * Berechnet Simple Moving Average (SMA)
     * 
     * @param prices Liste der Preise (Index 0 = neuestes Datum)
     * @param period Anzahl der Perioden
     * @param config Konfiguration für Preis-Quelle
     * @return SMA-Wert
     */
    public static double calculateSMA(List<DailyPrice> prices, int period, MAConfig config)
    {
        if (prices == null || prices.size() < period)
        { return 0.0; }

        double sum = 0.0;
        for (int i = 0; i < period; i++ )
        {
            sum += getPrice(prices.get(i), config.priceSource);
        }

        return sum / period;
    }


    /**
     * Berechnet Exponential Moving Average (EMA)
     * 
     * @param prices Liste der Preise (Index 0 = neuestes Datum)
     * @param period Anzahl der Perioden
     * @param config Konfiguration für Preis-Quelle
     * @return EMA-Wert
     */
    public static double calculateEMA(List<DailyPrice> prices, int period, MAConfig config)
    {
        if (prices == null || prices.size() < period)
        { return 0.0; }

        // Schritt 1: Berechne initialen SMA als Startwert
        double sma = calculateSMA(prices, period, config);

        // Schritt 2: Berechne Multiplikator (Glättungsfaktor)
        // Multiplikator = 2 / (period + 1)
        double multiplier = 2.0 / (period + 1);

        // Schritt 3: Berechne EMA von den ältesten zu den neuesten Daten
        double ema = sma;

        // Starte bei period (nach dem initialen SMA-Fenster) und gehe rückwärts zum neuesten Datum
        for (int i = period - 1; i >= 0; i-- )
        {
            double currentPrice = getPrice(prices.get(i), config.priceSource);
            // EMA = (Preis - EMA_vorher) × Multiplikator + EMA_vorher
            ema = (currentPrice - ema) * multiplier + ema;
        }

        return ema;
    }


    /**
     * Berechnet alle Moving Averages für die aktuelle Periode
     * 
     * @param prices Liste der Preise (Index 0 = neuestes Datum)
     * @param config Konfiguration
     * @return MAResult mit allen Werten
     */
    public static MAResult calculateAllMA(List<DailyPrice> prices, MAConfig config)
    {
        MAResult result = new MAResult();

        if (prices == null || prices.isEmpty())
        { return result; }

        result.currentPrice = getPrice(prices.get(0), config.priceSource);

        // Berechne EMAs
        result.emaShort = calculateEMA(prices, config.emaShortPeriod, config);
        result.emaMedium = calculateEMA(prices, config.emaMediumPeriod, config);
        result.emaLong = calculateEMA(prices, config.emaLongPeriod, config);

        // Berechne SMAs
        result.smaShort = calculateSMA(prices, config.smaShortPeriod, config);
        result.smaMedium = calculateSMA(prices, config.smaMediumPeriod, config);
        result.smaLong = calculateSMA(prices, config.smaLongPeriod, config);

        return result;
    }


    /**
     * Analysiert Crossovers und gibt Handelssignale
     * 
     * @param prices Liste der Preise (Index 0 = neuestes Datum, Index 1 = gestern)
     * @param config Konfiguration
     * @return CrossoverAnalysis mit Signalen und Empfehlungen
     */
    public static CrossoverAnalysis analyzeCrossovers(List<DailyPrice> prices, MAConfig config)
    {
        CrossoverAnalysis analysis = new CrossoverAnalysis();
        analysis.crossovers = new ArrayList<>();

        if (prices == null || prices.size() < 2)
        {
            analysis.signal = CrossoverSignal.NEUTRAL;
            analysis.recommendation = "Nicht genug Daten für Analyse";
            return analysis;
        }

        // Berechne aktuelle und gestrige MAs
        MAResult current = calculateAllMA(prices, config);

        // Erstelle gestrige Preisliste (ohne neuesten Tag)
        List<DailyPrice> yesterdayPrices = prices.subList(1, prices.size());
        MAResult yesterday = calculateAllMA(yesterdayPrices, config);

        analysis.currentMA = current;

        int bullishSignals = 0;
        int bearishSignals = 0;

        // 1. Golden Cross / Death Cross (SMA Medium vs SMA Long)
        if (crossedAbove(yesterday.smaMedium, yesterday.smaLong, current.smaMedium, current.smaLong))
        {
            analysis.crossovers.add("GOLDEN CROSS: SMA Medium kreuzt SMA Long nach oben (sehr bullisch!)");
            bullishSignals += 3;
        }
        else if (crossedBelow(yesterday.smaMedium, yesterday.smaLong, current.smaMedium, current.smaLong))
        {
            analysis.crossovers.add("DEATH CROSS: SMA Medium kreuzt SMA Long nach unten (sehr bearisch!)");
            bearishSignals += 3;
        }

        // 2. EMA Short vs EMA Medium (kurzfristiger Trend)
        if (crossedAbove(yesterday.emaShort, yesterday.emaMedium, current.emaShort, current.emaMedium))
        {
            analysis.crossovers.add("EMA Short kreuzt EMA Medium nach oben (bullisch)");
            bullishSignals += 2;
        }
        else if (crossedBelow(yesterday.emaShort, yesterday.emaMedium, current.emaShort, current.emaMedium))
        {
            analysis.crossovers.add("EMA Short kreuzt EMA Medium nach unten (bearisch)");
            bearishSignals += 2;
        }

        // 3. EMA Medium vs EMA Long (mittelfristiger Trend)
        if (crossedAbove(yesterday.emaMedium, yesterday.emaLong, current.emaMedium, current.emaLong))
        {
            analysis.crossovers.add("EMA Medium kreuzt EMA Long nach oben (bullisch)");
            bullishSignals += 2;
        }
        else if (crossedBelow(yesterday.emaMedium, yesterday.emaLong, current.emaMedium, current.emaLong))
        {
            analysis.crossovers.add("EMA Medium kreuzt EMA Long nach unten (bearisch)");
            bearishSignals += 2;
        }

        // 4. Preis vs EMA Short (sofortiger Trend)
        if (crossedAbove(yesterday.currentPrice, yesterday.emaShort, current.currentPrice, current.emaShort))
        {
            analysis.crossovers.add("Preis kreuzt EMA Short nach oben (kurzfristig bullisch)");
            bullishSignals += 1;
        }
        else if (crossedBelow(yesterday.currentPrice,
                              yesterday.emaShort,
                              current.currentPrice,
                              current.emaShort))
        {
            analysis.crossovers.add("Preis kreuzt EMA Short nach unten (kurzfristig bearisch)");
            bearishSignals += 1;
        }

        // 5. SMA Short vs SMA Medium
        if (crossedAbove(yesterday.smaShort, yesterday.smaMedium, current.smaShort, current.smaMedium))
        {
            analysis.crossovers.add("SMA Short kreuzt SMA Medium nach oben (bullisch)");
            bullishSignals += 1;
        }
        else if (crossedBelow(yesterday.smaShort, yesterday.smaMedium, current.smaShort, current.smaMedium))
        {
            analysis.crossovers.add("SMA Short kreuzt SMA Medium nach unten (bearisch)");
            bearishSignals += 1;
        }

        // Zusätzliche Trendbestätigung (keine Crossovers, aber wichtig)
        if (current.emaShort > current.emaMedium && current.emaMedium > current.emaLong)
        {
            analysis.crossovers.add("✓ Aufwärtstrend bestätigt: EMA Short > Medium > Long");
        }
        else if (current.emaShort < current.emaMedium && current.emaMedium < current.emaLong)
        {
            analysis.crossovers.add("✓ Abwärtstrend bestätigt: EMA Short < Medium < Long");
        }

        if (current.currentPrice > current.smaLong)
        {
            analysis.crossovers.add("✓ Preis über langfristigem SMA (langfristig bullisch)");
        }
        else if (current.currentPrice < current.smaLong)
        {
            analysis.crossovers.add("✓ Preis unter langfristigem SMA (langfristig bearisch)");
        }

        // Bestimme Signal basierend auf Score
        int netScore = bullishSignals - bearishSignals;

        if (netScore >= 4)
        {
            analysis.signal = CrossoverSignal.STRONG_BUY;
            analysis.recommendation = "Starkes Kaufsignal! Mehrere bullische Crossovers erkannt.";
        }
        else if (netScore >= 2)
        {
            analysis.signal = CrossoverSignal.BUY;
            analysis.recommendation = "Kaufsignal - Aufwärtstrend entwickelt sich.";
        }
        else if (netScore <= -4)
        {
            analysis.signal = CrossoverSignal.STRONG_SELL;
            analysis.recommendation = "Starkes Verkaufssignal! Mehrere bearische Crossovers erkannt.";
        }
        else if (netScore <= -2)
        {
            analysis.signal = CrossoverSignal.SELL;
            analysis.recommendation = "Verkaufssignal - Abwärtstrend entwickelt sich.";
        }
        else
        {
            analysis.signal = CrossoverSignal.NEUTRAL;
            analysis.recommendation = "Neutral - Keine klaren Signale, abwarten empfohlen.";
        }

        return analysis;
    }


    /**
     * Prüft ob Linie A die Linie B nach oben gekreuzt hat
     */
    private static boolean crossedAbove(double aYesterday, double bYesterday, double aToday, double bToday)
    {
        return aYesterday <= bYesterday && aToday > bToday;
    }


    /**
     * Prüft ob Linie A die Linie B nach unten gekreuzt hat
     */
    private static boolean crossedBelow(double aYesterday, double bYesterday, double aToday, double bToday)
    {
        return aYesterday >= bYesterday && aToday < bToday;
    }


    /**
     * Hilfsfunktion: Holt den Preis basierend auf PriceSource
     */
    private static double getPrice(DailyPrice price, MAConfig.PriceSource source)
    {
        return source == MAConfig.PriceSource.ADJ_CLOSE ? price.getAdjClose() : price.getAdjClose();
    }


    // Beispiel-Verwendung
    public static void main(String[] args)
    {
        String symbol = "TSLA";

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.mariadb.jdbc.Driver");
        dataSource.setUrl("jdbc:mariadb://192.168.178.31:3306/StocksDB");
        dataSource.setUsername("stocksdb");
        dataSource.setPassword("stocksdb");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        MarketDataService marketDataService = new MarketDataService(jdbcTemplate);
        List<DailyPrice> prices = marketDataService.getMarketData(symbol, DayCounter.now());

        System.out.println("=== MOVING AVERAGE ANALYSE ===\n");

        // Test 1: Standard-Konfiguration
        System.out.println("--- STANDARD-KONFIGURATION ---");
        MAConfig standardConfig = new MAConfig();
        MAResult standardMA = calculateAllMA(prices, standardConfig);
        System.out.println(standardMA);
        System.out.println();

        // Test 2: Swing Trading Konfiguration
        System.out.println("--- SWING TRADING KONFIGURATION ---");
        MAConfig swingConfig = MAConfig.forSwingTrading();
        CrossoverAnalysis swingAnalysis = analyzeCrossovers(prices, swingConfig);
        System.out.println(swingAnalysis);
        System.out.println();

        // Test 3: Day Trading Konfiguration
        System.out.println("--- DAY TRADING KONFIGURATION ---");
        MAConfig dayConfig = MAConfig.forDayTrading();
        CrossoverAnalysis dayAnalysis = analyzeCrossovers(prices, dayConfig);
        System.out.println(dayAnalysis);
        System.out.println();

        // Test 4: Einzelne EMA-Berechnung
        System.out.println("--- EINZELNE EMA-BERECHNUNGEN ---");
        double ema12 = calculateEMA(prices, 12, standardConfig);
        double ema26 = calculateEMA(prices, 26, standardConfig);
        double ema50 = calculateEMA(prices, 50, standardConfig);

        System.out.printf("EMA(12): $%.2f%n", ema12);
        System.out.printf("EMA(26): $%.2f%n", ema26);
        System.out.printf("EMA(50): $%.2f%n", ema50);
        System.out.println();

        // Trading-Empfehlung
        System.out.println("=== TRADING-EMPFEHLUNG ===");
        if (swingAnalysis.signal == CrossoverSignal.STRONG_BUY || swingAnalysis.signal == CrossoverSignal.BUY)
        {
            System.out.println("✓ KAUFEN empfohlen");
            System.out.printf("  Entry: ~$%.2f (aktueller Preis)%n", swingAnalysis.currentMA.currentPrice);
            System.out.printf("  Stop-Loss: ~$%.2f (unterhalb EMA Long)%n",
                              swingAnalysis.currentMA.emaLong * 0.98);
        }
        else if (swingAnalysis.signal == CrossoverSignal.STRONG_SELL
                        || swingAnalysis.signal == CrossoverSignal.SELL)
        {
            System.out.println("✗ VERKAUFEN oder Position vermeiden");
            System.out.printf("  Exit: ~$%.2f (aktueller Preis)%n", swingAnalysis.currentMA.currentPrice);
        }
        else
        {
            System.out.println("⊙ ABWARTEN - Keine klaren Signale");
        }
    }

}
