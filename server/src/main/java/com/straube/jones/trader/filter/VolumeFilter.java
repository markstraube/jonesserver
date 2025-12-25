package com.straube.jones.trader.filter;

/**
 * Bewertet, ob Kursbewegungen durch echtes Marktinteresse
 * (Volumen) bestätigt werden.
 */
public class VolumeFilter {

    /**
     * Durchschnittliches Tagesvolumen der letzten 20 Tage.
     * Quelle: Börse
     */
    private long averageVolume20d;

    /**
     * Volumen am letzten steigenden Tag.
     * Quelle: Börse
     */
    private long volumeOnUpDay;

    /**
     * Volumen am letzten Rücksetzer-Tag.
     * Quelle: Börse
     */
    private long volumeOnPullbackDay;

    public long getAverageVolume20d() { return averageVolume20d; }
    public void setAverageVolume20d(long averageVolume20d) { this.averageVolume20d = averageVolume20d; }
    public long getVolumeOnUpDay() { return volumeOnUpDay; }
    public void setVolumeOnUpDay(long volumeOnUpDay) { this.volumeOnUpDay = volumeOnUpDay; }
    public long getVolumeOnPullbackDay() { return volumeOnPullbackDay; }
    public void setVolumeOnPullbackDay(long volumeOnPullbackDay) { this.volumeOnPullbackDay = volumeOnPullbackDay; }
}
