package com.straube.jones.trader.service;

import java.util.List;

import com.straube.jones.trader.dto.DailyPrice;

/**
 * Berechnet den Relative Strength Index (RSI).
 */
public class RsiService {

    /**
     * RSI nach Welles Wilder (Standard).
     *
     * Bedeutung:
     * Zeigt Überkauft (>70) oder Überverkauft (<30).
     *
     * Skala:
     * 0–100
     */
    public double calculateRSI(List<DailyPrice> prices, int period) {

        double gains = 0;
        double losses = 0;

        for (int i = 0; i < period; i++) {
            double change = prices.get(i).getClose() - prices.get(i + 1).getClose();
            if (change > 0) {
                gains += change;
            } else {
                losses += Math.abs(change);
            }
        }

        if (losses == 0) {
            return 100;
        }

        double rs = gains / losses;
        return 100 - (100 / (1 + rs));
    }
}
