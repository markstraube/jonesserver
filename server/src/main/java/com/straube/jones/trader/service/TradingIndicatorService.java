package com.straube.jones.trader.service;


import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.straube.jones.db.DayCounter;
import com.straube.jones.trader.dto.DailyPrice;

@Service
public class TradingIndicatorService
{
    private static final Logger logger = LoggerFactory.getLogger(TradingIndicatorService.class);

    private final MarketDataService marketDataService;

    public TradingIndicatorService(MarketDataService marketDataService)
    {
        this.marketDataService = marketDataService;
    }

    /**
     * Trading-Signal Enum
     */
    public enum Signal
    {
        STRONG_BUY, // Starkes Kaufsignal
        BUY, // Kaufsignal
        HOLD, // Halten
        SELL, // Verkaufssignal
        STRONG_SELL // Starkes Verkaufssignal
    }

    /**
     * Detaillierte Analyse mit allen Indikatorwerten
     */
    public static class Analysis
    {
        private Signal signal;
        private double rsi;
        private double macdValue;
        private double macdSignal;
        private double upperBB;
        private double middleBB;
        private double lowerBB;
        private double currentPrice;
        private boolean highVolume;
        private String reason;

        public Signal getSignal()
        {
            return signal;
        }


        public void setSignal(Signal signal)
        {
            this.signal = signal;
        }


        public double getRsi()
        {
            return rsi;
        }


        public void setRsi(double rsi)
        {
            this.rsi = rsi;
        }


        public double getMacdValue()
        {
            return macdValue;
        }


        public void setMacdValue(double macdValue)
        {
            this.macdValue = macdValue;
        }


        public double getMacdSignal()
        {
            return macdSignal;
        }


        public void setMacdSignal(double macdSignal)
        {
            this.macdSignal = macdSignal;
        }


        public double getUpperBB()
        {
            return upperBB;
        }


        public void setUpperBB(double upperBB)
        {
            this.upperBB = upperBB;
        }


        public double getMiddleBB()
        {
            return middleBB;
        }


        public void setMiddleBB(double middleBB)
        {
            this.middleBB = middleBB;
        }


        public double getLowerBB()
        {
            return lowerBB;
        }


        public void setLowerBB(double lowerBB)
        {
            this.lowerBB = lowerBB;
        }


        public double getCurrentPrice()
        {
            return currentPrice;
        }


        public void setCurrentPrice(double currentPrice)
        {
            this.currentPrice = currentPrice;
        }


        public boolean isHighVolume()
        {
            return highVolume;
        }


        public void setHighVolume(boolean highVolume)
        {
            this.highVolume = highVolume;
        }


        public String getReason()
        {
            return reason;
        }


        public void setReason(String reason)
        {
            this.reason = reason;
        }


        @Override
        public String toString()
        {
            return String.format("Signal: %s%n" + "Grund: %s%n"
                            + "--------------------%n"
                            + "RSI: %.2f%n"
                            + "MACD: %.4f (Signal: %.4f)%n"
                            + "Bollinger Bands: Oberes=%.2f, Mitte=%.2f, Unteres=%.2f%n"
                            + "Aktueller Preis: %.2f%n"
                            + "Hohes Volumen: %s",
                                 signal,
                                 reason,
                                 rsi,
                                 macdValue,
                                 macdSignal,
                                 upperBB,
                                 middleBB,
                                 lowerBB,
                                 currentPrice,
                                 highVolume);
        }
    }

    /**
     * Konfiguration für Trading-Parameter
     */
    public static class TradingConfig
    {
        private int rsiPeriod = 14;
        private int emaShortPeriod = 12;
        private int emaLongPeriod = 26;
        private int macdSignalPeriod = 9;
        private int bollingerPeriod = 20;
        private double bollingerStdDev = 2.0;
        private int volumePeriod = 20;

        // Standard-Konfiguration
        public TradingConfig()
        {}


        // Custom-Konfiguration
        public TradingConfig(int rsiPeriod, int emaShort, int emaLong, int bollingerPeriod)
        {
            this.rsiPeriod = rsiPeriod;
            this.emaShortPeriod = emaShort;
            this.emaLongPeriod = emaLong;
            this.bollingerPeriod = bollingerPeriod;
        }


