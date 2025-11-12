package com.straube.jones.cmd.yahoo;


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class YahooFundamentals
{
    private static final Logger LOGGER = Logger.getLogger(YahooFundamentals.class.getName());

    private File rootFolder;

    public YahooFundamentals(String rootFolder)
    {
        this.rootFolder = new File(rootFolder, "yahoo");
        this.rootFolder.mkdirs();
    }


    /**
     * Lädt Fundamental-Daten für ein Symbol von Yahoo Finance
     * Verwendet die gleiche Chart-API wie für historische Daten, die keine Authentifizierung benötigt
     * @param symbol Aktiensymbol (z.B. "AAPL")
     * @return JSONObject mit den Daten
     */
    public static JSONObject downloadFundamentalData(String symbol)
        throws IOException
    {
        // Verwende die Chart-API, die keine Authentifizierung benötigt
        // Diese API gibt auch Meta-Informationen zurück
        String urlString = String.format("https://query2.finance.yahoo.com/v8/finance/chart/%s",
                                         URLEncoder.encode(symbol, StandardCharsets.UTF_8));

        LOGGER.log(Level.FINE, () -> "Fundamental Data URL: " + urlString);

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();

        try
        {
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent",
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200)
            {
                String errorMsg;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(),
                                                                                      StandardCharsets.UTF_8)))
                {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null)
                    {
                        response.append(line);
                    }
                    errorMsg = response.toString();
                }
                catch (Exception e)
                {
                    errorMsg = "No error details available";
                }
                throw new IOException("HTTP Error Code: " + responseCode + " - Details: " + errorMsg);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(),
                                                                                  StandardCharsets.UTF_8)))
            {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null)
                {
                    response.append(line);
                }
                return new JSONObject(response.toString());
            }
        }
        finally
        {
            conn.disconnect();
        }
    }


    /**
     * Extrahiert wichtige Fundamental-Kennzahlen aus den Yahoo Finance Chart-API Daten
     * @param chartData JSON-Objekt von downloadFundamentalData() (Chart-API Response)
     * @return Strukturiertes JSONObject mit den wichtigsten Kennzahlen
     */
    public static JSONObject extractKeyMetrics(JSONObject chartData)
    {
        JSONObject metrics = new JSONObject();

        try
        {
            JSONObject chart = chartData.getJSONObject("chart");
            JSONArray results = chart.getJSONArray("result");
            
            if (results.length() == 0)
            {
                LOGGER.log(Level.WARNING, "No results in chart data");
                return metrics;
            }
            
            JSONObject result = results.getJSONObject(0);
            
            // Die Chart-API gibt Meta-Informationen zurück
            if (result.has("meta"))
            {
                JSONObject meta = result.getJSONObject("meta");
                
                // Symbol und Exchange
                if (meta.has("symbol"))
                {
                    metrics.put("symbol", meta.getString("symbol"));
                }
                if (meta.has("exchangeName"))
                {
                    metrics.put("exchangeName", meta.getString("exchangeName"));
                }
                if (meta.has("currency"))
                {
                    metrics.put("currency", meta.getString("currency"));
                }
                
                // Preis-Daten
                if (meta.has("regularMarketPrice"))
                {
                    metrics.put("currentPrice", meta.getDouble("regularMarketPrice"));
                }
                if (meta.has("previousClose"))
                {
                    metrics.put("previousClose", meta.getDouble("previousClose"));
                }
                if (meta.has("regularMarketDayHigh"))
                {
                    metrics.put("dayHigh", meta.getDouble("regularMarketDayHigh"));
                }
                if (meta.has("regularMarketDayLow"))
                {
                    metrics.put("dayLow", meta.getDouble("regularMarketDayLow"));
                }
                if (meta.has("regularMarketVolume"))
                {
                    metrics.put("volume", meta.getLong("regularMarketVolume"));
                }
                
                // 52-Wochen Bereich
                if (meta.has("fiftyTwoWeekHigh"))
                {
                    metrics.put("fiftyTwoWeekHigh", meta.getDouble("fiftyTwoWeekHigh"));
                }
                if (meta.has("fiftyTwoWeekLow"))
                {
                    metrics.put("fiftyTwoWeekLow", meta.getDouble("fiftyTwoWeekLow"));
                }
                
                // Weitere Meta-Informationen
                if (meta.has("longName"))
                {
                    metrics.put("longName", meta.getString("longName"));
                }
                if (meta.has("shortName"))
                {
                    metrics.put("shortName", meta.getString("shortName"));
                }
                if (meta.has("marketCap"))
                {
                    metrics.put("marketCap", meta.getLong("marketCap"));
                }
                
                // Zeitzone
                if (meta.has("timezone"))
                {
                    metrics.put("timezone", meta.getString("timezone"));
                }
                if (meta.has("exchangeTimezoneName"))
                {
                    metrics.put("exchangeTimezoneName", meta.getString("exchangeTimezoneName"));
                }
            }

        }
        catch (Exception e)
        {
            LOGGER.log(Level.WARNING, "Error extracting key metrics", e);
        }

        return metrics;
    }


    /**
     * Lädt Fundamental-Daten für alle Aktien aus StocksCode.json
     * 
     *  assetProfile - Firmenprofil, Branche, Mitarbeiter, Website
        summaryProfile - Zusammenfassung
        summaryDetail - Marktkapitalisierung, P/E Ratio, Dividende, Beta
        financialData - Finanzkennzahlen, Gewinnmargen, ROE, Verschuldung
        defaultKeyStatistics - Key Statistics, Shares Outstanding, Float
        calendarEvents - Dividenden-Termine, Earnings-Termine
        recommendationTrend - Analystenbewertungen
        upgradeDowngradeHistory - Upgrade/Downgrade Historie
        earnings - Gewinndaten
        earningsHistory - Gewinn-Historie
        earningsTrend - Gewinn-Prognosen
        incomeStatementHistory - Gewinn- und Verlustrechnung
        balanceSheetHistory - Bilanz
        cashflowStatementHistory - Cashflow
        price - Aktueller Preis und Marktdaten
     * @return true wenn erfolgreich
     */
    public boolean fetchFundamentalData(boolean skipExisting, boolean reportMissing)
    {
        try
        {
            LOGGER.log(Level.INFO, "Loading StocksCode.json from classpath");

            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("StocksCode.json");
            if (inputStream == null)
            {
                LOGGER.log(Level.SEVERE, "StocksCode.json not found in classpath");
                return false;
            }

            String jsonString = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            JSONObject stocksData = new JSONObject(jsonString);

            LOGGER.log(Level.INFO, () -> stocksData.length() + " stocks loaded");

            // Erstelle fundamental Unterordner
            File fundamentalFolder = new File(rootFolder, "fundamental");
            fundamentalFolder.mkdirs();

            int count = 0;
            int errors = 0;

            // Iteriere über alle ISIN-Einträge
            for (String isin : stocksData.keySet())
            {
                JSONObject stockInfo = stocksData.getJSONObject(isin);
                String code = stockInfo.getString("code");

                // Erstelle Dateinamen: <ISIN>_<code>_fundamental.json
                File outputFile = new File(fundamentalFolder, isin + "_" + code + "_fundamental.json");

                if (outputFile.exists() && skipExisting)
                {
                    if (!reportMissing)
                    {
                        LOGGER.log(Level.INFO,
                                   () -> "File already exists, skipping: " + outputFile.getAbsolutePath());
                    }
                    continue;
                }
                else if (reportMissing)
                {
                    LOGGER.log(Level.WARNING,
                               () -> "nothing found for code: " + code + " " + stockInfo.toString());
                    continue;
                }
                try
                {
                    LOGGER.log(Level.INFO,
                               () -> "Fetching fundamental data for " + code + " (ISIN: " + isin + ")");

                    // Lade die Fundamental-Daten (Chart-API mit Meta-Informationen)
                    JSONObject fundamentalData = downloadFundamentalData(code);

                    // Extrahiere wichtige Kennzahlen
                    JSONObject keyMetrics = extractKeyMetrics(fundamentalData);

                    // Erstelle Ausgabe-JSON mit Symbol, ISIN und Kennzahlen
                    JSONObject output = new JSONObject();
                    output.put("symbol", code);
                    output.put("isin", isin);
                    output.put("fetchDate", LocalDate.now().format(DateTimeFormatter.ISO_DATE));
                    output.put("metrics", keyMetrics);
                    output.put("rawData", fundamentalData);

                    // Speichere in Datei
                    Files.write(outputFile.toPath(), output.toString(2).getBytes(StandardCharsets.UTF_8));

                    count++ ;
                    if (count % 10 == 0)
                    {
                        final int currentCount = count;
                        final int totalStocks = stocksData.length();
                        LOGGER.log(Level.INFO, () -> currentCount + " of " + totalStocks + " stocks fetched");
                    }

                    // Rate limiting - Yahoo mag keine zu schnellen Anfragen
                    Thread.sleep(1000);

                }
                catch (Exception e)
                {
                    errors++ ;
                    LOGGER.log(Level.WARNING,
                               () -> "Error fetching fundamental data for " + code
                                               + " (ISIN: "
                                               + isin
                                               + "): "
                                               + e.getMessage());
                }
            }

            final int finalCount = count;
            final int finalErrors = errors;
            LOGGER.log(Level.INFO,
                       () -> "Done! " + finalCount
                                       + " stocks saved to "
                                       + fundamentalFolder.getAbsolutePath()
                                       + " ("
                                       + finalErrors
                                       + " errors)");

            return true;

        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, "Error fetching fundamental data", e);
            return false;
        }
    }


    /**
    * Beispiel-Verwendung
    */
    public static void main(String[] args)
    {
        String rootFolder = args.length > 0 ? args[0] : "./data";
        YahooFundamentals downloader = new YahooFundamentals(rootFolder);

        // Lade auch Fundamental-Daten
        boolean fundamentalSuccess = downloader.fetchFundamentalData(true, false);
        System.exit(fundamentalSuccess ? 0 : 1);
    }
}
