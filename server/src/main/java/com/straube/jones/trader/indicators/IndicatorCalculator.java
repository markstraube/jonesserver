package com.straube.jones.trader.indicators;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.straube.jones.db.DayCounter;
import com.straube.jones.trader.dto.DailyPrice;
import com.straube.jones.trader.dto.IndicatorDto;

/**
 * Berechnet technische Indikatoren für eine Zeitreihe von Preisen.
 */
@Service
public class IndicatorCalculator
{

    /**
     * Berechnet Indikatoren für die gesamte Preisliste.
     * Die Liste muss absteigend sortiert sein (Index 0 = neuestes Datum).
     * 
     * @param symbol Das Aktiensymbol
     * @param prices Liste der Tagespreise
     * @return Liste von IndicatorDto Objekten (gleiche Reihenfolge wie prices)
     */
    public List<IndicatorDto> calculateIndicators(String symbol, List<DailyPrice> prices)
    {
        if (prices == null || prices.isEmpty())
        { return Collections.emptyList(); }

        int size = prices.size();
        List<IndicatorDto> results = new ArrayList<>(size);

        // Wir müssen die Liste umdrehen (aufsteigend: alt -> neu), um die Indikatoren effizient zu berechnen
        // Da die Eingabe absteigend ist (0 = neu), ist reverse = chronologisch
        List<DailyPrice> chronologicalPrices = new ArrayList<>(prices);
        Collections.reverse(chronologicalPrices);

        // Arrays für Indikatoren (parallel zu chronologicalPrices)
        Double[] sma5 = calculateSMA(chronologicalPrices, 5);
        Double[] sma10 = calculateSMA(chronologicalPrices, 10);
        Double[] sma20 = calculateSMA(chronologicalPrices, 20);
        Double[] sma30 = calculateSMA(chronologicalPrices, 30);

        Double[] ema5 = calculateEMA(chronologicalPrices, 5);
        Double[] ema10 = calculateEMA(chronologicalPrices, 10);
        Double[] ema20 = calculateEMA(chronologicalPrices, 20);
        Double[] ema30 = calculateEMA(chronologicalPrices, 30);

        // Bollinger Bands (15 Tage, 2.0 StdDev)
        BollingerResult[] bollinger = calculateBollingerBands(chronologicalPrices, 15, 2.0);

        // RSI (14 Tage)
        Double[] rsi = calculateRSI(chronologicalPrices, 14);

        // MACD (12, 26, 9)
        MacdResult[] macd = calculateMACD(chronologicalPrices, 12, 26, 9);

        // Support/Resistance (z.B. 20 Tage Lookback)
        Double[] support = calculateSupport(chronologicalPrices, 20);
        Double[] resistance = calculateResistance(chronologicalPrices, 20);

        // Zusammenfügen
        for (int i = 0; i < size; i++ )
        {
            IndicatorDto dto = new IndicatorDto();
            DailyPrice price = chronologicalPrices.get(i);

            dto.setSymbol(symbol);
            // Convert LocalDate to Long timestamp
            dto.setDate(DayCounter.toTimestamp(DayCounter.get(price.getDate())));
            dto.setCurrency(price.getCurrency());
            dto.setVolume((double)price.getVolume());

            dto.setSma5(sma5[i]);
            dto.setSma10(sma10[i]);
            dto.setSma20(sma20[i]);
            dto.setSma30(sma30[i]);

            dto.setEma5(ema5[i]);
            dto.setEma10(ema10[i]);
            dto.setEma20(ema20[i]);
            dto.setEma30(ema30[i]);

            if (bollinger[i] != null)
            {
                dto.setBb15low(bollinger[i].lower);
                dto.setBb15mid(bollinger[i].middle);
                dto.setBb15high(bollinger[i].upper);
            }

            dto.setRsi(rsi[i]);

            if (macd[i] != null)
            {
                dto.setMacdValue(macd[i].value);
                dto.setMacdSignal(macd[i].signal);
            }

            dto.setSupport(support[i]);
            dto.setResistance(resistance[i]);

            results.add(dto);
        }

        // Zurück in absteigende Reihenfolge (neu -> alt)
        Collections.reverse(results);
        return results;
    }


    private Double[] calculateSMA(List<DailyPrice> prices, int period)
    {
        Double[] result = new Double[prices.size()];
        double sum = 0;
        for (int i = 0; i < prices.size(); i++ )
        {
            sum += prices.get(i).getAdjClose();
            if (i >= period)
            {
                sum -= prices.get(i - period).getAdjClose();
                result[i] = sum / period;
            }
            else if (i == period - 1)
            {
                result[i] = sum / period;
            }
            else
            {
                result[i] = null;
            }
        }
        return result;
    }


    private Double[] calculateEMA(List<DailyPrice> prices, int period)
    {
        Double[] result = new Double[prices.size()];
        double k = 2.0 / (period + 1);
        Double ema = null;

        // Initialer SMA
        double sum = 0;
        for (int i = 0; i < prices.size(); i++ )
        {
            sum += prices.get(i).getAdjClose();
            if (i == period - 1)
            {
                ema = sum / period;
                result[i] = ema;
            }
            else if (i >= period)
            {
                ema = prices.get(i).getAdjClose() * k + ema * (1 - k);
                result[i] = ema;
            }
            else
            {
                result[i] = null;
            }
        }
        return result;
    }

    private static class BollingerResult
    {
        double lower, middle, upper;
    }