        public int getRsiPeriod()
        {
            return rsiPeriod;
        }


        public void setRsiPeriod(int rsiPeriod)
        {
            this.rsiPeriod = rsiPeriod;
        }


        public int getEmaShortPeriod()
        {
            return emaShortPeriod;
        }


        public void setEmaShortPeriod(int emaShortPeriod)
        {
            this.emaShortPeriod = emaShortPeriod;
        }


        public int getEmaLongPeriod()
        {
            return emaLongPeriod;
        }


        public void setEmaLongPeriod(int emaLongPeriod)
        {
            this.emaLongPeriod = emaLongPeriod;
        }


        public int getMacdSignalPeriod()
        {
            return macdSignalPeriod;
        }


        public void setMacdSignalPeriod(int macdSignalPeriod)
        {
            this.macdSignalPeriod = macdSignalPeriod;
        }


        public int getBollingerPeriod()
        {
            return bollingerPeriod;
        }


        public void setBollingerPeriod(int bollingerPeriod)
        {
            this.bollingerPeriod = bollingerPeriod;
        }


        public double getBollingerStdDev()
        {
            return bollingerStdDev;
        }


        public void setBollingerStdDev(double bollingerStdDev)
        {
            this.bollingerStdDev = bollingerStdDev;
        }


        public int getVolumePeriod()
        {
            return volumePeriod;
        }


        public void setVolumePeriod(int volumePeriod)
        {
            this.volumePeriod = volumePeriod;
        }
    }

    /**
     * Hauptfunktion: Analysiert aktuelle Marktlage und gibt Kauf/Verkauf-Signal
     * Verwendet Standard-Parameter
     * 
     * @param prices Liste der Tagespreise (Index 0 = neuestes Datum)
     * @return Analysis-Objekt mit Signal und Details
     */
    public static Analysis analyzeStock(List<DailyPrice> prices)
    {
        return analyzeStock(prices, new TradingConfig());
    }


