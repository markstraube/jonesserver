package com.straube.jones.trader.filter;

/**
 * Beschreibt relevante Unterstützungszonen,
 * an denen der Kurs nach oben drehen könnte.
 */
public class SupportFilter {

    /**
     * Nächstgelegene Unterstützung (Preisniveau).
     *
     * Quelle:
     * Technisch ermittelt (Tiefs, gleitende Durchschnitte)
     */
    private double nearestSupportLevel;

    /**
     * Prozentualer Abstand des aktuellen Kurses zur Unterstützung.
     *
     * Berechnung:
     * (Close - Support) / Support × 100
     */
    private double distanceToSupportPercent;

    /**
     * Art der Unterstützung.
     *
     * Beispiele:
     * EMA20, SMA50, Trendlinie, horizontales Level
     */
    private String supportType;

    public double getNearestSupportLevel() { return nearestSupportLevel; }
    public void setNearestSupportLevel(double nearestSupportLevel) { this.nearestSupportLevel = nearestSupportLevel; }
    public double getDistanceToSupportPercent() { return distanceToSupportPercent; }
    public void setDistanceToSupportPercent(double distanceToSupportPercent) { this.distanceToSupportPercent = distanceToSupportPercent; }
    public String getSupportType() { return supportType; }
    public void setSupportType(String supportType) { this.supportType = supportType; }
}
