package com.straube.jones.trader.indicators;


import java.lang.reflect.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.straube.jones.db.DayCounter;
import com.straube.jones.service.MarketDataService;
import com.straube.jones.trader.dto.DailyPrice;

/**
 * Indikatoren und Strategien für Momentum/Trend-Aktien
 * Optimiert für bullische Aktien wie RKLB, die lange im "überkauften" Bereich bleiben
 */
public class MomentumIndicators
{

    // ==================== ADX (Average Directional Index) ====================

    /**
     * ADX Ergebnis
     */
    public static class ADXResult
    {
        public double adx; // Trendstärke (0-100)
        public double plusDI; // +DI (bullische Bewegung)
        public double minusDI; // -DI (bearische Bewegung)
        public String trendStrength; // Interpretation

        @Override
        public String toString()
        {
            return String.format("ADX: %.2f (%s)%n+DI: %.2f, -DI: %.2f", adx, trendStrength, plusDI, minusDI);
        }
    }

    /**
     * Berechnet ADX (Average Directional Index)
     * Misst Trendstärke (nicht Richtung!)
     * 
     * @param prices Liste der Preise (Index 0 = neuestes Datum)
     * @param period Standard: 14
     * @return ADXResult
     */
    public static ADXResult calculateADX(List<DailyPrice> prices, int period)
    {
        ADXResult result = new ADXResult();

        if (prices == null || prices.size() < period + 1)
        {
            result.trendStrength = "Nicht genug Daten";
            return result;
        }

        // Berechne True Range (TR)
        List<Double> trueRanges = new ArrayList<>();
        for (int i = 0; i < prices.size() - 1; i++ )
        {
            double high = prices.get(i).getHigh();
            double low = prices.get(i).getLow();
            double prevClose = prices.get(i + 1).getAdjClose();

            double tr = Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
            trueRanges.add(tr);
        }

        // Berechne +DM und -DM (Directional Movement)
        List<Double> plusDM = new ArrayList<>();
        List<Double> minusDM = new ArrayList<>();

        for (int i = 0; i < prices.size() - 1; i++ )
        {
            double highDiff = prices.get(i).getHigh() - prices.get(i + 1).getHigh();
            double lowDiff = prices.get(i + 1).getLow() - prices.get(i).getLow();

            double plusDMValue = 0;
            double minusDMValue = 0;

            if (highDiff > lowDiff && highDiff > 0)
            {
                plusDMValue = highDiff;
            }
            if (lowDiff > highDiff && lowDiff > 0)
            {
                minusDMValue = lowDiff;
            }

            plusDM.add(plusDMValue);
            minusDM.add(minusDMValue);
        }

        // Berechne geglättete Werte (Wilder's Smoothing)
        double atr = calculateSmoothedAverage(trueRanges, period);
        double smoothedPlusDM = calculateSmoothedAverage(plusDM, period);
        double smoothedMinusDM = calculateSmoothedAverage(minusDM, period);

        // Berechne +DI und -DI
        result.plusDI = (smoothedPlusDM / atr) * 100;
        result.minusDI = (smoothedMinusDM / atr) * 100;

        // Berechne DX (Directional Index)
        double diDiff = Math.abs(result.plusDI - result.minusDI);
        double diSum = result.plusDI + result.minusDI;
        double dx = diSum == 0 ? 0 : (diDiff / diSum) * 100;

        // ADX ist geglätteter DX
        result.adx = dx; // Vereinfachung: Sollte eigentlich auch geglättet werden

        // Interpretation
        if (result.adx < 20)
        {
            result.trendStrength = "Schwacher/Kein Trend";
        }
        else if (result.adx < 25)
        {
            result.trendStrength = "Beginnender Trend";
        }
        else if (result.adx < 40)
        {
            result.trendStrength = "Starker Trend";
        }
        else if (result.adx < 50)
        {
            result.trendStrength = "Sehr starker Trend";
        }
        else
        {
            result.trendStrength = "Extremer Trend";
        }

        return result;
    }


    private static double calculateSmoothedAverage(List<Double> values, int period)
    {
        if (values.size() < period)
            return 0;

        // Erste Summe
        double sum = 0;
        for (int i = 0; i < period; i++ )
        {
            sum += values.get(i);
        }
        double smoothed = sum / period;

        // Wilder's Smoothing für Rest
        for (int i = period; i < values.size(); i++ )
        {
            smoothed = (smoothed * (period - 1) + values.get(i)) / period;
        }

        return smoothed;
    }

