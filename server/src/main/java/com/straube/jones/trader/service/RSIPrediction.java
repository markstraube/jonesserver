package com.straube.jones.trader.service;


import java.util.ArrayList;
import java.util.List;

import com.straube.jones.trader.service.TradingIndicatorService.Report;

/**
 * RSI-Vorhersage-Service zur Analyse der Wahrscheinlichkeit,
 * dass RSI unter 30 fällt und Berechnung von Kaufpreis-Zielen.
 */
public class RSIPrediction
{
    private RSIPrediction() {
        // Utility-Klasse - keine Instanziierung
    }

    /**
     * Historische Preisdaten für bessere Vorhersage
     */
    public static class HistoricalData
    {
        private List<Double> recentPrices; // Letzte 30 Tage Preise
        private List<Long> recentVolumes; // Letzte 30 Tage Volumen
        private double avgDailyVolatility; // Durchschnittliche Tagesvolatilität (%)
        private int consecutiveLossDays; // Anzahl aufeinanderfolgender Verlusttage
        private double avgLossOnDownDays; // Durchschnittlicher Verlust an Verlusttagen (%)

        public HistoricalData(List<Double> prices, List<Long> volumes)
        {
            this.recentPrices = prices;
            this.recentVolumes = volumes;
            this.avgDailyVolatility = calculateVolatility(prices);
            this.consecutiveLossDays = calculateConsecutiveLossDays(prices);
            this.avgLossOnDownDays = calculateAvgLoss(prices);
        }

        public List<Double> getRecentPrices() { return recentPrices; }
        public List<Long> getRecentVolumes() { return recentVolumes; }
        public double getAvgDailyVolatility() { return avgDailyVolatility; }
        public int getConsecutiveLossDays() { return consecutiveLossDays; }
        public double getAvgLossOnDownDays() { return avgLossOnDownDays; }


        private double calculateVolatility(List<Double> prices)
        {
            if (prices.size() < 2)
                return 0;

            List<Double> changes = new ArrayList<>();
            for (int i = 0; i < prices.size() - 1; i++ )
            {
                double change = Math.abs((prices.get(i) - prices.get(i + 1)) / prices.get(i + 1) * 100);
                changes.add(change);
            }

            double sum = 0;
            for (double change : changes)
                sum += change;
            return sum / changes.size();
        }


        private int calculateConsecutiveLossDays(List<Double> prices)
        {
            if (prices.size() < 2)
                return 0;

            int consecutive = 0;
            for (int i = 0; i < Math.min(10, prices.size() - 1); i++ )
            {
                if (prices.get(i) < prices.get(i + 1))
                {
                    consecutive++ ;
                }
                else
                {
                    break;
                }
            }
            return consecutive;
        }


