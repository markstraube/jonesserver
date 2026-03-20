package com.straube.jones.trader.dto;

/**
 * Minimale Preisrepräsentation für Indikatorberechnungen.
 *
 * <p>Dieses Interface entkoppelt die Berechnungslogik (z.B. EMA, MACD) von konkreten
 * Datentypen. Sowohl Tagesdaten ({@link DailyPrice}) als auch Intraday-Buckets
 * ({@code IntradayResponse.IntradayDataPoint}) implementieren es, sodass Indikatoren
 * für beide Zeitreihen mit derselben Logik berechnet werden können.
 *
 * <p><b>Konvention:</b>
 * <ul>
 *   <li>Bei {@link DailyPrice} liefert {@code getCloseValue()} den <em>adjustierten</em>
 *       Schlusskurs ({@code adjClose}), da dieser für Langzeit-Indikatoren korrekt ist.</li>
 *   <li>Bei Intraday-Daten liefert {@code getCloseValue()} den Schlusskurs des Buckets
 *       ({@code close}).</li>
 * </ul>
 */
public interface PricePoint
{
    /**
     * Gibt den für Indikatorberechnungen relevanten Schlusskurs zurück.
     *
     * @return Schlusskurs als {@code double}
     */
    double getCloseValue();
}