    // ==================== OBV (On-Balance Volume) ====================

    /**
     * OBV Ergebnis
     */
    public static class OBVResult
    {
        public double obv;
        public double obvEMA; // Geglättetes OBV für Trend
        public String signal;

        @Override
        public String toString()
        {
            return String.format("OBV: %.0f%nOBV-EMA: %.0f%nSignal: %s", obv, obvEMA, signal);
        }
    }

    /**
     * Berechnet On-Balance Volume (OBV)
     * Kombiniert Preis und Volumen
     * 
     * @param prices Liste der Preise (Index 0 = neuestes Datum)
     * @return OBVResult
     */
    public static OBVResult calculateOBV(List<DailyPrice> prices)
    {
        OBVResult result = new OBVResult();

        if (prices == null || prices.size() < 20)
        {
            result.signal = "Nicht genug Daten";
            return result;
        }

        // Berechne OBV (von alt nach neu)
        List<Double> obvValues = new ArrayList<>();
        double obv = 0;

        for (int i = prices.size() - 2; i >= 0; i-- )
        {
            double currentPrice = prices.get(i).getAdjClose();
            double previousPrice = prices.get(i + 1).getAdjClose();
            long volume = prices.get(i).getVolume();

            if (currentPrice > previousPrice)
            {
                obv += volume;
            }
            else if (currentPrice < previousPrice)
            {
                obv -= volume;
            }
            // Bei gleichem Preis: OBV unverändert

            obvValues.add(obv);
        }

        result.obv = obvValues.get(obvValues.size() - 1); // Neuester OBV

        // Berechne EMA(20) des OBV für Trend
        result.obvEMA = calculateEMAFromDoubles(obvValues, 20);

        // Interpretation
        double currentPrice = prices.get(0).getAdjClose();
        double price20DaysAgo = prices.get(Math.min(19, prices.size() - 1)).getAdjClose();
        boolean priceUp = currentPrice > price20DaysAgo;
        boolean obvUp = result.obv > result.obvEMA;

        if (priceUp && obvUp)
        {
            result.signal = "BULLISCH - Preis und OBV steigen (Trend bestätigt)";
        }
        else if (priceUp && !obvUp)
        {
            result.signal = "WARNUNG - Preis steigt aber OBV fällt (Divergenz!)";
        }
        else if (!priceUp && !obvUp)
        {
            result.signal = "BEARISCH - Preis und OBV fallen";
        }
        else
        {
            result.signal = "NEUTRAL - Gemischte Signale";
        }

        return result;
    }

    // ==================== Volumen-Analyse ====================

    /**
     * Volumen-Analyse Ergebnis
     */
    public static class VolumeAnalysis
    {
        public long currentVolume;
        public long avgVolume20;
        public double volumeRatio; // Aktuell / Durchschnitt
        public boolean highVolume;
        public String interpretation;

        @Override
        public String toString()
        {
            return String.format("Aktuelles Volumen: %,d%n" + "Ø Volumen (20d): %,d%n"
                            + "Verhältnis: %.2fx%n"
                            + "Hohes Volumen: %s%n"
                            + "Interpretation: %s",
                                 currentVolume,
                                 avgVolume20,
                                 volumeRatio,
                                 highVolume ? "JA" : "NEIN",
                                 interpretation);
        }
    }

    /**
     * Analysiert Volumen im Vergleich zum Durchschnitt
     * 
     * @param prices Liste der Preise (Index 0 = neuestes Datum)
     * @param period Standard: 20
     * @return VolumeAnalysis
     */
    public static VolumeAnalysis analyzeVolume(List<DailyPrice> prices, int period)
    {
        VolumeAnalysis result = new VolumeAnalysis();

        if (prices == null || prices.size() < period)
        {
            result.interpretation = "Nicht genug Daten";
            return result;
        }

        result.currentVolume = prices.get(0).getVolume();

        // Berechne Durchschnittsvolumen
        long sum = 0;
        for (int i = 0; i < period; i++ )
        {
            sum += prices.get(i).getVolume();
        }
        result.avgVolume20 = sum / period;

        result.volumeRatio = (double)result.currentVolume / result.avgVolume20;
        result.highVolume = result.volumeRatio >= 1.5;

        // Preisentwicklung prüfen
        double priceChange = (prices.get(0).getAdjClose() - prices.get(1).getAdjClose())
                        / prices.get(1).getAdjClose()
                        * 100;

        // Interpretation
        if (result.volumeRatio > 2.5 && priceChange > 3)
        {
            result.interpretation = "SEHR BULLISCH - Starker Kaufdruck mit hohem Volumen";
        }
        else if (result.volumeRatio > 1.5 && priceChange > 1)
        {
            result.interpretation = "BULLISCH - Überdurchschnittliches Kaufvolumen";
        }
        else if (result.volumeRatio > 2.5 && priceChange < -3)
        {
            result.interpretation = "PANIKVERKAUF - Hohes Volumen bei fallendem Preis";
        }
        else if (result.volumeRatio > 1.5 && priceChange < -1)
        {
            result.interpretation = "VERKAUFSDRUCK - Überdurchschnittliches Verkaufsvolumen";
        }
        else if (result.volumeRatio < 0.5)
        {
            result.interpretation = "NIEDRIGES INTERESSE - Wenig Handelsaktivität";
        }
        else
        {
            result.interpretation = "NORMAL - Durchschnittliches Volumen";
        }

        return result;
    }

