package com.straube.jones.trader;


import java.util.ArrayList;
import java.util.List;

import com.straube.jones.trader.dto.IndicatorDto;

public class TradingStrategyAnalyzer
{
    /**
     * Gesamtbewertung einer Aktie
     */
    public static class StrategyAnalysis
    {
        private String symbol;
        private long date;
        private double currentPrice;

        // Swing Trading Analyse
        private SwingTradingScore swingTrading;

        // Momentum Strategie Analyse
        private MomentumScore momentum;

        // Gesamtempfehlung
        private String overallRecommendation;
        private int confidenceScore; // 0-100

        public String getSymbol()
        {
            return symbol;
        }


        public void setSymbol(String symbol)
        {
            this.symbol = symbol;
        }


        public long getDate()
        {
            return date;
        }


        public void setDate(long date)
        {
            this.date = date;
        }


        public double getCurrentPrice()
        {
            return currentPrice;
        }


        public void setCurrentPrice(double currentPrice)
        {
            this.currentPrice = currentPrice;
        }


        public SwingTradingScore getSwingTrading()
        {
            return swingTrading;
        }


        public void setSwingTrading(SwingTradingScore swingTrading)
        {
            this.swingTrading = swingTrading;
        }


        public MomentumScore getMomentum()
        {
            return momentum;
        }


        public void setMomentum(MomentumScore momentum)
        {
            this.momentum = momentum;
        }


        public String getOverallRecommendation()
        {
            return overallRecommendation;
        }


        public void setOverallRecommendation(String overallRecommendation)
        {
            this.overallRecommendation = overallRecommendation;
        }


        public int getConfidenceScore()
        {
            return confidenceScore;
        }


