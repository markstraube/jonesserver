package com.straube.jones.trader.service;


import java.util.List;

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
}