    // ==================== Trend-Analyse ====================

    /**
     * Trend-Analyse Ergebnis
     */
    public static class TrendAnalysis
    {
        public boolean uptrend;
        public String strength; // Weak, Medium, Strong
        public boolean emaAligned; // Alle EMAs in richtiger Reihenfolge
        public double ema8;
        public double ema21;
        public double ema50;
        public String recommendation;

        @Override
        public String toString()
        {
            return String.format("Trend: %s (%s)%n" + "EMA-Ausrichtung: %s%n"
                            + "EMA(8): %.2f%n"
                            + "EMA(21): %.2f%n"
                            + "EMA(50): %.2f%n"
                            + "Empfehlung: %s",
                                 uptrend ? "AUFWÄRTS" : "ABWÄRTS",
                                 strength,
                                 emaAligned ? "PERFEKT" : "NICHT ALIGNED",
                                 ema8,
                                 ema21,
                                 ema50,
                                 recommendation);
        }
    }

    /**
     * Analysiert den aktuellen Trend
     * 
     * @param prices Liste der Preise (Index 0 = neuestes Datum)
     * @return TrendAnalysis
     */
    public static TrendAnalysis analyzeTrend(List<DailyPrice> prices)
    {
        TrendAnalysis result = new TrendAnalysis();

        if (prices == null || prices.size() < 50)
        {
            result.recommendation = "Nicht genug Daten";
            return result;
        }

        // Berechne EMAs
        result.ema8 = calculateEMA(prices, 8);
        result.ema21 = calculateEMA(prices, 21);
        result.ema50 = calculateEMA(prices, 50);

        double currentPrice = prices.get(0).getAdjClose();

        // Prüfe EMA-Ausrichtung
        result.emaAligned = (result.ema8 > result.ema21 && result.ema21 > result.ema50);
        result.uptrend = result.emaAligned && currentPrice > result.ema8;

        // Bestimme Stärke
        if (result.uptrend)
        {
            double distanceToEMA8 = ((currentPrice - result.ema8) / result.ema8) * 100;

            if (distanceToEMA8 > 5 && result.emaAligned)
            {
                result.strength = "SEHR STARK";
                result.recommendation = "Starker Aufwärtstrend - Trend folgen, nicht gegen handeln";
            }
            else if (distanceToEMA8 > 2 && result.emaAligned)
            {
                result.strength = "STARK";
                result.recommendation = "Aufwärtstrend intakt - Bei Pullback zu EMA21 kaufen";
            }
            else
            {
                result.strength = "MODERAT";
                result.recommendation = "Leichter Aufwärtstrend - Vorsichtig bleiben";
            }
        }
        else
        {
            boolean downtrend = (result.ema8 < result.ema21 && result.ema21 < result.ema50);

            if (downtrend)
            {
                result.strength = "ABWÄRTS";
                result.recommendation = "Abwärtstrend - Vermeiden oder Short";
            }
            else
            {
                result.strength = "SEITWÄRTS";
                result.recommendation = "Kein klarer Trend - Range-Trading oder abwarten";
            }
        }

        return result;
    }

    // ==================== Momentum Trading Signal ====================

    /**
     * Komplettes Momentum-Trading-Signal
     */
    public static class MomentumSignal
    {
        public enum Action
        {
            STRONG_BUY, BUY, HOLD, SELL, STRONG_SELL, WAIT
        }

        public Action action;
        public int score; // -10 bis +10
        public String primaryReason;
        public List<String> factors;
        public double entryPrice;
        public double stopLoss;
        public double takeProfit;