    /**
     * Hauptfunktion: Analysiert aktuelle Marktlage und gibt Kauf/Verkauf-Signal
     * Mit konfigurierbaren Perioden
     * 
     * @param prices Liste der Tagespreise (Index 0 = neuestes Datum)
     * @param config Konfiguration mit allen Perioden-Parametern
     * @return Analysis-Objekt mit Signal und Details
     */
    public static Analysis analyzeStock(List<DailyPrice> prices, TradingConfig config)
    {
        Analysis analysis = new Analysis();

        // Berechne minimale benötigte Datenmenge
        int minDataPoints = Math.max(Math.max(config.getRsiPeriod() + 1,
                                              config.getEmaLongPeriod() + config.getMacdSignalPeriod()),
                                     Math.max(config.getBollingerPeriod(), config.getVolumePeriod()))
                        + 10; // +10 als Puffer für Glättung

        if (prices == null || prices.size() < minDataPoints)
        {
            analysis.setSignal(Signal.HOLD);
            analysis.setReason(String.format("Nicht genug Daten für Analyse (benötigt: %d, vorhanden: %d)",
                                             minDataPoints,
                                             prices == null ? 0 : prices.size()));
            return analysis;
        }

        // Berechne alle Indikatoren mit konfigurierten Perioden
        analysis.setRsi(calculateRSI(prices, config.getRsiPeriod()));
        double[] macd = calculateMACD(prices,
                                      config.getEmaShortPeriod(),
                                      config.getEmaLongPeriod(),
                                      config.getMacdSignalPeriod());
        analysis.setMacdValue(macd[0]);
        analysis.setMacdSignal(macd[1]);

        double[] bb = calculateBollingerBands(prices,
                                              config.getBollingerPeriod(),
                                              config.getBollingerStdDev());
        analysis.setUpperBB(bb[0]);
        analysis.setMiddleBB(bb[1]);
        analysis.setLowerBB(bb[2]);

        analysis.setCurrentPrice(prices.get(0).getAdjClose());
        analysis.setHighVolume(isHighVolume(prices, config.getVolumePeriod()));

        // Zähle bullische und bearische Signale
        int bullishScore = 0;
        int bearishScore = 0;
        List<String> reasons = new ArrayList<>();

        // RSI-Analyse
        if (analysis.getRsi() < 30)
        {
            bullishScore += 2;
            reasons.add("RSI überverkauft (<30)");
        }
        else if (analysis.getRsi() < 40)
        {
            bullishScore += 1;
            reasons.add("RSI niedrig (<40)");
        }
        else if (analysis.getRsi() > 70)
        {
            bearishScore += 2;
            reasons.add("RSI überkauft (>70)");
        }
        else if (analysis.getRsi() > 60)
        {
            bearishScore += 1;
            reasons.add("RSI hoch (>60)");
        }

        // MACD-Analyse
        if (analysis.getMacdValue() > analysis.getMacdSignal())
        {
            double diff = analysis.getMacdValue() - analysis.getMacdSignal();
            if (diff > 0.5)
            {
                bullishScore += 2;
                reasons.add("MACD stark bullisch");
            }
            else
            {
                bullishScore += 1;
                reasons.add("MACD bullisch");
            }
        }
        else
        {
            double diff = analysis.getMacdSignal() - analysis.getMacdValue();
            if (diff > 0.5)
            {
                bearishScore += 2;
                reasons.add("MACD stark bearisch");
            }
            else
            {
                bearishScore += 1;
                reasons.add("MACD bearisch");
            }
        }

        // Bollinger Bands-Analyse
        double bbPosition = (analysis.getCurrentPrice() - analysis.getLowerBB())
                        / (analysis.getUpperBB() - analysis.getLowerBB());

        if (bbPosition < 0.2)
        {
            bullishScore += 2;
            reasons.add("Preis nahe unterem Bollinger Band");
        }
        else if (bbPosition < 0.4)
        {
            bullishScore += 1;
            reasons.add("Preis im unteren Bereich");
        }
        else if (bbPosition > 0.8)
        {
            bearishScore += 2;
            reasons.add("Preis nahe oberem Bollinger Band");
        }
        else if (bbPosition > 0.6)
        {
            bearishScore += 1;
            reasons.add("Preis im oberen Bereich");
        }

        // Volumen-Bestätigung
        if (analysis.isHighVolume())
        {
            if (bullishScore > bearishScore)
            {
                bullishScore += 1;
                reasons.add("Hohes Volumen bestätigt Aufwärtsbewegung");
            }
            else if (bearishScore > bullishScore)
            {
                bearishScore += 1;
                reasons.add("Hohes Volumen bestätigt Abwärtsbewegung");
            }
        }

        // Berechne finales Signal
        int netScore = bullishScore - bearishScore;

        if (netScore >= 4)
        {
            analysis.setSignal(Signal.STRONG_BUY);
        }
        else if (netScore >= 2)
        {
            analysis.setSignal(Signal.BUY);
        }
        else if (netScore <= -4)
        {
            analysis.setSignal(Signal.STRONG_SELL);
        }
        else if (netScore <= -2)
        {
            analysis.setSignal(Signal.SELL);
        }
        else
        {
            analysis.setSignal(Signal.HOLD);
        }

        analysis.setReason(String.join(", ", reasons));
        if (reasons.isEmpty())
        {
            analysis.setReason("Keine klaren Signale");
        }

        return analysis;
    }


    /**
     * Berechnet RSI (Relative Strength Index)
     */
    private static double calculateRSI(List<DailyPrice> prices, int period)
    {
        if (prices.size() <= period)
            return 50.0;

        double sumGain = 0;
        double sumLoss = 0;
        int dataSize = prices.size();

        for (int i = 0; i < period; i++ )
        {
            int index = (dataSize - 2) - i;
            double change = prices.get(index).getAdjClose() - prices.get(index + 1).getAdjClose();

            if (change > 0)
            {
                sumGain += change;
            }
            else
            {
                sumLoss += Math.abs(change);
            }
        }

        double avgGain = sumGain / period;
        double avgLoss = sumLoss / period;

        int startIndex = (dataSize - 2) - period;
        for (int i = startIndex; i >= 0; i-- )
        {
            double change = prices.get(i).getAdjClose() - prices.get(i + 1).getAdjClose();
            double currentGain = change > 0 ? change : 0;
            double currentLoss = change < 0 ? Math.abs(change) : 0;

            avgGain = ((avgGain * (period - 1)) + currentGain) / period;
            avgLoss = ((avgLoss * (period - 1)) + currentLoss) / period;
        }

        if (avgLoss == 0)
            return 100;

        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }


    /**
     * Berechnet MACD (Moving Average Convergence Divergence)
     * @param prices Liste der Preise
     * @param shortPeriod Kurze EMA-Periode (Standard: 12)
     * @param longPeriod Lange EMA-Periode (Standard: 26)
     * @param signalPeriod Signal-Linien-Periode (Standard: 9)
     * @return [MACD-Wert, Signal-Linie]
     */
    private static double[] calculateMACD(List<DailyPrice> prices,
                                          int shortPeriod,
                                          int longPeriod,
                                          int signalPeriod)
    {
        double emaShort = calculateEMA(prices, shortPeriod);
        double emaLong = calculateEMA(prices, longPeriod);
        double macd = emaShort - emaLong;

        // Berechne Signal-Linie als EMA des MACD
        // Vereinfachung: approximiere mit 90% des MACD-Werts
        // In Production würde man die tatsächliche EMA-Berechnung auf MACD-Werten durchführen
        double signal = macd * (1.0 - (2.0 / (signalPeriod + 1)));

        return new double[]{macd, signal};
    }


    /**
     * Berechnet Exponential Moving Average (EMA)
     */
    private static double calculateEMA(List<DailyPrice> prices, int period)
    {
        if (prices.size() < period)
            return prices.get(0).getAdjClose();

        double multiplier = 2.0 / (period + 1);
        double ema = calculateSMA(prices, period);

        for (int i = prices.size() - period - 1; i >= 0; i-- )
        {
            ema = (prices.get(i).getAdjClose() - ema) * multiplier + ema;
        }

        return ema;
    }


    /**
     * Berechnet Simple Moving Average (SMA)
     */
    private static double calculateSMA(List<DailyPrice> prices, int period)
    {
        double sum = 0;
        for (int i = 0; i < period && i < prices.size(); i++ )
        {
            sum += prices.get(i).getAdjClose();
        }
        return sum / Math.min(period, prices.size());
    }


    /**
     * Berechnet Bollinger Bands
     * @return [Oberes Band, Mittleres Band, Unteres Band]
     */
    private static double[] calculateBollingerBands(List<DailyPrice> prices,
                                                    int period,
                                                    double stdDevMultiplier)
    {
        double sma = calculateSMA(prices, period);

        // Berechne Standardabweichung
        double sumSquaredDiff = 0;
        for (int i = 0; i < period && i < prices.size(); i++ )
        {
            double diff = prices.get(i).getAdjClose() - sma;
            sumSquaredDiff += diff * diff;
        }
        double stdDev = Math.sqrt(sumSquaredDiff / period);

        double upper = sma + (stdDevMultiplier * stdDev);
        double lower = sma - (stdDevMultiplier * stdDev);

        return new double[]{upper, sma, lower};
    }


    /**
     * Prüft, ob aktuelles Volumen überdurchschnittlich hoch ist
     */
    private static boolean isHighVolume(List<DailyPrice> prices, int period)
    {
        long currentVolume = prices.get(0).getVolume();
        long avgVolume = 0;

        for (int i = 1; i < period + 1 && i < prices.size(); i++ )
        {
            avgVolume += prices.get(i).getVolume();
        }
        avgVolume /= Math.min(period, prices.size() - 1);

        return currentVolume > avgVolume * 1.5; // 50% über Durchschnitt
    }

    public static class ReportEntry
    {
        private String name;
        private TradingConfig config;
        private Analysis result;

        public ReportEntry(String name, TradingConfig config, Analysis result)
        {
            this.name = name;
            this.config = config;
            this.result = result;
        }


