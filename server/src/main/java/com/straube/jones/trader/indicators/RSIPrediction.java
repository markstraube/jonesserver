package com.straube.jones.trader.indicators;

import java.util.ArrayList;
import java.util.List;

import com.straube.jones.trader.collectors.TradingIndicatorService;
import com.straube.jones.trader.collectors.TradingIndicatorService.Report;
import com.straube.jones.trader.dto.DailyPrice;

/**
 * Verbesserte RSI-Vorhersage mit direkter Verwendung von DailyPrice-Listen.
 * Analysiert Wahrscheinlichkeit für RSI < 30 und berechnet Kaufpreis-Ziele.
 */
public class RSIPrediction
{
    private RSIPrediction() {
        // Utility-Klasse - keine Instanziierung
    }
    /**
     * Historische Daten-Analyse
     */
    public static class HistoricalAnalysis
    {
        private double avgDailyVolatility; // Durchschnittliche Tagesvolatilität (%)
        private int consecutiveLossDays; // Anzahl aufeinanderfolgender Verlusttage
        private double avgLossOnDownDays; // Durchschnittlicher Verlust an Verlusttagen (%)
        private double maxDrawdown; // Maximaler Rückgang in Periode (%)
        private double avgGainOnUpDays; // Durchschnittlicher Gewinn an Gewinntagen (%)
        private int totalDownDays; // Anzahl Verlusttage in letzten 30 Tagen
        private double priceChange30Days; // Preisveränderung über 30 Tage (%)

        public double getAvgDailyVolatility() { return avgDailyVolatility; }
        public void setAvgDailyVolatility(double avgDailyVolatility) { this.avgDailyVolatility = avgDailyVolatility; }
        public int getConsecutiveLossDays() { return consecutiveLossDays; }
        public void setConsecutiveLossDays(int consecutiveLossDays) { this.consecutiveLossDays = consecutiveLossDays; }
        public double getAvgLossOnDownDays() { return avgLossOnDownDays; }
        public void setAvgLossOnDownDays(double avgLossOnDownDays) { this.avgLossOnDownDays = avgLossOnDownDays; }
        public double getMaxDrawdown() { return maxDrawdown; }
        public void setMaxDrawdown(double maxDrawdown) { this.maxDrawdown = maxDrawdown; }
        public double getAvgGainOnUpDays() { return avgGainOnUpDays; }
        public void setAvgGainOnUpDays(double avgGainOnUpDays) { this.avgGainOnUpDays = avgGainOnUpDays; }
        public int getTotalDownDays() { return totalDownDays; }
        public void setTotalDownDays(int totalDownDays) { this.totalDownDays = totalDownDays; }
        public double getPriceChange30Days() { return priceChange30Days; }
        public void setPriceChange30Days(double priceChange30Days) { this.priceChange30Days = priceChange30Days; }

        @Override
        public String toString()
        {
            return String.format("Historische Analyse:%n" + "  Ø Tagesvolatilität: %.2f%%%n"
                            + "  Aufeinanderfolgende Verlusttage: %d%n"
                            + "  Ø Verlust an Verlusttagen: %.2f%%%n"
                            + "  Max. Drawdown: %.2f%%%n"
                            + "  Verlusttage (30d): %d%n"
                            + "  Preisveränderung (30d): %.2f%%",
                                 avgDailyVolatility,
                                 consecutiveLossDays,
                                 avgLossOnDownDays,
                                 maxDrawdown,
                                 totalDownDays,
                                 priceChange30Days);
        }
    }

    /**
     * Ergebnis der Wahrscheinlichkeitsanalyse
     */
    public static class RSI30Probability
    {
        private double probabilityPercent;
        private String assessment;
        private List<String> factors;
        private int daysToReachRSI30Estimate;
        private HistoricalAnalysis historicalAnalysis;