        // Indikator-Werte
        public TrendAnalysis trend;
        public ADXResult adx;
        public VolumeAnalysis volume;
        public OBVResult obv;

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("═══════════════════════════════════\n");
            sb.append(String.format("  MOMENTUM TRADING SIGNAL%n"));
            sb.append("═══════════════════════════════════\n");
            sb.append(String.format("Aktion: %s (Score: %+d)%n", action, score));
            sb.append(String.format("Hauptgrund: %s%n%n", primaryReason));

            if (action == Action.BUY || action == Action.STRONG_BUY)
            {
                sb.append(String.format("Entry: $%.2f%n", entryPrice));
                sb.append(String.format("Stop-Loss: $%.2f (%.1f%%)%n",
                                        stopLoss,
                                        ((stopLoss - entryPrice) / entryPrice * 100)));
                sb.append(String.format("Take-Profit: $%.2f (+%.1f%%)%n%n",
                                        takeProfit,
                                        ((takeProfit - entryPrice) / entryPrice * 100)));
            }

            sb.append("Faktoren:\n");
            for (String factor : factors)
            {
                sb.append(String.format("  • %s%n", factor));
            }

            sb.append("\n--- DETAILLIERTE ANALYSE ---\n\n");
            sb.append(trend).append("\n\n");
            sb.append(adx).append("\n\n");
            sb.append(volume).append("\n\n");
            sb.append(obv).append("\n");