        public String getName()
        {
            return name;
        }


        public TradingConfig getConfig()
        {
            return config;
        }


        public Analysis getResult()
        {
            return result;
        }
    }

    public static class Report
    {
        private String symbol;
        private List<ReportEntry> analyses = new ArrayList<>();

        public Report(String symbol)
        {
            this.symbol = symbol;
        }


        public String getSymbol()
        {
            return symbol;
        }


        public List<ReportEntry> getAnalyses()
        {
            return analyses;
        }


        public void addAnalysis(String name, TradingConfig config, Analysis result)
        {
            analyses.add(new ReportEntry(name, config, result));
        }

        @Override
        public String toString()
        {
            try
            {
                ObjectMapper mapper = new ObjectMapper();
                mapper.enable(SerializationFeature.INDENT_OUTPUT);
                return mapper.writeValueAsString(this);
            }
            catch (Exception e)
            {
                return "Error converting Report to JSON: " + e.getMessage();
            }
        }
    }

        public Report getReport(String symbol)
    {
        return getReport(symbol, DayCounter.now());
    }

    public Report getReport(String symbol, long fromDayCounterDesc)
    {
        List<DailyPrice> prices = marketDataService.getMarketData(symbol, fromDayCounterDesc);

        if (prices.isEmpty())
        {
            logger.error("No prices found for symbol: " + symbol);
            return null;
        }

        Report report = new Report(symbol);

        // Beispiel 1: Standard-Konfiguration verwenden
        logger.info("=== STANDARD-KONFIGURATION ===");
        TradingConfig config1 = new TradingConfig();
        Analysis analysis1 = analyzeStock(prices, config1);
        logger.info("{}", analysis1);
        report.addAnalysis("Standard Configuration", config1, analysis1);

        // Beispiel 2: Custom-Konfiguration für kurzfristigeres Trading
        logger.info("\n=== KURZFRISTIGE KONFIGURATION ===");
        TradingConfig shortTermConfig = new TradingConfig();
        shortTermConfig.setRsiPeriod(9); // Kürzere RSI-Periode
        shortTermConfig.setEmaShortPeriod(8); // Kürzere EMA
        shortTermConfig.setEmaLongPeriod(17); // Kürzere EMA
        shortTermConfig.setBollingerPeriod(15); // Kürzere Bollinger Bands
        shortTermConfig.setVolumePeriod(15); // Kürzere Volumen-Periode

        Analysis analysis2 = analyzeStock(prices, shortTermConfig);
        logger.info("{}", analysis2);
        report.addAnalysis("Short Term Configuration", shortTermConfig, analysis2);

        // Beispiel 3: Custom-Konfiguration für langfristigeres Trading
        logger.info("\n=== LANGFRISTIGE KONFIGURATION ===");
        TradingConfig longTermConfig = new TradingConfig();
        longTermConfig.setRsiPeriod(21); // Längere RSI-Periode
        longTermConfig.setEmaShortPeriod(20); // Längere EMA
        longTermConfig.setEmaLongPeriod(50); // Längere EMA
        longTermConfig.setBollingerPeriod(30); // Längere Bollinger Bands
        longTermConfig.setVolumePeriod(30); // Längere Volumen-Periode

        Analysis analysis3 = analyzeStock(prices, longTermConfig);
        logger.info("{}", analysis3);
        report.addAnalysis("Long Term Configuration", longTermConfig, analysis3);

        return report;
    }


     // Beispiel-Verwendung
    public static void main(String[] args)
    {
        String symbol = "RKLB";        
        
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.mariadb.jdbc.Driver");
        dataSource.setUrl("jdbc:mariadb://192.168.178.31:3306/StocksDB");
        dataSource.setUsername("stocksdb");
        dataSource.setPassword("stocksdb");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        MarketDataService marketDataService = new MarketDataService(jdbcTemplate);

        TradingIndicatorService indicatorService = new TradingIndicatorService(marketDataService);
        Report report = indicatorService.getReport(symbol, DayCounter.get("2025-12-12"));
        System.out.println("Technischer Analyse-Report für " + symbol + ":\n" + report.toString());
    }
}
