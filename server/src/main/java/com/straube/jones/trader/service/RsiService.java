package com.straube.jones.trader.service;


import java.util.List;

import com.straube.jones.trader.dto.DailyPrice;

/**
 * Berechnet den Relative Strength Index (RSI).
 */
public class RsiService
{

    /**
     * RSI nach Welles Wilder (Standard).
     *
     * Bedeutung:
     * Zeigt Überkauft (>70) oder Überverkauft (<30).
     *
     * Skala:
     * 0–100
     */
    public double calculateRSI(List<DailyPrice> prices, int period)
    {
        if (prices == null || prices.size() <= period + 1)
        { return 0; }

        // 1. Initialer Durchschnitt Gewinn/Verlust (Einfacher Durchschnitt) über die ersten 'period' Änderungen (älteste verfügbare)
        double sumGain = 0;
        double sumLoss = 0;
        int dataSize = prices.size();

        // Wir beginnen mit den ältesten Daten, um die Glättung korrekt aufzubauen.
        // Die Liste ist sortiert: Index 0 = Neuestes Datum.
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

        // 2. Glättung für die restlichen Daten nach Wilder's Smoothing
        int startIndex = (dataSize - 2) - period;

        for (int i = startIndex; i >= 0; i-- )
        {
            double change = prices.get(i).getAdjClose() - prices.get(i + 1).getAdjClose();
            double currentGain = 0;
            double currentLoss = 0;

            if (change > 0)
            {
                currentGain = change;
            }
            else
            {
                currentLoss = Math.abs(change);
            }

            avgGain = ((avgGain * (period - 1)) + currentGain) / period;
            avgLoss = ((avgLoss * (period - 1)) + currentLoss) / period;
        }

        if (avgLoss == 0)
        { return 100; }

        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }
}