        public double getProbabilityPercent() { return probabilityPercent; }
        public void setProbabilityPercent(double probabilityPercent) { this.probabilityPercent = probabilityPercent; }
        public String getAssessment() { return assessment; }
        public void setAssessment(String assessment) { this.assessment = assessment; }
        public List<String> getFactors() { return factors; }
        public void setFactors(List<String> factors) { this.factors = factors; }
        public int getDaysToReachRSI30Estimate() { return daysToReachRSI30Estimate; }
        public void setDaysToReachRSI30Estimate(int daysToReachRSI30Estimate) { this.daysToReachRSI30Estimate = daysToReachRSI30Estimate; }
        public HistoricalAnalysis getHistoricalAnalysis() { return historicalAnalysis; }
        public void setHistoricalAnalysis(HistoricalAnalysis historicalAnalysis) { this.historicalAnalysis = historicalAnalysis; }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Wahrscheinlichkeit RSI < 30 in 30 Tagen: %.1f%%%n", probabilityPercent));
            sb.append(String.format("Einschätzung: %s%n", assessment));
            sb.append(String.format("Geschätzte Tage bis RSI < 30: %s%n",
                                    daysToReachRSI30Estimate > 0 ? daysToReachRSI30Estimate
                                                    : "Nicht erreichbar"));
            sb.append("Faktoren:%n");
            for (String factor : factors)
            {
                sb.append(String.format("  - %s%n", factor));
            }
            if (historicalAnalysis != null)
            {
                sb.append("\n").append(historicalAnalysis);
            }
            return sb.toString();
        }
    }

    /**
     * Kaufpreise für verschiedene Zeithorizonte
     */
    public static class BuyPriceTargets
    {
        private double currentPrice;
        private double target5Days;
        private double target10Days;
        private double target20Days;
        private double target30Days;
        private double requiredDailyDecline;
        private String volatilityAssessment;

        public double getCurrentPrice() { return currentPrice; }
        public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }
        public double getTarget5Days() { return target5Days; }
        public void setTarget5Days(double target5Days) { this.target5Days = target5Days; }
        public double getTarget10Days() { return target10Days; }
        public void setTarget10Days(double target10Days) { this.target10Days = target10Days; }
        public double getTarget20Days() { return target20Days; }
        public void setTarget20Days(double target20Days) { this.target20Days = target20Days; }
        public double getTarget30Days() { return target30Days; }
        public void setTarget30Days(double target30Days) { this.target30Days = target30Days; }
        public double getRequiredDailyDecline() { return requiredDailyDecline; }
        public void setRequiredDailyDecline(double requiredDailyDecline) { this.requiredDailyDecline = requiredDailyDecline; }
        public String getVolatilityAssessment() { return volatilityAssessment; }
        public void setVolatilityAssessment(String volatilityAssessment) { this.volatilityAssessment = volatilityAssessment; }

        @Override
        public String toString()
        {
            return String.format("Kaufpreis-Ziele (RSI < 30):%n" + "Aktueller Preis: $%.2f%n"
                            + "Volatilitäts-Einschätzung: %s%n"
                            + "-----------------------------%n"
                            + "Nach  5 Tagen: $%.2f (%.1f%% Rückgang)%n"
                            + "Nach 10 Tagen: $%.2f (%.1f%% Rückgang)%n"
                            + "Nach 20 Tagen: $%.2f (%.1f%% Rückgang)%n"
                            + "Nach 30 Tagen: $%.2f (%.1f%% Rückgang)%n"
                            + "-----------------------------%n"
                            + "Benötigter Ø Tagesrückgang: %.2f%%",
                                 currentPrice,
                                 volatilityAssessment,
                                 target5Days,
                                 ((currentPrice - target5Days) / currentPrice * 100),
                                 target10Days,
                                 ((currentPrice - target10Days) / currentPrice * 100),
                                 target20Days,
                                 ((currentPrice - target20Days) / currentPrice * 100),
                                 target30Days,
                                 ((currentPrice - target30Days) / currentPrice * 100),
                                 requiredDailyDecline);
        }
    }

    /**
     * Analysiert historische Preisdaten
     * @param prices Liste von DailyPrice (Index 0 = neuestes Datum)
     * @return HistoricalAnalysis mit allen Kennzahlen
     */
    public static HistoricalAnalysis analyzeHistoricalData(List<DailyPrice> prices)
    {
        HistoricalAnalysis analysis = new HistoricalAnalysis();

        if (prices == null || prices.size() < 2)
        { return analysis; }

        // Nimm maximal 30 Tage oder was verfügbar ist
        int days = Math.min(30, prices.size() - 1);

        List<Double> dailyChanges = new ArrayList<>();
        List<Double> losses = new ArrayList<>();
        List<Double> gains = new ArrayList<>();
        int downDays = 0;
        double maxDrawdown = 0;
        double peakPrice = prices.get(0).getAdjClose();

        // Berechne tägliche Veränderungen
        for (int i = 0; i < days; i++ )
        {
            double today = prices.get(i).getAdjClose();
            double yesterday = prices.get(i + 1).getAdjClose();
            double change = ((today - yesterday) / yesterday) * 100;

            dailyChanges.add(Math.abs(change));

            if (change < 0)
            {
                losses.add(Math.abs(change));
                downDays++ ;
            }
            else if (change > 0)
            {
                gains.add(change);
            }

            // Berechne Drawdown
            if (today > peakPrice)
            {
                peakPrice = today;
            }
            double drawdown = ((peakPrice - today) / peakPrice) * 100;
            if (drawdown > maxDrawdown)
            {
                maxDrawdown = drawdown;
            }
        }

        // Durchschnittliche Volatilität
        double sumVolatility = 0;
        for (double change : dailyChanges)
        {
            sumVolatility += change;
        }
        analysis.setAvgDailyVolatility(dailyChanges.isEmpty() ? 0 : sumVolatility / dailyChanges.size());

        // Durchschnittlicher Verlust an Verlusttagen
        double sumLosses = 0;
        for (double loss : losses)
        {
            sumLosses += loss;
        }
        analysis.setAvgLossOnDownDays(losses.isEmpty() ? 0 : sumLosses / losses.size());

        // Durchschnittlicher Gewinn an Gewinntagen
        double sumGains = 0;
        for (double gain : gains)
        {
            sumGains += gain;
        }
        analysis.setAvgGainOnUpDays(gains.isEmpty() ? 0 : sumGains / gains.size());

        // Aufeinanderfolgende Verlusttage (von neueste nach älter)
        int consecutive = 0;
        for (int i = 0; i < days; i++ )
        {
            double today = prices.get(i).getAdjClose();
            double yesterday = prices.get(i + 1).getAdjClose();

            if (today < yesterday)
            {
                consecutive++ ;
            }
            else
            {
                break;
            }
        }
        analysis.setConsecutiveLossDays(consecutive);

        // Weitere Metriken
        analysis.setMaxDrawdown(maxDrawdown);
        analysis.setTotalDownDays(downDays);

        // Preisveränderung über 30 Tage
        double oldestPrice = prices.get(Math.min(30, prices.size() - 1)).getAdjClose();
        double newestPrice = prices.get(0).getAdjClose();
        analysis.setPriceChange30Days(((newestPrice - oldestPrice) / oldestPrice) * 100);

        return analysis;
    }


    /**
     * FUNKTION 1: Schätzt Wahrscheinlichkeit, dass RSI < 30 in nächsten 30 Tagen
     * 
     * @param report Trading Report von TradingIndicatorService
     * @param prices Liste von DailyPrice (Index 0 = neuestes Datum)
     * @return Wahrscheinlichkeitsanalyse
     */
    public static RSI30Probability estimateRSI30Probability(Report report, List<DailyPrice> prices)
    {
        RSI30Probability result = new RSI30Probability();
        result.setFactors(new ArrayList<>());

        // Hole Mid-Term Analyse
        TradingIndicatorService.Analysis midTerm = null;
        for (TradingIndicatorService.ReportEntry entry : report.getAnalyses())
        {
            if (entry.getName().contains("Mid Term"))
            {
                midTerm = entry.getResult();
                break;
            }
        }

        if (midTerm == null && !report.getAnalyses().isEmpty())
        {
            midTerm = report.getAnalyses().get(0).getResult();
        }

        if (midTerm == null)
        {
            result.setProbabilityPercent(0);
            result.setAssessment("Keine Daten verfügbar");
            return result;
        }

        // Analysiere historische Daten
        HistoricalAnalysis historical = null;
        if (prices != null && prices.size() >= 2)
        {
            historical = analyzeHistoricalData(prices);
            result.setHistoricalAnalysis(historical);
        }

        double probability = 0.0;

        // Faktor 1: Aktueller RSI-Wert (35% Gewichtung)
        double rsiDistance = midTerm.getRsi() - 30;
        double rsiFactor = 0;

        if (rsiDistance <= 5)
        {
            rsiFactor = 80;
            result.getFactors().add(String.format("RSI sehr nah an 30 (aktuell: %.1f)", midTerm.getRsi()));
        }
        else if (rsiDistance <= 10)
        {
            rsiFactor = 55;
            result.getFactors().add(String.format("RSI moderat nah an 30 (aktuell: %.1f)", midTerm.getRsi()));
        }
        else if (rsiDistance <= 20)
        {
            rsiFactor = 30;
            result.getFactors().add(String.format("RSI im mittleren Bereich (aktuell: %.1f)", midTerm.getRsi()));
        }
        else
        {
            rsiFactor = 10;
            result.getFactors().add(String.format("RSI weit von 30 entfernt (aktuell: %.1f)", midTerm.getRsi()));
        }

        probability += rsiFactor * 0.35;

        // Faktor 2: MACD Momentum (20% Gewichtung)
        double macdMomentum = midTerm.getMacdValue() - midTerm.getMacdSignal();
        double macdFactor = 0;

        if (macdMomentum < -2)
        {
            macdFactor = 75;
            result.getFactors().add("MACD stark bearisch (negatives Momentum)");
        }
        else if (macdMomentum < 0)
        {
            macdFactor = 45;
            result.getFactors().add("MACD bearisch");
        }
        else if (macdMomentum < 2)
        {
            macdFactor = 20;
            result.getFactors().add("MACD neutral bis leicht bullisch");
        }
        else
        {
            macdFactor = 5;
            result.getFactors().add("MACD stark bullisch (konträr zu RSI < 30)");
        }

        probability += macdFactor * 0.20;

        // Faktor 3: Bollinger Band Position (15% Gewichtung)
        double bbPosition = (midTerm.getCurrentPrice() - midTerm.getLowerBB()) / (midTerm.getUpperBB() - midTerm.getLowerBB());
        double bbFactor = 0;

        if (bbPosition < 0.0)
        {
            bbFactor = 80;
            result.getFactors().add("Preis durchbricht unteres Bollinger Band");
        }
        else if (bbPosition < 0.2)
        {
            bbFactor = 60;
            result.getFactors().add("Preis sehr nahe unterem Bollinger Band");
        }
        else if (bbPosition < 0.4)
        {
            bbFactor = 35;
            result.getFactors().add("Preis in unterer Hälfte der Bollinger Bands");
        }
        else
        {
            bbFactor = 10;
            result.getFactors().add("Preis in oberer Hälfte (unwahrscheinlich für RSI < 30)");
        }

        probability += bbFactor * 0.15;

        if (historical != null)
        {
            // Faktor 4: Historische Volatilität (15% Gewichtung)
            double volatilityFactor = 0;

            if (historical.getAvgDailyVolatility() > 5)
            {
                volatilityFactor = 70;
                result.getFactors().add(String.format("Hohe Volatilität (Ø %.1f%% täglich) - schnelle Bewegungen möglich",
                                                 historical.getAvgDailyVolatility()));
            }
            else if (historical.getAvgDailyVolatility() > 3)
            {
                volatilityFactor = 45;
                result.getFactors().add(String.format("Moderate Volatilität (Ø %.1f%% täglich)",
                                                 historical.getAvgDailyVolatility()));
            }
            else
            {
                volatilityFactor = 20;
                result.getFactors().add(String.format("Niedrige Volatilität (Ø %.1f%% täglich) - langsame Bewegungen",
                                                 historical.getAvgDailyVolatility()));
            }

            probability += volatilityFactor * 0.15;

            // Faktor 5: Aktuelle Verlust-Serie (10% Gewichtung)
            double lossFactor = 0;

            if (historical.getConsecutiveLossDays() >= 5)
            {
                lossFactor = 75;
                result.getFactors().add(String.format("Starke Abwärtsdynamik: %d aufeinanderfolgende Verlusttage",
                                                 historical.getConsecutiveLossDays()));
            }
            else if (historical.getConsecutiveLossDays() >= 3)
            {
                lossFactor = 45;
                result.getFactors().add(String.format("Moderate Abwärtsdynamik: %d aufeinanderfolgende Verlusttage",
                                                 historical.getConsecutiveLossDays()));
            }
            else if (historical.getConsecutiveLossDays() >= 1)
            {
                lossFactor = 20;
                result.getFactors().add(String.format("Leichte Abwärtsbewegung: %d Verlusttag(e)",
                                                 historical.getConsecutiveLossDays()));
            }
            else
            {
                lossFactor = 5;
                result.getFactors().add("Keine aktuelle Verlust-Serie - Aufwärtstrend intakt");
            }

            probability += lossFactor * 0.10;

            // Faktor 6: 30-Tage Trend (5% Gewichtung)
            double trendFactor = 0;

            if (historical.getPriceChange30Days() < -10)
            {
                trendFactor = 70;
                result.getFactors().add(String.format("Starker Abwärtstrend: %.1f%% in 30 Tagen",
                                                 historical.getPriceChange30Days()));
            }
            else if (historical.getPriceChange30Days() < 0)
            {
                trendFactor = 40;
                result.getFactors().add(String.format("Abwärtstrend: %.1f%% in 30 Tagen",
                                                 historical.getPriceChange30Days()));
            }
            else if (historical.getPriceChange30Days() < 10)
            {
                trendFactor = 20;
                result.getFactors().add(String.format("Leichter Aufwärtstrend: +%.1f%% in 30 Tagen",
                                                 historical.getPriceChange30Days()));
            }
            else
            {
                trendFactor = 5;
                result.getFactors().add(String.format("Starker Aufwärtstrend: +%.1f%% in 30 Tagen (konträr zu RSI < 30)",
                                                 historical.getPriceChange30Days()));
            }

            probability += trendFactor * 0.05;
        }

        result.setProbabilityPercent(probability);

        // Einschätzung
        if (probability >= 65)
        {
            result.setAssessment("SEHR HOCH - RSI < 30 sehr wahrscheinlich");
            result.setDaysToReachRSI30Estimate(7);
        }
        else if (probability >= 50)
        {
            result.setAssessment("HOCH - RSI < 30 wahrscheinlich");
            result.setDaysToReachRSI30Estimate(12);
        }
        else if (probability >= 35)
        {
            result.setAssessment("MITTEL - RSI < 30 möglich");
            result.setDaysToReachRSI30Estimate(20);
        }
        else if (probability >= 20)
        {
            result.setAssessment("NIEDRIG - RSI < 30 unwahrscheinlich");
            result.setDaysToReachRSI30Estimate(30);
        }
        else
        {
            result.setAssessment("SEHR NIEDRIG - RSI < 30 sehr unwahrscheinlich");
            result.setDaysToReachRSI30Estimate(-1);
        }

        return result;
    }


    /**
     * FUNKTION 2: Berechnet Kaufpreis-Ziele für verschiedene Zeithorizonte
     * Annahme: RSI fällt nach 30 Tagen unter 30
     * 
     * @param report Trading Report von TradingIndicatorService
     * @param prices Liste von DailyPrice (Index 0 = neuestes Datum)
     * @return Kaufpreis-Ziele
     */
    public static BuyPriceTargets calculateBuyPriceTargets(Report report, List<DailyPrice> prices)
    {
        BuyPriceTargets targets = new BuyPriceTargets();

        // Hole Mid-Term Analyse
        TradingIndicatorService.Analysis midTerm = null;
        for (TradingIndicatorService.ReportEntry entry : report.getAnalyses())
        {
            if (entry.getName().contains("Mid Term"))
            {
                midTerm = entry.getResult();
                break;
            }
        }

        if (midTerm == null && !report.getAnalyses().isEmpty())
        {
            midTerm = report.getAnalyses().get(0).getResult();
        }

        if (midTerm == null)
        { return targets; }

        targets.setCurrentPrice(midTerm.getCurrentPrice());

        // Analysiere historische Daten
        HistoricalAnalysis historical = null;
        if (prices != null && prices.size() >= 2)
        {
            historical = analyzeHistoricalData(prices);
        }

        double currentRSI = midTerm.getRsi();
        double rsiDistance = currentRSI - 30;

        // Basis-Rückgang berechnen
        // Formel: Für RSI < 30 brauchen wir RS = 0.428
        // Das bedeutet: Average Loss / Average Gain = 2.33
        // Empirisch: 12-20% Rückgang über 14-30 Tage

        double baseDeclinePercent;

        if (rsiDistance <= 10)
        {
            baseDeclinePercent = 10 + (rsiDistance * 0.3); // 10-13%
        }
        else if (rsiDistance <= 20)
        {
            baseDeclinePercent = 13 + ((rsiDistance - 10) * 0.5); // 13-18%
        }
        else
        {
            baseDeclinePercent = 18 + ((rsiDistance - 20) * 0.3); // 18-24%
        }

        // Adjustiere basierend auf historischer Volatilität
        if (historical != null)
        {
            double volatilityMultiplier;

            if (historical.getAvgDailyVolatility() > 5)
            {
                // Hohe Volatilität: Ziel kann schneller erreicht werden
                volatilityMultiplier = 1.3;
                targets.setVolatilityAssessment("Hoch - schnelle Preisbewegungen erwartet");
            }
            else if (historical.getAvgDailyVolatility() > 3)
            {
                volatilityMultiplier = 1.15;
                targets.setVolatilityAssessment("Moderat - normale Preisbewegungen");
            }
            else
            {
                volatilityMultiplier = 1.0;
                targets.setVolatilityAssessment("Niedrig - langsame Preisbewegungen");
            }

            baseDeclinePercent *= volatilityMultiplier;

            // Berücksichtige aktuellen Trend
            if (historical.getPriceChange30Days() < -5)
            {
                // Bereits im Abwärtstrend: weniger zusätzlicher Rückgang nötig
                baseDeclinePercent *= 0.85;
                targets.setVolatilityAssessment(targets.getVolatilityAssessment() + " (bereits im Abwärtstrend)");
            }
            else if (historical.getPriceChange30Days() > 10)
            {
                // Starker Aufwärtstrend: mehr Rückgang nötig
                baseDeclinePercent *= 1.15;
                targets.setVolatilityAssessment(targets.getVolatilityAssessment() + " (gegen starken Aufwärtstrend)");
            }

            // Berücksichtige durchschnittlichen Verlust
            if (historical.getAvgLossOnDownDays() > 2.5)
            {
                // Starke Verlusttage: schnelleres Erreichen möglich
                baseDeclinePercent *= 0.9;
            }
        }
        else
        {
            targets.setVolatilityAssessment("Unbekannt - keine historischen Daten");
        }

        // Begrenze auf realistische Werte
        baseDeclinePercent = Math.max(8, Math.min(baseDeclinePercent, 28));

        targets.setRequiredDailyDecline(baseDeclinePercent / 30);

        // Berechne Zwischenziele mit realistischer nicht-linearer Kurve
        // Marktbewegungen beschleunigen sich oft (Momentum-Effekt)

        // Tag 5: 12% des Gesamtrückgangs (langsamer Start)
        double decline5Days = baseDeclinePercent * 0.12;
        targets.setTarget5Days(targets.getCurrentPrice() * (1 - decline5Days / 100));

        // Tag 10: 30% des Gesamtrückgangs (Beschleunigung beginnt)
        double decline10Days = baseDeclinePercent * 0.30;
        targets.setTarget10Days(targets.getCurrentPrice() * (1 - decline10Days / 100));

        // Tag 20: 65% des Gesamtrückgangs (starke Beschleunigung)
        double decline20Days = baseDeclinePercent * 0.65;
        targets.setTarget20Days(targets.getCurrentPrice() * (1 - decline20Days / 100));

        // Tag 30: 100% des Gesamtrückgangs (RSI < 30 erreicht)
        targets.setTarget30Days(targets.getCurrentPrice() * (1 - baseDeclinePercent / 100));

        return targets;
    }
}
