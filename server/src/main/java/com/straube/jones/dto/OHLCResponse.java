package com.straube.jones.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Response DTO für den {@code GET /api/stocks/intraday/current-candle} Endpoint.
 *
 * <p>Kapselt den aktuellen (laufenden) OHLC-Kerze-Datensatz für einen einzelnen
 * Minuten-Bucket zusammen mit den Bucket-Metadaten.
 *
 * <p><b>Felder:</b>
 * <ul>
 *   <li>{@link #isin}        – ISIN des abgefragten Wertpapiers.</li>
 *   <li>{@link #date}        – Handelsdatum (ISO-8601, z.B. {@code "2026-03-19"}).</li>
 *   <li>{@link #bucket}      – Bucket-Größe als String (z.B. {@code "5M"}).</li>
 *   <li>{@link #bucketStart} – Bucket-Beginn in Millisekunden seit Epoch (inklusiv).</li>
 *   <li>{@link #bucketEnd}   – Bucket-Ende in Millisekunden seit Epoch (inklusiv,
 *                              also {@code bucketStart + bucketMinutes*60*1000 - 1}).</li>
 *   <li>{@link #candle}      – Aggregierter OHLC-Datensatz für den Bucket.</li>
 * </ul>
 *
 * <p><b>Beispiel-Antwort:</b>
 * <pre>
 * {
 *   "isin":         "US0378331005",
 *   "date":         "2026-03-19",
 *   "bucket":       "5M",
 *   "bucket-start": 1742389200000,
 *   "bucket-end":   1742389499999,
 *   "candle": {
 *     "timestamp": 1742389200000,
 *     "open":      150.25,
 *     "close":     151.00,
 *     "high":      151.50,
 *     "low":       150.10,
 *     "bid":       150.95,
 *     "ask":       151.05,
 *     "avg":       150.80,
 *     "volume":    12500,
 *     "delta":     0.52
 *   }
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OHLCResponse
{
    /** ISIN des abgefragten Wertpapiers. */
    private String isin;

    /** Handelsdatum im ISO-8601-Format (z.B. {@code "2026-03-19"}). */
    private String date;

    /**
     * Bucket-Größe als String (z.B. {@code "1M"}, {@code "5M"}, {@code "10M"}).
     * Entspricht dem übergebenen {@code bucket}-Parameter.
     */
    private String bucket;

    /**
     * Beginn des Buckets als Unix-Zeitstempel in Millisekunden (inklusiv).
     * Entspricht dem letzten vollen Bucket-Beginn vor der aktuellen Uhrzeit,
     * ausgerichtet an der vollen Stunde (Europe/Berlin).
     */
    @JsonProperty("bucket-start")
    private Long bucketStart;

    /**
     * Ende des Buckets als Unix-Zeitstempel in Millisekunden (inklusiv).
     * Berechnet als {@code bucketStart + bucketMinutes * 60 * 1000 - 1}.
     */
    @JsonProperty("bucket-end")
    private Long bucketEnd;

    /**
     * Aggregierter OHLC-Kerze-Datensatz für den Bucket.
     * Enthält open, close, high, low, bid, ask, avg, volume und delta.
     * Berechnet aus allen {@code tTradegateIntraday}-Snapshots innerhalb der Bucket-Grenzen.
     */
    private IntradayResponse.IntradayDataPoint candle;


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


    public String getBucket()
    {
        return bucket;
    }


    public void setBucket(String bucket)
    {
        this.bucket = bucket;
    }


    public Long getBucketStart()
    {
        return bucketStart;
    }


    public void setBucketStart(Long bucketStart)
    {
        this.bucketStart = bucketStart;
    }


    public Long getBucketEnd()
    {
        return bucketEnd;
    }


    public void setBucketEnd(Long bucketEnd)
    {
        this.bucketEnd = bucketEnd;
    }


    public IntradayResponse.IntradayDataPoint getCandle()
    {
        return candle;
    }


    public void setCandle(IntradayResponse.IntradayDataPoint candle)
    {
        this.candle = candle;
    }
}
