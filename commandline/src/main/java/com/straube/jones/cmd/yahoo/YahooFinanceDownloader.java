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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class YahooFinanceDownloader
{
    private static final Logger LOGGER = Logger.getLogger(YahooFinanceDownloader.class.getName());

    private File rootFolder;

    public YahooFinanceDownloader(String rootFolder)
    {
        this.rootFolder = new File(rootFolder, "yahoo");
        this.rootFolder.mkdirs();
    }

    public enum OutputFormat
    {
        CSV, JSON
    }

    /**
     * Lädt historische Kursdaten von Yahoo Finance über die Chart-API (benötigt keine Authentifizierung)
     * @param symbol Aktiensymbol (z.B. "MTB")
     * @param startDate Startdatum
     * @param endDate Enddatum
     * @param format Output-Format (CSV oder JSON)
     * @return Daten als String im gewählten Format
     */
    public static String downloadHistoricalData(String symbol,
                                                LocalDate startDate,
                                                LocalDate endDate,
                                                OutputFormat format)
        throws IOException
    {
        String jsonString = downloadRawJson(symbol, startDate, endDate);

        if (format == OutputFormat.JSON)
        {
            return convertToStructuredJson(jsonString);
        }
        else
        {
            return convertJsonToCsv(jsonString);
        }
    }


    /**
     * Lädt historische Kursdaten von Yahoo Finance über die Chart-API (benötigt keine Authentifizierung)
     * Standard-Format ist CSV für Rückwärtskompatibilität
     * @param symbol Aktiensymbol (z.B. "MTB")
     * @param startDate Startdatum
     * @param endDate Enddatum
     * @return CSV-Daten als String
     */
    public static String downloadHistoricalData(String symbol, LocalDate startDate, LocalDate endDate)
        throws IOException
    {
        return downloadHistoricalData(symbol, startDate, endDate, OutputFormat.CSV);
    }


    /**
     * Lädt die rohen JSON-Daten von Yahoo Finance
     */
    private static String downloadRawJson(String symbol, LocalDate startDate, LocalDate endDate)
        throws IOException
    {
        // Konvertiere LocalDate zu Unix Timestamps
        long period1 = startDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
        long period2 = endDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();

        // Verwende die Chart-API, die keine Authentifizierung benötigt
        String urlString = String.format("https://query2.finance.yahoo.com/v8/finance/chart/%s?period1=%d&period2=%d&interval=1d&events=history",
                                         URLEncoder.encode(symbol, StandardCharsets.UTF_8),
                                         period1,
                                         period2);

        LOGGER.log(Level.FINE, () -> "Download URL: " + urlString);

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
            { throw new IOException("HTTP Error Code: " + responseCode); }

            // Lese JSON Response
            StringBuilder jsonResponse = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(),
                                                                                  StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    jsonResponse.append(line);
                }
            }

            // Gebe rohen JSON-String zurück
            return jsonResponse.toString();
        }
        finally
        {
            conn.disconnect();
        }
    }


    /**
     * Konvertiert die JSON-Response von Yahoo Finance zu CSV-Format
     */
    private static String convertJsonToCsv(String jsonString)
        throws IOException
    {
        JSONObject json = new JSONObject(jsonString);
        JSONObject chart = json.getJSONObject("chart");
        JSONArray results = chart.getJSONArray("result");

        if (results.length() == 0)
        { throw new IOException("Keine Daten in der Response gefunden"); }

        JSONObject result = results.getJSONObject(0);
        JSONArray timestamps = result.getJSONArray("timestamp");
        JSONObject indicators = result.getJSONObject("indicators");
        JSONArray quotes = indicators.getJSONArray("quote");

        if (quotes.length() == 0)
        { throw new IOException("Keine Quote-Daten gefunden"); }

        JSONObject quote = quotes.getJSONObject(0);
        JSONArray opens = quote.getJSONArray("open");
        JSONArray highs = quote.getJSONArray("high");
        JSONArray lows = quote.getJSONArray("low");
        JSONArray closes = quote.getJSONArray("close");
        JSONArray volumes = quote.getJSONArray("volume");

        // Hole Adjusted Close falls vorhanden
        JSONArray adjCloses = null;
        if (indicators.has("adjclose"))
        {
            JSONArray adjcloseArray = indicators.getJSONArray("adjclose");
            if (adjcloseArray.length() > 0)
            {
                adjCloses = adjcloseArray.getJSONObject(0).getJSONArray("adjclose");
            }
        }

        // Baue CSV
        StringBuilder csv = new StringBuilder();
        csv.append("Date,Open,High,Low,Close,Adj Close,Volume\n");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (int i = 0; i < timestamps.length(); i++ )
        {
            long timestamp = timestamps.getLong(i);
            LocalDate date = Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault()).toLocalDate();

            Object open = opens.isNull(i) ? "null" : opens.get(i);
            Object high = highs.isNull(i) ? "null" : highs.get(i);
            Object low = lows.isNull(i) ? "null" : lows.get(i);
            Object close = closes.isNull(i) ? "null" : closes.get(i);
            Object volume = volumes.isNull(i) ? "null" : volumes.get(i);
            Object adjClose = (adjCloses != null && !adjCloses.isNull(i)) ? adjCloses.get(i) : close;

            csv.append(String.format("%s,%s,%s,%s,%s,%s,%s\n",
                                     date.format(formatter),
                                     open,
                                     high,
                                     low,
                                     close,
                                     adjClose,
                                     volume));
        }

        return csv.toString();
    }


    /**
     * Konvertiert die JSON-Response von Yahoo Finance zu strukturiertem JSON-Format
     * mit Meta-Informationen, Spaltennamen und Daten-Records
     */
    private static String convertToStructuredJson(String jsonString)
        throws IOException
    {
        JSONObject json = new JSONObject(jsonString);
        JSONObject chart = json.getJSONObject("chart");
        JSONArray results = chart.getJSONArray("result");

        if (results.length() == 0)
        { throw new IOException("Keine Daten in der Response gefunden"); }

        JSONObject result = results.getJSONObject(0);

        // Extrahiere Meta-Informationen
        JSONObject meta = result.getJSONObject("meta");

        // Extrahiere Daten
        JSONArray timestamps = result.getJSONArray("timestamp");
        JSONObject indicators = result.getJSONObject("indicators");
        JSONArray quotes = indicators.getJSONArray("quote");

        if (quotes.length() == 0)
        { throw new IOException("Keine Quote-Daten gefunden"); }

        JSONObject quote = quotes.getJSONObject(0);
        JSONArray opens = quote.getJSONArray("open");
        JSONArray highs = quote.getJSONArray("high");
        JSONArray lows = quote.getJSONArray("low");
        JSONArray closes = quote.getJSONArray("close");
        JSONArray volumes = quote.getJSONArray("volume");

        // Hole Adjusted Close falls vorhanden
        JSONArray adjCloses = null;
        if (indicators.has("adjclose"))
        {
            JSONArray adjcloseArray = indicators.getJSONArray("adjclose");
            if (adjcloseArray.length() > 0)
            {
                adjCloses = adjcloseArray.getJSONObject(0).getJSONArray("adjclose");
            }
        }

        // Baue strukturiertes JSON mit garantierter Reihenfolge
        StringBuilder jsonOutput = new StringBuilder();
        jsonOutput.append("{\n");

        // 1. Meta-Informationen (als erstes)
        jsonOutput.append("  \"meta\": ");
        jsonOutput.append(meta.toString(2).replace("\n", "\n  "));
        jsonOutput.append(",\n");

        // 2. Spaltennamen (als zweites)
        jsonOutput.append("  \"columns\": [\n");
        jsonOutput.append("    \"date\",\n");
        jsonOutput.append("    \"open\",\n");
        jsonOutput.append("    \"high\",\n");
        jsonOutput.append("    \"low\",\n");
        jsonOutput.append("    \"close\",\n");
        jsonOutput.append("    \"adjClose\",\n");
        jsonOutput.append("    \"volume\"\n");
        jsonOutput.append("  ],\n");

        // 3. Daten-Records (als letztes)
        jsonOutput.append("  \"data\": [\n");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (int i = 0; i < timestamps.length(); i++ )
        {
            long timestamp = timestamps.getLong(i);
            LocalDate date = Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault()).toLocalDate();

            // Baue Record manuell
            if (i > 0)
            {
                jsonOutput.append(",\n");
            }
            jsonOutput.append("    {\n");
            jsonOutput.append("      \"date\": \"").append(date.format(formatter)).append("\",\n");
            jsonOutput.append("      \"open\": ").append(formatJsonValue(opens, i)).append(",\n");
            jsonOutput.append("      \"high\": ").append(formatJsonValue(highs, i)).append(",\n");
            jsonOutput.append("      \"low\": ").append(formatJsonValue(lows, i)).append(",\n");
            jsonOutput.append("      \"close\": ").append(formatJsonValue(closes, i)).append(",\n");

            // Adjusted Close
            if (adjCloses != null && !adjCloses.isNull(i))
            {
                jsonOutput.append("      \"adjClose\": ").append(adjCloses.get(i)).append(",\n");
            }
            else if (!closes.isNull(i))
            {
                jsonOutput.append("      \"adjClose\": ").append(closes.get(i)).append(",\n");
            }
            else
            {
                jsonOutput.append("      \"adjClose\": null,\n");
            }

            jsonOutput.append("      \"volume\": ").append(formatJsonValue(volumes, i)).append("\n");
            jsonOutput.append("    }");
        }

        jsonOutput.append("\n  ]\n");
        jsonOutput.append("}");

        return jsonOutput.toString();
    }


    /**
     * Hilfsmethode zum Formatieren von JSON-Werten (null oder Zahl)
     */
    private static String formatJsonValue(JSONArray array, int index)
    {
        if (array.isNull(index))
        { return "null"; }
        return String.valueOf(array.get(index));
    }


    /**
     * Lädt historische Kursdaten für alle Aktien aus StocksCode.json
     * @param format Output-Format (CSV oder JSON)
     * @param daysBack Anzahl Tage zurück (z.B. 365 für 1 Jahr)
     * @return true wenn erfolgreich
     */
    public boolean fetchHistoricalData(OutputFormat format,
                                       int daysBack,
                                       boolean skipExisting,
                                       boolean reportMissing)
    {
        AtomicInteger codeIndex = new AtomicInteger(0);
        try
        {
            LOGGER.log(Level.INFO, "Loading StocksCode.json from classpath");

            // Lade StocksCode.json aus dem Classpath
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("StocksCode.json");
            if (inputStream == null)
            {
                LOGGER.log(Level.SEVERE, "StocksCode.json not found in classpath");
                return false;
            }

            String jsonString = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            JSONObject stocksData = new JSONObject(jsonString);

            LOGGER.log(Level.INFO, () -> stocksData.length() + " stocks loaded");

            // Erstelle historic Unterordner
            File historicFolder = new File(rootFolder, "historic");
            historicFolder.mkdirs();

            // Berechne Zeitraum
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(daysBack);

            int count = 0;
            int errors = 0;

            // Iteriere über alle ISIN-Einträge
            for (String isin : stocksData.keySet())
            {
                JSONObject stockInfo = stocksData.getJSONObject(isin);
                String[] codes = stockInfo.getString("code").split(",");
                if (codeIndex.get() >= codes.length)
                {
                    LOGGER.log(Level.WARNING, () -> "No code available for ISIN: " + isin);
                    codeIndex.set(0);
                    continue;
                }
                String code = codes[codeIndex.get()].trim();

                // Erstelle Dateinamen: <ISIN>_<code>.<format>
                String extension = format == OutputFormat.CSV ? "csv" : "json";
                File outputFile = new File(historicFolder, isin + "_" + code + "." + extension);
                if (outputFile.exists() && skipExisting)
                {
                    if (!reportMissing)
                    {
                        LOGGER.log(Level.FINER,
                                   () -> "File already exists, skipping: " + outputFile.getAbsolutePath());
                    }
                    codeIndex.set(0);
                    continue;
                }
                else if (reportMissing)
                {
                    System.out.println("nothing found for code: " + code + stockInfo.toString());
                    codeIndex.set(0);
                    continue;
                }
                try
                {
                    LOGGER.log(Level.FINER, () -> "Fetching data for " + code + " (ISIN: " + isin + ")");

                    // Lade die Daten
                    String data = downloadHistoricalData(code, startDate, endDate, format);
                    codeIndex.set(0);

                    // Speichere in Datei
                    Files.write(outputFile.toPath(), data.getBytes(StandardCharsets.UTF_8));

                    count++ ;
                    if (count % 10 == 0)
                    {
                        final int currentCount = count;
                        final int totalStocks = stocksData.length();
                        LOGGER.log(Level.FINER,
                                   () -> currentCount + " of " + totalStocks + " stocks fetched");
                    }
                }
                catch (Exception e)
                {
                    errors++ ;
                    codeIndex.incrementAndGet();
                    LOGGER.log(Level.WARNING,
                               () -> "Failed fetching data for " + code
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
                                       + historicFolder.getAbsolutePath()
                                       + " ("
                                       + finalErrors
                                       + " errors)");

            return true;
        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, "Error fetching historical data", e);
            return false;
        }
    }

    /**
     * Beispiel-Verwendung
     */
    public static void main(String[] args)
    {
        String rootFolder = args.length > 0 ? args[0] : "./data";
        YahooFinanceDownloader downloader = new YahooFinanceDownloader(rootFolder);

        // Lade Daten für die letzten 365 Tage im CSV-Format
        boolean success = downloader.fetchHistoricalData(OutputFormat.JSON, 365, true, false);

        System.exit(success ? 0 : 1);
    }
}
