package com.straube.jones.trader.service;


import java.util.List;

import com.straube.jones.trader.dto.DailyPrice;

/**
 * Analysiert Handelsvolumen.
 */
public class VolumeAnalysisService
{

    /**
     * Durchschnittliches Volumen über N Tage.
     */
    public long calculateAverageVolume(List<DailyPrice> prices, int period)
    {
        return Math.round(prices.stream().limit(period).mapToLong(DailyPrice::getVolume).average().orElse(0));
    }


    /**
     * Prüft, ob ein steigender Tag durch hohes Volumen bestätigt wurde.
     */
    public boolean isUpDayWithHighVolume(DailyPrice day, long averageVolume)
    {
        return day.getClose() > day.getOpen() && day.getVolume() >= averageVolume;
    }


    /**
     * Prüft, ob ein Rücksetzer mit geringem Volumen stattfand.
     */
    public boolean isPullbackWithLowVolume(DailyPrice day, long averageVolume)
    {
        return day.getClose() < day.getOpen() && day.getVolume() <= averageVolume;
    }
}
