package com.straube.jones.dto;


import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Response DTO für den {@code GET /api/stocks/intraday/macd} Endpoint.
 *
 * <p>Enthält die berechneten MACD-Indikatorwerte (MACD-Linie, Signal-Linie, Histogramm)
 * für jeden Zeitbucket eines einzelnen Handelstages.  Die MACD-Berechnung wird auf Basis
 * der vollständigen verfügbaren Intraday-Historie (bis zu 14 Tage) durchgeführt, um
 * die Anlaufphase der exponentiell gewichteten Mittelwerte zu überwinden.  Zurückgegeben
 * werden jedoch nur die Werte des angeforderten Handelstages.
 *
 * <p><b>Felder:</b>
 * <ul>
 *   <li>{@link #isin}         – ISIN des abgefragten Wertpapiers.</li>
 *   <li>{@link #date}         – Handelsdatum (ISO-8601, z.B. {@code "2026-03-19"}).</li>
 *   <li>{@link #reduce}       – Bucket-Größe aus dem {@code reduce}-Parameter
 *                               (z.B. {@code "5M"}); fehlt wenn kein Reduce angegeben.</li>
 *   <li>{@link #shortPeriod}  – EMA-Periode der kurzen Linie (Standard: 12).</li>
 *   <li>{@link #longPeriod}   – EMA-Periode der langen Linie (Standard: 26).</li>
 *   <li>{@link #signalPeriod} – EMA-Periode der Signal-Linie (Standard: 9).</li>
 *   <li>{@link #data}         – Liste der MACD-Datenpunkte für den angeforderten Tag.</li>
 * </ul>
 *
 * <p><b>Beispiel-Antwort</b> (reduce={@code "5M"}, shortPeriod=12, longPeriod=26, signalPeriod=9):
 * <pre>
 * {
 *   "isin":          "US0378331005",
 *   "date":          "2026-03-19",
 *   "reduce":        "5M",
 *   "short-period":  12,
 *   "long-period":   26,
 *   "signal-period": 9,
 *   "data": [
 *     {
 *       "timestamp": 1742378400000,
 *       "macd":      0.1234,
 *       "signal":    0.1100,
 *       "histogram": 0.0134
 *     },
 *     {
 *       "timestamp": 1742378700000,
 *       "macd":      null,
 *       "signal":    null,
 *       "histogram": null
 *     }
 *   ]
 * }
 * </pre>
 *
 * <p><b>Hinweis zu {@code null}-Werten:</b> Für die ersten Buckets des Beobachtungszeitraums
 * (Anlaufphase der EMA-Berechnung) sind {@code macd}, {@code signal} und {@code histogram}
 * {@code null}.  Ab dem Zeitpunkt, an dem genügend historische Daten vorhanden sind
 * ({@code longPeriod + signalPeriod} Buckets), liefert die Berechnung valide Werte.
 *
 * @see com.straube.jones.trader.indicators.IndicatorCalculator#calculateMACD
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntradayMacdResponse
{
    /** ISIN des abgefragten Wertpapiers. */
    private String isin;

    /** Handelsdatum des angeforderten Tages im ISO-8601-Format (z.B. {@code "2026-03-19"}). */
    private String date;

    /**
     * Bucket-Größe als String (z.B. {@code "5M"}).
     * Fehlt in der JSON-Antwort, wenn kein {@code reduce}-Parameter übergeben wurde.
     */
    private String reduce;

    /**
     * EMA-Periode für die kurze Linie der MACD-Berechnung.
     * Standard: 12.
     */
    @JsonProperty("short-period")
    private int shortPeriod;

    /**
     * EMA-Periode für die lange Linie der MACD-Berechnung.
     * Standard: 26.
     */
    @JsonProperty("long-period")
    private int longPeriod;

    /**
     * EMA-Periode der Signal-Linie.
     * Standard: 9.
     */
    @JsonProperty("signal-period")
    private int signalPeriod;

    /**
     * MACD-Datenpunkte für den angeforderten Handelstag.
     * Jeder Eintrag entspricht einem Zeitbucket (oder Raw-Tick bei fehlendem {@code reduce}).
     */
    private List<MacdDataPoint> data;


    // -----------------------------------------------------------------------
    // Inner class
    // -----------------------------------------------------------------------

    /**
     * Einzelner MACD-Datenpunkt für einen Zeitbucket.
     *
     * <p>{@code macd}, {@code signal} und {@code histogram} sind {@code null},
     * wenn für diesen Punkt noch nicht genügend Vorgeschichte für die EMA-Berechnung
     * vorhanden war (Anlaufphase).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MacdDataPoint
    {
        /**
         * Bucket-Start-Zeitstempel in Millisekunden seit Epoch (Unix-Zeit).
         * Bei fehlendem {@code reduce}: exakter Snapshot-Zeitstempel.
         */
        private Long timestamp;

        /**
         * MACD-Linie: EMA(shortPeriod) − EMA(longPeriod).
         * {@code null} während der Anlaufphase.
         */
        private Double macd;

        /**
         * Signal-Linie: EMA(MACD-Linie, signalPeriod).
         * {@code null} während der Anlaufphase.
         */
        private Double signal;

        /**
         * Histogramm: MACD-Linie − Signal-Linie.
         * Positiver Wert = bullisches Momentum, negativer Wert = bearisches Momentum.
         * {@code null} wenn {@code macd} oder {@code signal} noch nicht berechenbar.
         */
        private Double histogram;


        // --- Getters & Setters ---

        public Long getTimestamp()
        {
            return timestamp;
        }


        public void setTimestamp(Long timestamp)
        {
            this.timestamp = timestamp;
        }


        public Double getMacd()
        {
            return macd;
        }


        public void setMacd(Double macd)
        {
            this.macd = macd;
        }


        public Double getSignal()
        {
            return signal;
        }


        public void setSignal(Double signal)
        {
            this.signal = signal;
        }


        public Double getHistogram()
        {
            return histogram;
        }


        public void setHistogram(Double histogram)
        {
            this.histogram = histogram;
        }
    }


    // -----------------------------------------------------------------------
    // Getters & Setters
    // -----------------------------------------------------------------------

    public String getIsin()
    {
        return isin;
    }


    public void setIsin(String isin)
    {
        this.isin = isin;
    }


    public String getDate()
    {
        return date;
    }


    public void setDate(String date)
    {
        this.date = date;
    }


    public String getReduce()
    {
        return reduce;
    }


    public void setReduce(String reduce)
    {
        this.reduce = reduce;
    }


    public int getShortPeriod()
    {
        return shortPeriod;
    }


    public void setShortPeriod(int shortPeriod)
    {
        this.shortPeriod = shortPeriod;
    }


    public int getLongPeriod()
    {
        return longPeriod;
    }


    public void setLongPeriod(int longPeriod)
    {
        this.longPeriod = longPeriod;
    }


    public int getSignalPeriod()
    {
        return signalPeriod;
    }


    public void setSignalPeriod(int signalPeriod)
    {
        this.signalPeriod = signalPeriod;
    }


    public List<MacdDataPoint> getData()
    {
        return data;
    }


    public void setData(List<MacdDataPoint> data)
    {
        this.data = data;
    }
}