        private double calculateAvgLoss(List<Double> prices)
        {
            if (prices.size() < 2)
                return 0;

            List<Double> losses = new ArrayList<>();
            for (int i = 0; i < prices.size() - 1; i++ )
            {
                if (prices.get(i) < prices.get(i + 1))
                {
                    double loss = (prices.get(i + 1) - prices.get(i)) / prices.get(i + 1) * 100;
                    losses.add(loss);
                }
            }

            if (losses.isEmpty())
                return 0;
            double sum = 0;
            for (double loss : losses)
                sum += loss;
            return sum / losses.size();
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

        public double getProbabilityPercent() { return probabilityPercent; }
        public void setProbabilityPercent(double probabilityPercent) { this.probabilityPercent = probabilityPercent; }
        public String getAssessment() { return assessment; }
        public void setAssessment(String assessment) { this.assessment = assessment; }
        public List<String> getFactors() { return factors; }
        public void setFactors(List<String> factors) { this.factors = factors; }
        public int getDaysToReachRSI30Estimate() { return daysToReachRSI30Estimate; }
        public void setDaysToReachRSI30Estimate(int daysToReachRSI30Estimate) { this.daysToReachRSI30Estimate = daysToReachRSI30Estimate; }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Wahrscheinlichkeit RSI < 30 in 30 Tagen: %.1f%%%n", probabilityPercent));
            sb.append(String.format("Einschätzung: %s%n", assessment));
            sb.append(String.format("Geschätzte Tage bis RSI < 30: %d%n", daysToReachRSI30Estimate));
            sb.append("Faktoren:%n");
            for (String factor : factors)
            {
                sb.append(String.format("  - %s%n", factor));
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

        @Override
        public String toString()
        {
            return String.format("Kaufpreis-Ziele (RSI < 30):%n" + "Aktueller Preis: $%.2f%n"
                            + "-----------------------------%n"
                            + "Nach  5 Tagen: $%.2f (%.1f%% Rückgang)%n"
                            + "Nach 10 Tagen: $%.2f (%.1f%% Rückgang)%n"
                            + "Nach 20 Tagen: $%.2f (%.1f%% Rückgang)%n"
                            + "Nach 30 Tagen: $%.2f (%.1f%% Rückgang)%n"
                            + "-----------------------------%n"
                            + "Benötigter Ø Tagesrückgang: %.2f%%",
                                 currentPrice,
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
     * FUNKTION 1: Schätzt Wahrscheinlichkeit, dass RSI < 30 in nächsten 30 Tagen
     * 
     * @param report Trading Report von TradingIndicatorService
     * @param historicalData Historische Preisdaten (optional, aber empfohlen)
     * @return Wahrscheinlichkeitsanalyse
     */
    public static RSI30Probability estimateRSI30Probability(Report report, HistoricalData historicalData)
    {
        RSI30Probability result = new RSI30Probability();
        result.setFactors(new ArrayList<>());

        // Hole Mid-Term Analyse (meist aussagekräftigste)
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

        double probability = 0.0;

        // Faktor 1: Aktueller RSI-Wert (40% Gewichtung)
        double rsiDistance = midTerm.getRsi() - 30;
        double rsiFactor = 0;

        if (rsiDistance <= 5)
        {
            rsiFactor = 80; // Sehr nah
            result.getFactors().add(String.format("RSI sehr nah an 30 (aktuell: %.1f)", midTerm.getRsi()));
        }
        else if (rsiDistance <= 10)
        {
            rsiFactor = 50; // Nah
            result.getFactors().add(String.format("RSI moderat nah an 30 (aktuell: %.1f)", midTerm.getRsi()));
        }
        else if (rsiDistance <= 20)
        {
            rsiFactor = 25; // Mittel
            result.getFactors().add(String.format("RSI im mittleren Bereich (aktuell: %.1f)", midTerm.getRsi()));
        }
        else
        {
            rsiFactor = 10; // Weit entfernt
            result.getFactors().add(String.format("RSI weit von 30 entfernt (aktuell: %.1f)", midTerm.getRsi()));
        }

        probability += rsiFactor * 0.4;

        // Faktor 2: MACD Momentum (20% Gewichtung)
        double macdMomentum = midTerm.getMacdValue() - midTerm.getMacdSignal();
        double macdFactor = 0;

        if (macdMomentum < -2)
        {
            macdFactor = 70; // Stark bearisch
            result.getFactors().add("MACD stark bearisch (negatives Momentum)");
        }
        else if (macdMomentum < 0)
        {
            macdFactor = 40; // Bearisch
            result.getFactors().add("MACD bearisch");
        }
        else if (macdMomentum < 2)
        {
            macdFactor = 20; // Neutral/leicht bullisch
            result.getFactors().add("MACD neutral bis leicht bullisch");
        }
        else
        {
            macdFactor = 5; // Stark bullisch
            result.getFactors().add("MACD stark bullisch (unwahrscheinlich für RSI < 30)");
        }

        probability += macdFactor * 0.2;

        // Faktor 3: Bollinger Band Position (15% Gewichtung)
        double bbPosition = (midTerm.getCurrentPrice() - midTerm.getLowerBB()) / (midTerm.getUpperBB() - midTerm.getLowerBB());
        double bbFactor = 0;

        if (bbPosition < 0.1)
        {
            bbFactor = 70; // Weit unterhalb
            result.getFactors().add("Preis durchbricht unteres Bollinger Band");
        }
        else if (bbPosition < 0.3)
        {
            bbFactor = 50; // Nahe unterhalb
            result.getFactors().add("Preis nahe unterem Bollinger Band");
        }
        else if (bbPosition < 0.5)
        {
            bbFactor = 30; // Untere Hälfte
            result.getFactors().add("Preis in unterer Hälfte der Bollinger Bands");
        }
        else
        {
            bbFactor = 10; // Obere Hälfte
            result.getFactors().add("Preis in oberer Hälfte (unwahrscheinlich für RSI < 30)");
        }

        probability += bbFactor * 0.15;

        // Faktor 4: Historische Volatilität (15% Gewichtung)
        if (historicalData != null)
        {
            double volatilityFactor = 0;

            if (historicalData.getAvgDailyVolatility() > 5)
            {
                volatilityFactor = 60; // Hohe Volatilität
                result.getFactors().add(String.format("Hohe Volatilität (Ø %.1f%% täglich)",
                                                 historicalData.getAvgDailyVolatility()));
            }
            else if (historicalData.getAvgDailyVolatility() > 3)
            {
                volatilityFactor = 40; // Mittlere Volatilität
                result.getFactors().add(String.format("Moderate Volatilität (Ø %.1f%% täglich)",
                                                 historicalData.getAvgDailyVolatility()));
            }
            else
            {
                volatilityFactor = 20; // Niedrige Volatilität
                result.getFactors().add(String.format("Niedrige Volatilität (Ø %.1f%% täglich)",
                                                 historicalData.getAvgDailyVolatility()));
            }

            probability += volatilityFactor * 0.15;
        }

        // Faktor 5: Aktuelle Verlust-Serie (10% Gewichtung)
        if (historicalData != null && historicalData.getConsecutiveLossDays() > 0)
        {
            double lossFactor = 0;

            if (historicalData.getConsecutiveLossDays() >= 5)
            {
                lossFactor = 70; // Lange Verlust-Serie
                result.getFactors().add(String.format("%d aufeinanderfolgende Verlusttage",
                                                 historicalData.getConsecutiveLossDays()));
            }
            else if (historicalData.getConsecutiveLossDays() >= 3)
            {
                lossFactor = 40; // Mittlere Verlust-Serie
                result.getFactors().add(String.format("%d aufeinanderfolgende Verlusttage",
                                                 historicalData.getConsecutiveLossDays()));
            }
            else
            {
                lossFactor = 20; // Kurze Verlust-Serie
                result.getFactors().add(String.format("%d Verlusttag(e)", historicalData.getConsecutiveLossDays()));
            }

            probability += lossFactor * 0.1;
        }

        result.setProbabilityPercent(probability);

        // Einschätzung
        if (probability >= 60)
        {
            result.setAssessment("HOCH - RSI < 30 sehr wahrscheinlich");
            result.setDaysToReachRSI30Estimate(10);
        }
        else if (probability >= 40)
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
            result.setDaysToReachRSI30Estimate(-1); // Nicht erreichbar
        }

        return result;
    }


    /**
     * FUNKTION 2: Berechnet Kaufpreis-Ziele für verschiedene Zeithorizonte
     * Annahme: RSI fällt nach 30 Tagen unter 30
     * 
     * @param report Trading Report von TradingIndicatorService
     * @param historicalData Historische Preisdaten (optional)
     * @return Kaufpreis-Ziele
     */
    public static BuyPriceTargets calculateBuyPriceTargets(Report report, HistoricalData historicalData)
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

        // Berechne erforderlichen Gesamtrückgang
        // Für RSI < 30: Average Loss muss ~2.33x größer sein als Average Gain
        // Das bedeutet typischerweise 12-18% Preisrückgang über 14 Perioden

        double currentRSI = midTerm.getRsi();
        double rsiDistance = currentRSI - 30;

        // Basis-Rückgang: Je weiter RSI von 30, desto mehr Rückgang nötig
        double baseDeclinePercent = 10 + (rsiDistance / 10 * 5); // 10-20% je nach RSI

        // Adjustiere basierend auf Volatilität
        if (historicalData != null && historicalData.getAvgDailyVolatility() > 0)
        {
            // Bei höherer Volatilität kann Ziel schneller erreicht werden
            double volatilityMultiplier = 1.0 + (historicalData.getAvgDailyVolatility() / 10);
            baseDeclinePercent *= volatilityMultiplier;
        }
        else
        {
            // Default: moderate Volatilität angenommen
            baseDeclinePercent *= 1.3;
        }

        // Begrenze auf realistische Werte (max 25% in 30 Tagen)
        baseDeclinePercent = Math.min(baseDeclinePercent, 25);

        targets.setRequiredDailyDecline(baseDeclinePercent / 30);

        // Berechne Zwischenziele mit nicht-linearer Kurve
        // Anfangs langsamerer Rückgang, dann Beschleunigung

        // Tag 5: 15% des Gesamtrückgangs
        double decline5Days = baseDeclinePercent * 0.15;
        targets.setTarget5Days(targets.getCurrentPrice() * (1 - decline5Days / 100));

        // Tag 10: 35% des Gesamtrückgangs
        double decline10Days = baseDeclinePercent * 0.35;
        targets.setTarget10Days(targets.getCurrentPrice() * (1 - decline10Days / 100));

        // Tag 20: 70% des Gesamtrückgangs
        double decline20Days = baseDeclinePercent * 0.70;
        targets.setTarget20Days(targets.getCurrentPrice() * (1 - decline20Days / 100));

        // Tag 30: 100% des Gesamtrückgangs (RSI < 30 erreicht)
        double decline30Days = baseDeclinePercent;
        targets.setTarget30Days(targets.getCurrentPrice() * (1 - decline30Days / 100));

        return targets;
    }
}
