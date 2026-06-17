package com.straube.jones.dto;


import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Response DTO für den {@code GET /api/stocks/intraday/rsi} Endpoint.
 *
 * <p>Enthält die berechneten RSI-Indikatorwerte für jeden Zeitbucket eines einzelnen
 * Handelstages.  Die RSI-Berechnung wird auf Basis der vollständigen verfügbaren
 * Intraday-Historie (bis zu 14 Tage) durchgeführt, um die Anlaufphase zu überwinden.
 * Zurückgegeben werden jedoch nur die Werte des angeforderten Handelstages.
 *
 * <p><b>Felder:</b>
 * <ul>
 *   <li>{@link #isin}    – ISIN des abgefragten Wertpapiers.</li>
 *   <li>{@link #date}    – Handelsdatum (ISO-8601, z.B. {@code "2026-03-19"}).</li>
 *   <li>{@link #reduce}  – Bucket-Größe aus dem {@code reduce}-Parameter
 *                          (z.B. {@code "5M"}); fehlt wenn kein Reduce angegeben.</li>
 *   <li>{@link #period}  – RSI-Periode (Standard: 14).</li>
 *   <li>{@link #data}    – Liste der RSI-Datenpunkte für den angeforderten Tag.</li>
 * </ul>
 *
 * <p><b>Beispiel-Antwort</b> (reduce={@code "5M"}, period=14):
 * <pre>
 * {
 *   "isin":    "US0378331005",
 *   "date":    "2026-03-19",
 *   "reduce":  "5M",
 *   "period":  14,
 *   "data": [
 *     { "timestamp": 1742378400000, "rsi": 58.42 },
 *     { "timestamp": 1742378700000, "rsi": null  }
 *   ]
 * }
 * </pre>
 *
 * <p><b>Hinweis zu {@code null}-Werten:</b> Für die ersten {@code period} Buckets des
 * Beobachtungszeitraums (Anlaufphase der Wilder-Glättung) ist {@code rsi} {@code null}.
 *
 * @see com.straube.jones.trader.indicators.IndicatorCalculator#calculateRSI
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntradayRsiResponse
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
     * RSI-Periode (Wilder's Smoothing).
     * Standard: 14.
     */
    private int period;

    /**
     * RSI-Datenpunkte für den angeforderten Handelstag.
     * Jeder Eintrag entspricht einem Zeitbucket (oder Raw-Tick bei fehlendem {@code reduce}).
     */
    private List<RsiDataPoint> data;


    // -----------------------------------------------------------------------
    // Inner class
    // -----------------------------------------------------------------------

    /**
     * Einzelner RSI-Datenpunkt für einen Zeitbucket.
     *
     * <p>{@code rsi} ist {@code null}, wenn für diesen Punkt noch nicht genügend
     * Vorgeschichte für die RSI-Berechnung vorhanden war (Anlaufphase).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RsiDataPoint
    {
        /**
         * Bucket-Start-Zeitstempel in Millisekunden seit Epoch (Unix-Zeit).
         * Bei fehlendem {@code reduce}: exakter Snapshot-Zeitstempel.
         */
        private Long timestamp;

        /**
         * RSI-Wert im Bereich [0, 100].
         * {@code null} während der Anlaufphase.
         */
        private Double rsi;


        // --- Getters & Setters ---

        public Long getTimestamp()
        {
            return timestamp;
        }


        public void setTimestamp(Long timestamp)
        {
            this.timestamp = timestamp;
        }


        public Double getRsi()
        {
            return rsi;
        }


        public void setRsi(Double rsi)
        {
            this.rsi = rsi;
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


    public int getPeriod()
    {
        return period;
    }


    public void setPeriod(int period)
    {
        this.period = period;
    }


    public List<RsiDataPoint> getData()
    {
        return data;
    }


    public void setData(List<RsiDataPoint> data)
    {
        this.data = data;
    }
}