            return sb.toString();
        }
    }

    /**
     * HAUPTFUNKTION: Generiert Momentum-Trading-Signal
     * Optimiert für bullische Trendaktien
     * 
     * @param prices Liste der Preise (Index 0 = neuestes Datum)
     * @param rsi Aktueller RSI-Wert (optional, kann null sein)
     * @return MomentumSignal mit Empfehlung
     */
    public static MomentumSignal generateMomentumSignal(List<DailyPrice> prices, Double rsi)
    {
        MomentumSignal signal = new MomentumSignal();
        signal.factors = new ArrayList<>();
        signal.score = 0;

        if (prices == null || prices.size() < 50)
        {
            signal.action = MomentumSignal.Action.WAIT;
            signal.primaryReason = "Nicht genug Daten für Analyse";
            return signal;
        }

        double currentPrice = prices.get(0).getAdjClose();
        signal.entryPrice = currentPrice;

        // Berechne alle Indikatoren
        signal.trend = analyzeTrend(prices);
        signal.adx = calculateADX(prices, 14);
        signal.volume = analyzeVolume(prices, 20);
        signal.obv = calculateOBV(prices);

        // ==================== SCORING ====================

        // 1. Trend-Analyse (max +4 / -4)
        if (signal.trend.uptrend && signal.trend.emaAligned)
        {
            if (signal.trend.strength.equals("SEHR STARK"))
            {
                signal.score += 4;
                signal.factors.add("✓ Sehr starker Aufwärtstrend (EMA perfekt aligned)");
            }
            else if (signal.trend.strength.equals("STARK"))
            {
                signal.score += 3;
                signal.factors.add("✓ Starker Aufwärtstrend");
            }
            else
            {
                signal.score += 2;
                signal.factors.add("✓ Aufwärtstrend vorhanden");
            }
        }
        else if (signal.trend.strength.equals("ABWÄRTS"))
        {
            signal.score -= 3;
            signal.factors.add("✗ Abwärtstrend aktiv");
        }
        else
        {
            signal.score += 0;
            signal.factors.add("○ Kein klarer Trend");
        }

        // 2. ADX - Trendstärke (max +3 / -1)
        if (signal.adx.adx > 40)
        {
            signal.score += 3;
            signal.factors.add(String.format("✓ Sehr starker Trend (ADX: %.1f)", signal.adx.adx));
        }
        else if (signal.adx.adx > 25)
        {
            signal.score += 2;
            signal.factors.add(String.format("✓ Starker Trend (ADX: %.1f)", signal.adx.adx));
        }
        else if (signal.adx.adx < 20)
        {
            signal.score -= 1;
            signal.factors.add(String.format("○ Schwacher Trend (ADX: %.1f)", signal.adx.adx));
        }

        // 3. Volumen (max +2 / -2)
        if (signal.volume.highVolume)
        {
            double priceChange = (prices.get(0).getAdjClose() - prices.get(1).getAdjClose())
                            / prices.get(1).getAdjClose()
                            * 100;
            if (priceChange > 0)
            {
                signal.score += 2;
                signal.factors.add(String.format("✓ Hohes Kaufvolumen (%.1fx Durchschnitt)",
                                                 signal.volume.volumeRatio));
            }
            else
            {
                signal.score -= 2;
                signal.factors.add("✗ Hohes Verkaufsvolumen");
            }
        }
        else if (signal.volume.volumeRatio < 0.5)
        {
            signal.score -= 1;
            signal.factors.add("○ Niedriges Volumen - wenig Interesse");
        }

        // 4. OBV (max +2 / -2)
        if (signal.obv.signal.contains("BULLISCH"))
        {
            signal.score += 2;
            signal.factors.add("✓ OBV bestätigt Aufwärtstrend");
        }
        else if (signal.obv.signal.contains("WARNUNG"))
        {
            signal.score -= 2;
            signal.factors.add("✗ OBV-Divergenz - Vorsicht!");
        }
        else if (signal.obv.signal.contains("BEARISCH"))
        {
            signal.score -= 2;
            signal.factors.add("✗ OBV bearisch");
        }

        // 5. RSI-Interpretation (im Kontext!)
        if (rsi != null)
        {
            if (signal.trend.uptrend && signal.adx.adx > 30)
            {
                // In starken Trends: RSI > 70 ist OK!
                if (rsi > 80)
                {
                    signal.score -= 1;
                    signal.factors.add(String.format("○ RSI sehr hoch (%.1f) - aber in starkem Trend akzeptabel",
                                                     rsi));
                }
                else if (rsi > 70)
                {
                    signal.factors.add(String.format("○ RSI überkauft (%.1f) - in Aufwärtstrend normal",
                                                     rsi));
                }
                else if (rsi < 30)
                {
                    signal.score += 1;
                    signal.factors.add(String.format("✓ RSI niedrig (%.1f) - gute Entry-Chance", rsi));
                }
            }
            else
            {
                // Ohne starken Trend: Klassische RSI-Interpretation
                if (rsi > 70)
                {
                    signal.score -= 1;
                    signal.factors.add(String.format("✗ RSI überkauft (%.1f) - Vorsicht", rsi));
                }
                else if (rsi < 30)
                {
                    signal.score += 1;
                    signal.factors.add(String.format("✓ RSI überverkauft (%.1f)", rsi));
                }
            }
        }

        // ==================== FINALE ENTSCHEIDUNG ====================

        if (signal.score >= 7)
        {
            signal.action = MomentumSignal.Action.STRONG_BUY;
            signal.primaryReason = "Sehr starkes Momentum mit bestätigtem Aufwärtstrend";
            signal.stopLoss = signal.trend.ema21 * 0.95; // 5% unter EMA21
            signal.takeProfit = currentPrice * 1.20; // +20% Ziel
        }
        else if (signal.score >= 4)
        {
            signal.action = MomentumSignal.Action.BUY;
            signal.primaryReason = "Gutes Momentum und Aufwärtstrend";
            signal.stopLoss = signal.trend.ema21 * 0.97; // 3% unter EMA21
            signal.takeProfit = currentPrice * 1.15; // +15% Ziel
        }
        else if (signal.score >= 1)
        {
            signal.action = MomentumSignal.Action.HOLD;
            signal.primaryReason = "Gemischte Signale - Position halten falls investiert";
        }
        else if (signal.score >= -3)
        {
            signal.action = MomentumSignal.Action.SELL;
            signal.primaryReason = "Momentum schwächt ab oder Trend bricht";
        }
        else
        {
            signal.action = MomentumSignal.Action.STRONG_SELL;
            signal.primaryReason = "Starke negative Signale - Exit empfohlen";
        }

        // Bei keinem Trend: WAIT
        if (!signal.trend.uptrend && signal.adx.adx < 20)
        {
            signal.action = MomentumSignal.Action.WAIT;
            signal.primaryReason = "Kein klarer Trend - Abwarten";
        }

        return signal;
    }

    // ==================== HELPER FUNKTIONEN ====================


    private static double calculateEMA(List<DailyPrice> prices, int period)
    {
        if (prices.size() < period)
            return 0;

        double multiplier = 2.0 / (period + 1);

        // Initial SMA
        double sum = 0;
        for (int i = 0; i < period; i++ )
        {
            sum += prices.get(i).getAdjClose();
        }
        double ema = sum / period;

        // EMA berechnen
        for (int i = period - 1; i >= 0; i-- )
        {
            ema = (prices.get(i).getAdjClose() - ema) * multiplier + ema;
        }

        return ema;
    }


    private static double calculateEMAFromDoubles(List<Double> values, int period)
    {
        if (values.size() < period)
            return 0;

        double multiplier = 2.0 / (period + 1);

        double sum = 0;
        for (int i = 0; i < period; i++ )
        {
            sum += values.get(i);
        }
        double ema = sum / period;

        for (int i = period; i < values.size(); i++ )
        {
            ema = (values.get(i) - ema) * multiplier + ema;
        }

        return ema;
    }


    /**
     * Berechnet den Relative Strength Index (RSI)
     * 
     * @param prices Liste der Preise (Index 0 = neuestes Datum)
     * @param period Standard: 14
     * @return RSI Wert (0-100)
     */
    public static double calculateRSI(List<DailyPrice> prices, int period)
    {
        if (prices == null || prices.size() <= period)
            return 50.0;

        double sumGain = 0;
        double sumLoss = 0;
        int dataSize = prices.size();

        // Initial Average (Simple Moving Average)
        // Wir starten von hinten (älteste Daten), um den geglätteten Wert korrekt aufzubauen
        for (int i = 0; i < period; i++ )
        {
            int index = (dataSize - 2) - i;
            // Prüfen ob index gültig
            if (index < 0)
                break;

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

        // Wilder's Smoothing bis zum aktuellen Datum
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

    // ==================== MAIN - BEISPIEL ====================


    public static void main(String[] args)
    {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.mariadb.jdbc.Driver");
        // Fix für "RSA public key is not available": allowPublicKeyRetrieval=true
        dataSource.setUrl("jdbc:mariadb://192.168.178.31:3306/StocksDB?allowPublicKeyRetrieval=true&useSSL=false");
        dataSource.setUsername("stocksdb");
        dataSource.setPassword("stocksdb");

        Map<String, Integer> scores = new HashMap<>();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        MarketDataService marketDataService = new MarketDataService(jdbcTemplate);

        List<String> symbols = Arrays.asList("MU");//marketDataService.getAllSymbols();
        MomentumSignal signal = null;
        for (String symbol : symbols)
        {
            List<DailyPrice> prices = marketDataService.getMarketData(symbol);

            if (prices == null || prices.isEmpty())
            {
                System.err.println("Keine Daten gefunden für " + symbol);
                continue;
            }

            System.out.println("Geladene Datensätze: " + prices.size());
            System.out.println("Aktueller Preis: " + prices.get(0).getAdjClose());

            // 2. RSI berechnen
            double rsi = calculateRSI(prices, 14);
            System.out.println("Aktueller RSI(14): " + String.format("%.2f", rsi));

            // 3. Momentum Signal generieren (inkl. ADX, Trend, Volumen, Exit-Levels)
            signal = generateMomentumSignal(prices, rsi);
            scores.put(symbol, signal.score);
        }
        boolean bprintScores = true;
        if (signal != null && bprintScores)
        {
            // 4. Ergebnis ausgeben
            System.out.println("\n" + signal.toString());

            // Zusätzliche Details ausgeben, falls gewünscht
            System.out.println("--------------------------------------------------");
            System.out.println("Zusätzliche Details:");
            System.out.println("ADX Trendstärke: " + signal.adx.trendStrength);
            System.out.println("Volumen Analyse: " + signal.volume.interpretation);
            System.out.println("Dynamischer Stop-Loss: " + String.format("%.2f", signal.stopLoss));
            System.out.println("Dynamischer Take-Profit: " + String.format("%.2f", signal.takeProfit));
        }
        else if (!scores.isEmpty())
        {
            String maxDaySql = "SELECT MAX(cDayCounter) FROM tIndicators";
            Long maxDay = jdbcTemplate.queryForObject(maxDaySql, Long.class);

            List<Entry<String, Integer>> scoreEntries = new ArrayList<>(scores.entrySet());
            jdbcTemplate.batchUpdate("UPDATE tIndicators SET cMomentumScore = ? WHERE cSymbol = ? AND cDayCounter = ?",
                                     new BatchPreparedStatementSetter()
                                     {
                                         @Override
                                         public void setValues(PreparedStatement ps, int i)
                                             throws SQLException
                                         {
                                             Entry<String, Integer> entry = scoreEntries.get(i);
                                             ps.setInt(1, entry.getValue());
                                             ps.setString(2, entry.getKey());
                                             ps.setLong(3, maxDay);
                                         }


                                         @Override
                                         public int getBatchSize()
                                         {
                                             return scoreEntries.size();
                                         }
                                     });
        }

    }
}