        public void setConfidenceScore(int confidenceScore)
        {
            this.confidenceScore = confidenceScore;
        }


        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=== ANALYSE FÜR %s (Preis: %.2f) ===%n%n", symbol, currentPrice));
            sb.append(swingTrading.toString()).append("\n\n");
            sb.append(momentum.toString()).append("\n\n");
            sb.append(String.format("GESAMTEMPFEHLUNG: %s (Konfidenz: %d/100)%n",
                                    overallRecommendation,
                                    confidenceScore));
            return sb.toString();
        }
    }

    /**
     * Swing Trading Bewertung
     */
    public static class SwingTradingScore
    {
        private String signal; // KAUFEN, HALTEN, VERKAUFEN
        private int score; // 0-100
        private String pricePosition; // Wo steht der Preis in der Range?
        private String macdSignal;
        private String rsiSignal;
        private String bollingerSignal;
        private String supportResistanceSignal;
        private List<String> reasons = new ArrayList<>();

        public String getSignal()
        {
            return signal;
        }


        public void setSignal(String signal)
        {
            this.signal = signal;
        }


        public int getScore()
        {
            return score;
        }


        public void setScore(int score)
        {
            this.score = score;
        }


        public String getPricePosition()
        {
            return pricePosition;
        }


        public void setPricePosition(String pricePosition)
        {
            this.pricePosition = pricePosition;
        }


        public String getMacdSignal()
        {
            return macdSignal;
        }


        public void setMacdSignal(String macdSignal)
        {
            this.macdSignal = macdSignal;
        }


        public String getRsiSignal()
        {
            return rsiSignal;
        }


        public void setRsiSignal(String rsiSignal)
        {
            this.rsiSignal = rsiSignal;
        }


        public String getBollingerSignal()
        {
            return bollingerSignal;
        }


        public void setBollingerSignal(String bollingerSignal)
        {
            this.bollingerSignal = bollingerSignal;
        }


        public String getSupportResistanceSignal()
        {
            return supportResistanceSignal;
        }


        public void setSupportResistanceSignal(String supportResistanceSignal)
        {
            this.supportResistanceSignal = supportResistanceSignal;
        }


        public List<String> getReasons()
        {
            return reasons;
        }


        public void setReasons(List<String> reasons)
        {
            this.reasons = reasons;
        }


        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("--- SWING TRADING ANALYSE ---\n");
            sb.append(String.format("Signal: %s (Score: %d/100)%n", signal, score));
            sb.append(String.format("Preisposition: %s%n", pricePosition));
            sb.append(String.format("MACD: %s%n", macdSignal));
            sb.append(String.format("RSI: %s%n", rsiSignal));
            sb.append(String.format("Bollinger Bänder: %s%n", bollingerSignal));
            sb.append(String.format("Support/Resistance: %s%n", supportResistanceSignal));
            sb.append("Begründung:\n");
            for (String reason : reasons)
            {
                sb.append("  • ").append(reason).append("\n");
            }
            return sb.toString();
        }
    }

    /**
     * Momentum Strategie Bewertung
     */
    public static class MomentumScore
    {
        private String signal; // KAUFEN, HALTEN, VERKAUFEN
        private int score; // 0-100
        private String trendStrength;
        private String trendDirection;
        private String relativeStrength;
        private String momentumQuality;
        private List<String> reasons = new ArrayList<>();

        public String getSignal()
        {
            return signal;
        }


        public void setSignal(String signal)
        {
            this.signal = signal;
        }


        public int getScore()
        {
            return score;
        }


        public void setScore(int score)
        {
            this.score = score;
        }


        public String getTrendStrength()
        {
            return trendStrength;
        }


        public void setTrendStrength(String trendStrength)
        {
            this.trendStrength = trendStrength;
        }


        public String getTrendDirection()
        {
            return trendDirection;
        }


        public void setTrendDirection(String trendDirection)
        {
            this.trendDirection = trendDirection;
        }


        public String getRelativeStrength()
        {
            return relativeStrength;
        }


        public void setRelativeStrength(String relativeStrength)
        {
            this.relativeStrength = relativeStrength;
        }


        public String getMomentumQuality()
        {
            return momentumQuality;
        }


        public void setMomentumQuality(String momentumQuality)
        {
            this.momentumQuality = momentumQuality;
        }


        public List<String> getReasons()
        {
            return reasons;
        }


        public void setReasons(List<String> reasons)
        {
            this.reasons = reasons;
        }


        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("--- MOMENTUM STRATEGIE ANALYSE ---\n");
            sb.append(String.format("Signal: %s (Score: %d/100)%n", signal, score));
            sb.append(String.format("Trendstärke: %s%n", trendStrength));
            sb.append(String.format("Trendrichtung: %s%n", trendDirection));
            sb.append(String.format("Relative Stärke: %s%n", relativeStrength));
            sb.append(String.format("Momentum-Qualität: %s%n", momentumQuality));
            sb.append("Begründung:\n");
            for (String reason : reasons)
            {
                sb.append("  • ").append(reason).append("\n");
            }
            return sb.toString();
        }
    }

    /**
     * Hauptanalyse-Methode
     * 
     * @param indicators Liste aller Indikatoren inkl. heute (Index 0 = neuester/heute, absteigend sortiert)
     *            Mindestens 5-10 Tage für aussagekräftige Analyse
     * @param currentPrice Aktueller Aktienkurs
     * @return Vollständige Strategie-Analyse
     */
    public static StrategyAnalysis analyzeStock(List<IndicatorDto> indicators, double currentPrice)
    {
        if (indicators == null || indicators.isEmpty())
        { throw new IllegalArgumentException("Indicators list cannot be null or empty"); }

        IndicatorDto current = indicators.get(0); // Neueste Daten = heute

        StrategyAnalysis analysis = new StrategyAnalysis();
        analysis.setSymbol(current.getSymbol());
        analysis.setDate(current.getDate());
        analysis.setCurrentPrice(currentPrice);

        // Swing Trading Analyse
        analysis.setSwingTrading(analyzeSwingTrading(current, indicators, currentPrice));

        // Momentum Analyse
        analysis.setMomentum(analyzeMomentum(current, indicators, currentPrice));

        // Gesamtempfehlung
        generateOverallRecommendation(analysis);

        return analysis;
    }


    /**
     * SWING TRADING ANALYSE Fokus: Kurzfristige Preisbewegungen, Überkauft/Überverkauft, Mean Reversion
     * 
     * @param current Aktuelle Indikatoren (heute)
     * @param indicators Alle Indikatoren inkl. heute (Index 0 = neuester)
     * @param currentPrice Aktueller Kurs
     */
    private static SwingTradingScore analyzeSwingTrading(IndicatorDto current,
                                                         List<IndicatorDto> indicators,
                                                         double currentPrice)
    {
        SwingTradingScore score = new SwingTradingScore();
        int points = 0;

        // 1. BOLLINGER BÄNDER (20 Punkte)
        double bbRange = current.getBb15high() - current.getBb15low();
        double bbPosition = (currentPrice - current.getBb15low()) / bbRange;

        if (currentPrice <= current.getBb15low() * 1.01) // Nahe unterem Band
        {
            score.setBollingerSignal("KAUFSIGNAL - Preis am unteren Band (Überverkauft)");
            points += 20;
            score.getReasons().add("Preis berührt unteres Bollinger Band - typisches Swing-Kaufsignal");
        }
        else if (currentPrice >= current.getBb15high() * 0.99) // Nahe oberem Band
        {
            score.setBollingerSignal("VERKAUFSSIGNAL - Preis am oberen Band (Überkauft)");
            points -= 15;
            score.getReasons().add("Preis berührt oberes Bollinger Band - Gewinnmitnahme empfohlen");
        }
        else if (bbPosition > 0.3 && bbPosition < 0.7) // Mittlerer Bereich
        {
            score.setBollingerSignal("NEUTRAL - Preis in mittlerer Range");
            points += 5;
        }
        else
        {
            score.setBollingerSignal(String.format("BEOBACHTEN - Preis bei %.0f%% der BB-Range",
                                                   bbPosition * 100));
        }

        score.setPricePosition(String.format("%.1f%% der Bollinger Band Range (%.2f - %.2f)",
                                             bbPosition * 100,
                                             current.getBb15low(),
                                             current.getBb15high()));

        // 2. RSI ANALYSE (25 Punkte)
        if (current.getRsi() < 30)
        {
            score.setRsiSignal(String.format("STARK ÜBERVERKAUFT (RSI: %.1f) - Starkes Kaufsignal",
                                             current.getRsi()));
            points += 25;
            score.getReasons()
                 .add(String.format("RSI bei %.1f - deutlich überverkauft, Erholungspotenzial hoch",
                                    current.getRsi()));
        }
        else if (current.getRsi() < 40)
        {
            score.setRsiSignal(String.format("ÜBERVERKAUFT (RSI: %.1f) - Kaufsignal", current.getRsi()));
            points += 15;
            score.getReasons().add("RSI unter 40 - überverkaufter Bereich");
        }
        else if (current.getRsi() > 70)
        {
            score.setRsiSignal(String.format("STARK ÜBERKAUFT (RSI: %.1f) - Verkaufssignal",
                                             current.getRsi()));
            points -= 20;
            score.getReasons()
                 .add(String.format("RSI bei %.1f - stark überkauft, Korrektur wahrscheinlich",
                                    current.getRsi()));
        }
        else if (current.getRsi() > 60)
        {
            score.setRsiSignal(String.format("ÜBERKAUFT (RSI: %.1f) - Vorsicht", current.getRsi()));
            points -= 10;
            score.getReasons().add("RSI über 60 - überkaufter Bereich erreicht");
        }
        else if (current.getRsi() >= 45 && current.getRsi() <= 55)
        {
            score.setRsiSignal(String.format("NEUTRAL (RSI: %.1f)", current.getRsi()));
            points += 10;
            score.getReasons().add("RSI im neutralen Bereich");
        }
        else
        {
            score.setRsiSignal(String.format("RSI: %.1f", current.getRsi()));
            points += 5;
        }

        // 3. MACD CROSSOVER (20 Punkte)
        double macdDiff = current.getMacdValue() - current.getMacdSignal();
        boolean bullishMACD = macdDiff > 0;

        // Prüfe auf Crossover (falls genug History vorhanden)
        if (indicators.size() >= 2)
        {
            IndicatorDto previous = indicators.get(1); // Gestern
            double prevMACDDiff = previous.getMacdValue() - previous.getMacdSignal();

            if (macdDiff > 0 && prevMACDDiff <= 0)
            {
                score.setMacdSignal("BULLISH CROSSOVER - Starkes Kaufsignal");
                points += 20;
                score.getReasons()
                     .add("MACD hat gerade Signal-Linie nach oben gekreuzt - frisches Kaufsignal");
            }
            else if (macdDiff < 0 && prevMACDDiff >= 0)
            {
                score.setMacdSignal("BEARISH CROSSOVER - Verkaufssignal");
                points -= 15;
                score.getReasons().add("MACD hat Signal-Linie nach unten gekreuzt - Verkaufssignal");
            }
            else if (bullishMACD)
            {
                score.setMacdSignal(String.format("BULLISH (MACD: %.2f > Signal: %.2f)",
                                                  current.getMacdValue(),
                                                  current.getMacdSignal()));
                points += 10;
            }
            else
            {
                score.setMacdSignal(String.format("BEARISH (MACD: %.2f < Signal: %.2f)",
                                                  current.getMacdValue(),
                                                  current.getMacdSignal()));
                points -= 5;
            }
        }
        else
        {
            score.setMacdSignal(bullishMACD ? "BULLISH" : "BEARISH");
            points += bullishMACD ? 10 : -5;
        }

        // 4. SUPPORT/RESISTANCE (15 Punkte)
        double distanceToSupport = ((currentPrice - current.getSupport()) / current.getSupport()) * 100;
        double distanceToResistance = ((current.getResistance() - currentPrice) / currentPrice) * 100;

        if (distanceToSupport < 2) // Nahe Support
        {
            score.setSupportResistanceSignal(String.format("NAH AM SUPPORT (%.2f) - Kaufzone",
                                                           current.getSupport()));
            points += 15;
            score.getReasons()
                 .add(String.format("Preis nur %.1f%% über Support - gutes Risiko/Rendite-Verhältnis",
                                    distanceToSupport));
        }
        else if (distanceToResistance < 2) // Nahe Resistance
        {
            score.setSupportResistanceSignal(String.format("NAH AM WIDERSTAND (%.2f) - Gewinnmitnahme",
                                                           current.getResistance()));
            points -= 10;
            score.getReasons()
                 .add(String.format("Preis nur %.1f%% unter Resistance - begrenztes Aufwärtspotenzial",
                                    distanceToResistance));
        }
        else
        {
            score.setSupportResistanceSignal(String.format("Mitte der Range (Support: %.2f, Resistance: %.2f)",
                                                           current.getSupport(),
                                                           current.getResistance()));
            points += 5;
        }

        // 5. MOVING AVERAGE CROSSOVERS (20 Punkte)
        if (current.getEma5() > current.getEma10() && current.getEma10() > current.getEma20())
        {
            points += 10;
            score.getReasons().add("Positive EMA-Staffelung (5>10>20) - bullischer Kurzfristtrend");
        }
        else if (current.getEma5() < current.getEma10() && current.getEma10() < current.getEma20())
        {
            points -= 10;
            score.getReasons().add("Negative EMA-Staffelung (5<10<20) - bearischer Kurzfristtrend");
        }

        // Preis-Position zu EMAs
        if (currentPrice > current.getEma10())
        {
            points += 5;
            score.getReasons().add("Preis über EMA10 - kurzfristiger Aufwärtstrend intakt");
        }
        else
        {
            points -= 5;
        }

        // SCORE BERECHNUNG (0-100)
        score.setScore(Math.max(0, Math.min(100, 50 + points)));

        // SIGNAL ABLEITEN
        if (score.getScore() >= 70)
        {
            score.setSignal("KAUFEN");
        }
        else if (score.getScore() >= 50)
        {
            score.setSignal("HALTEN / BEOBACHTEN");
        }
        else if (score.getScore() >= 30)
        {
            score.setSignal("VORSICHT / REDUZIEREN");
        }
        else
        {
            score.setSignal("VERKAUFEN");
        }

        return score;
    }


    /**
     * MOMENTUM STRATEGIE ANALYSE Fokus: Trendfolge, relative Stärke, Fortsetzung starker Bewegungen
     * 
     * @param current Aktuelle Indikatoren (heute)
     * @param indicators Alle Indikatoren inkl. heute (Index 0 = neuester)
     * @param currentPrice Aktueller Kurs
     */
    private static MomentumScore analyzeMomentum(IndicatorDto current,
                                                 List<IndicatorDto> indicators,
                                                 double currentPrice)
    {
        MomentumScore score = new MomentumScore();
        int points = 0;

        // 1. ADX - TRENDSTÄRKE (30 Punkte)
        if (current.getAdx() > 40)
        {
            score.setTrendStrength(String.format("SEHR STARK (ADX: %.1f)", current.getAdx()));
            points += 30;
            score.getReasons()
                 .add(String.format("ADX bei %.1f - sehr starker Trend vorhanden", current.getAdx()));
        }
        else if (current.getAdx() > 25)
        {
            score.setTrendStrength(String.format("STARK (ADX: %.1f)", current.getAdx()));
            points += 20;
            score.getReasons().add(String.format("ADX bei %.1f - klarer Trend etabliert", current.getAdx()));
        }
        else if (current.getAdx() > 20)
        {
            score.setTrendStrength(String.format("MODERAT (ADX: %.1f)", current.getAdx()));
            points += 10;
            score.getReasons().add("ADX über 20 - Trend beginnt sich zu bilden");
        }
        else
        {
            score.setTrendStrength(String.format("SCHWACH (ADX: %.1f) - KEIN TREND", current.getAdx()));
            points -= 20;
            score.getReasons().add("ADX unter 20 - kein klarer Trend, Momentum-Strategie ungeeignet");
        }

        // 2. TRENDRICHTUNG (+DI vs -DI) (20 Punkte)
        if (current.getAdxPlusDI() > current.getAdxMinusDI())
        {
            double diSpread = current.getAdxPlusDI() - current.getAdxMinusDI();
            if (diSpread > 10)
            {
                score.setTrendDirection(String.format("STARK AUFWÄRTS (+DI: %.1f, -DI: %.1f)",
                                                      current.getAdxPlusDI(),
                                                      current.getAdxMinusDI()));
                points += 20;
                score.getReasons().add("Deutliche Dominanz von +DI - starker Aufwärtstrend");
            }
            else
            {
                score.setTrendDirection(String.format("AUFWÄRTS (+DI: %.1f, -DI: %.1f)",
                                                      current.getAdxPlusDI(),
                                                      current.getAdxMinusDI()));
                points += 10;
            }
        }
        else
        {
            double diSpread = current.getAdxMinusDI() - current.getAdxPlusDI();
            if (diSpread > 10)
            {
                score.setTrendDirection(String.format("STARK ABWÄRTS (+DI: %.1f, -DI: %.1f)",
                                                      current.getAdxPlusDI(),
                                                      current.getAdxMinusDI()));
                points -= 20;
                score.getReasons().add("Deutliche Dominanz von -DI - starker Abwärtstrend");
            }
            else
            {
                score.setTrendDirection(String.format("ABWÄRTS (+DI: %.1f, -DI: %.1f)",
                                                      current.getAdxPlusDI(),
                                                      current.getAdxMinusDI()));
                points -= 10;
            }
        }

        // 3. RELATIVE STÄRKE NACH LEVY (25 Punkte)
        if (current.getRsl() > 1.10)
        {
            score.setRelativeStrength(String.format("SEHR STARK (RSL: %.3f) - Deutliche Outperformance",
                                                    current.getRsl()));
            points += 25;
            score.getReasons()
                 .add(String.format("RSL bei %.3f - Aktie übertrifft Benchmark um >10%%", current.getRsl()));
        }
        else if (current.getRsl() > 1.05)
        {
            score.setRelativeStrength(String.format("STARK (RSL: %.3f) - Outperformance", current.getRsl()));
            points += 15;
            score.getReasons()
                 .add(String.format("RSL bei %.3f - Aktie übertrifft Benchmark um ~5-10%%",
                                    current.getRsl()));
        }
        else if (current.getRsl() > 1.00)
        {
            score.setRelativeStrength(String.format("POSITIV (RSL: %.3f) - Leichte Outperformance",
                                                    current.getRsl()));
            points += 5;
            score.getReasons().add("RSL über 1.0 - leichte Outperformance gegenüber Benchmark");
        }
        else if (current.getRsl() > 0.95)
        {
            score.setRelativeStrength(String.format("NEUTRAL (RSL: %.3f)", current.getRsl()));
            points -= 5;
        }
        else
        {
            score.setRelativeStrength(String.format("SCHWACH (RSL: %.3f) - Underperformance",
                                                    current.getRsl()));
            points -= 15;
            score.getReasons()
                 .add(String.format("RSL bei %.3f - Aktie hinkt Benchmark hinterher", current.getRsl()));
        }

        // 4. RATE OF CHANGE (15 Punkte)
        if (current.getRoc() > 15)
        {
            points += 15;
            score.getReasons()
                 .add(String.format("ROC bei %.1f%% - sehr starkes kurzfristiges Momentum",
                                    current.getRoc()));
        }
        else if (current.getRoc() > 10)
        {
            points += 10;
            score.getReasons()
                 .add(String.format("ROC bei %.1f%% - starkes kurzfristiges Momentum", current.getRoc()));
        }
        else if (current.getRoc() > 5)
        {
            points += 5;
            score.getReasons().add(String.format("ROC bei %.1f%% - positives Momentum", current.getRoc()));
        }
        else if (current.getRoc() < -10)
        {
            points -= 15;
            score.getReasons()
                 .add(String.format("ROC bei %.1f%% - stark negatives Momentum", current.getRoc()));
        }
        else if (current.getRoc() < -5)
        {
            points -= 10;
            score.getReasons().add(String.format("ROC bei %.1f%% - negatives Momentum", current.getRoc()));
        }

        // 5. MOVING AVERAGE TREND (10 Punkte)
        boolean bullishMA = current.getSma5() > current.getSma10() && current.getSma10() > current.getSma20()
                        && current.getSma20() > current.getSma30();
        boolean bearishMA = current.getSma5() < current.getSma10() && current.getSma10() < current.getSma20()
                        && current.getSma20() < current.getSma30();

        if (bullishMA)
        {
            points += 10;
            score.getReasons().add("Perfekte bullische SMA-Staffelung (5>10>20>30) - klarer Aufwärtstrend");
        }
        else if (bearishMA)
        {
            points -= 10;
            score.getReasons().add("Perfekte bearische SMA-Staffelung (5<10<20<30) - klarer Abwärtstrend");
        }
        else if (current.getSma5() > current.getSma20())
        {
            points += 5;
        }

        // MOMENTUM-QUALITÄT BEWERTEN
        boolean strongTrend = current.getAdx() > 25;
        boolean uptrend = current.getAdxPlusDI() > current.getAdxMinusDI();
        boolean outperforming = current.getRsl() > 1.05;
        boolean positiveMomentum = current.getRoc() > 5;

        int qualityFactors = 0;
        if (strongTrend)
            qualityFactors++ ;
        if (uptrend)
            qualityFactors++ ;
        if (outperforming)
            qualityFactors++ ;
        if (positiveMomentum)
            qualityFactors++ ;

        switch (qualityFactors)
        {
        case 4:
            score.setMomentumQuality("EXZELLENT - Alle Momentum-Faktoren positiv");
            break;
        case 3:
            score.setMomentumQuality("GUT - Meiste Momentum-Faktoren positiv");
            break;
        case 2:
            score.setMomentumQuality("MITTELMÄSSIG - Gemischte Signale");
            break;
        case 1:
            score.setMomentumQuality("SCHWACH - Wenige positive Faktoren");
            break;
        default:
            score.setMomentumQuality("UNGEEIGNET - Keine Momentum-Kriterien erfüllt");
        }

        // SCORE BERECHNUNG (0-100)
        score.setScore(Math.max(0, Math.min(100, 50 + points)));

        // SIGNAL ABLEITEN
        if (score.getScore() >= 75 && strongTrend && uptrend)
        {
            score.setSignal("STARKES KAUFSIGNAL");
        }
        else if (score.getScore() >= 60)
        {
            score.setSignal("KAUFEN");
        }
        else if (score.getScore() >= 45)
        {
            score.setSignal("HALTEN");
        }
        else if (score.getScore() >= 30)
        {
            score.setSignal("BEOBACHTEN / REDUZIEREN");
        }
        else
        {
            score.setSignal("VERKAUFEN / MEIDEN");
        }

        return score;
    }


    /**
     * Generiert eine Gesamtempfehlung basierend auf beiden Strategien
     */
    private static void generateOverallRecommendation(StrategyAnalysis analysis)
    {
        int swingScore = analysis.getSwingTrading().getScore();
        int momentumScore = analysis.getMomentum().getScore();

        // Gewichtung: Beide Strategien gleich wichtig
        int combinedScore = (swingScore + momentumScore) / 2;
        analysis.setConfidenceScore(combinedScore);

        // Spezielle Kombinationen
        if (swingScore >= 70 && momentumScore >= 70)
        {
            analysis.setOverallRecommendation("STARKES KAUFSIGNAL - Beide Strategien stimmen überein");
        }
        else if (swingScore >= 60 && momentumScore >= 60)
        {
            analysis.setOverallRecommendation("KAUFEN - Positive Signale in beiden Strategien");
        }
        else if (swingScore <= 30 && momentumScore <= 30)
        {
            analysis.setOverallRecommendation("VERKAUFEN - Beide Strategien negativ");
        }
        else if (Math.abs(swingScore - momentumScore) > 30)
        {
            if (swingScore > momentumScore)
            {
                analysis.setOverallRecommendation("SWING TRADING BEVORZUGEN - Momentum schwach");
            }
            else
            {
                analysis.setOverallRecommendation("MOMENTUM STRATEGIE BEVORZUGEN - Swing-Setup unvollständig");
            }
        }
        else if (combinedScore >= 55)
        {
            analysis.setOverallRecommendation("HALTEN / BEOBACHTEN - Leicht positive Tendenz");
        }
        else if (combinedScore >= 40)
        {
            analysis.setOverallRecommendation("NEUTRAL - Abwarten auf klarere Signale");
        }
        else
        {
            analysis.setOverallRecommendation("VORSICHT / REDUZIEREN - Überwiegend negative Signale");
        }
    }
}