    private BollingerResult[] calculateBollingerBands(List<DailyPrice> prices,
                                                      int period,
                                                      double stdDevMultiplier)
    {
        BollingerResult[] result = new BollingerResult[prices.size()];
        Double[] sma = calculateSMA(prices, period);

        for (int i = 0; i < prices.size(); i++ )
        {
            if (sma[i] == null)
                continue;

            double sumSqDiff = 0;
            for (int j = 0; j < period; j++ )
            {
                double diff = prices.get(i - j).getAdjClose() - sma[i];
                sumSqDiff += diff * diff;
            }
            double stdDev = Math.sqrt(sumSqDiff / period);

            BollingerResult res = new BollingerResult();
            res.middle = sma[i];
            res.upper = sma[i] + stdDevMultiplier * stdDev;
            res.lower = sma[i] - stdDevMultiplier * stdDev;
            result[i] = res;
        }
        return result;
    }


    private Double[] calculateRSI(List<DailyPrice> prices, int period)
    {
        Double[] result = new Double[prices.size()];
        if (prices.size() <= period)
            return result;

        double avgGain = 0;
        double avgLoss = 0;

        // Initial Average
        for (int i = 1; i <= period; i++ )
        {
            double change = prices.get(i).getAdjClose() - prices.get(i - 1).getAdjClose();
            if (change > 0)
                avgGain += change;
            else
                avgLoss += Math.abs(change);
        }
        avgGain /= period;
        avgLoss /= period;

        result[period] = 100.0 - (100.0 / (1.0 + (avgGain / (avgLoss == 0 ? 1 : avgLoss)))); // Avoid div by zero

        // Smoothed
        for (int i = period + 1; i < prices.size(); i++ )
        {
            double change = prices.get(i).getAdjClose() - prices.get(i - 1).getAdjClose();
            double gain = change > 0 ? change : 0;
            double loss = change < 0 ? Math.abs(change) : 0;

            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;

            if (avgLoss == 0)
            {
                result[i] = 100.0;
            }
            else
            {
                double rs = avgGain / avgLoss;
                result[i] = 100.0 - (100.0 / (1.0 + rs));
            }
        }
        return result;
    }

    private static class MacdResult
    {
        double value, signal;
    }

    private MacdResult[] calculateMACD(List<DailyPrice> prices,
                                       int shortPeriod,
                                       int longPeriod,
                                       int signalPeriod)
    {
        MacdResult[] result = new MacdResult[prices.size()];
        Double[] emaShort = calculateEMA(prices, shortPeriod);
        Double[] emaLong = calculateEMA(prices, longPeriod);

        // MACD Line = EMA_short - EMA_long
        Double[] macdLine = new Double[prices.size()];
        for (int i = 0; i < prices.size(); i++ )
        {
            if (emaShort[i] != null && emaLong[i] != null)
            {
                macdLine[i] = emaShort[i] - emaLong[i];
            }
        }

        // Signal Line = EMA(MACD Line, signalPeriod)
        // Wir müssen EMA auf macdLine berechnen. Da calculateEMA DailyPrice erwartet, kopieren wir Logik oder wrappen.
        // Einfacher: EMA Logik inline für Double array
        Double[] signalLine = calculateEMAOnArray(macdLine, signalPeriod);

        for (int i = 0; i < prices.size(); i++ )
        {
            if (macdLine[i] != null && signalLine[i] != null)
            {
                MacdResult res = new MacdResult();
                res.value = macdLine[i];
                res.signal = signalLine[i];
                result[i] = res;
            }
        }
        return result;
    }


    private Double[] calculateEMAOnArray(Double[] values, int period)
    {
        Double[] result = new Double[values.length];
        double k = 2.0 / (period + 1);
        Double ema = null;

        // Finde ersten nicht-null Wert Index
        int firstValidIndex = -1;
        for (int i = 0; i < values.length; i++ )
        {
            if (values[i] != null)
            {
                firstValidIndex = i;
                break;
            }
        }

        if (firstValidIndex == -1 || values.length - firstValidIndex < period)
            return result;

        // Initial SMA
        double sum = 0;
        int count = 0;
        for (int i = firstValidIndex; i < values.length; i++ )
        {
            if (values[i] == null)
                continue; // Sollte nicht passieren wenn lückenlos
            sum += values[i];
            count++ ;
            if (count == period)
            {
                ema = sum / period;
                result[i] = ema;

                // Weiter mit EMA
                for (int j = i + 1; j < values.length; j++ )
                {
                    if (values[j] != null)
                    {
                        ema = values[j] * k + ema * (1 - k);
                        result[j] = ema;
                    }
                }
                break;
            }
        }
        return result;
    }


    private Double[] calculateSupport(List<DailyPrice> prices, int period)
    {
        Double[] result = new Double[prices.size()];
        for (int i = period; i < prices.size(); i++ )
        {
            double min = Double.MAX_VALUE;
            for (int j = 0; j < period; j++ )
            {
                min = Math.min(min, prices.get(i - j).getLow());
            }
            result[i] = min;
        }
        return result;
    }


    private Double[] calculateResistance(List<DailyPrice> prices, int period)
    {
        Double[] result = new Double[prices.size()];
        for (int i = period; i < prices.size(); i++ )
        {
            double max = -Double.MAX_VALUE;
            for (int j = 0; j < period; j++ )
            {
                max = Math.max(max, prices.get(i - j).getHigh());
            }
            result[i] = max;
        }
        return result;
    }
}
