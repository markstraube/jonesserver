package com.straube.jones.cmd.yahoo;


import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.json.JSONArray;
import org.json.JSONObject;

public class YahooFinanceDownloader
{
    public enum OutputFormat {
        CSV,
        JSON
    }

    /**
     * Lädt historische Kursdaten von Yahoo Finance über die Chart-API (benötigt keine Authentifizierung)
     * @param symbol Aktiensymbol (z.B. "MTB")
     * @param startDate Startdatum
     * @param endDate Enddatum
     * @param format Output-Format (CSV oder JSON)
     * @return Daten als String im gewählten Format
     */
    public static String downloadHistoricalData(String symbol, LocalDate startDate, LocalDate endDate, OutputFormat format)
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
        
        System.out.println("Download URL: " + urlString);

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
                throw new IOException("HTTP Error Code: " + responseCode);
            }

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
        {
            throw new IOException("Keine Daten in der Response gefunden");
        }

        JSONObject result = results.getJSONObject(0);
        JSONArray timestamps = result.getJSONArray("timestamp");
        JSONObject indicators = result.getJSONObject("indicators");
        JSONArray quotes = indicators.getJSONArray("quote");
        
        if (quotes.length() == 0)
        {
            throw new IOException("Keine Quote-Daten gefunden");
        }
        
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
        
        for (int i = 0; i < timestamps.length(); i++)
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
        {
            throw new IOException("Keine Daten in der Response gefunden");
        }

        JSONObject result = results.getJSONObject(0);
        
        // Extrahiere Meta-Informationen
        JSONObject meta = result.getJSONObject("meta");
        
        // Extrahiere Daten
        JSONArray timestamps = result.getJSONArray("timestamp");
        JSONObject indicators = result.getJSONObject("indicators");
        JSONArray quotes = indicators.getJSONArray("quote");
        
        if (quotes.length() == 0)
        {
            throw new IOException("Keine Quote-Daten gefunden");
        }
        
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
        
        for (int i = 0; i < timestamps.length(); i++)
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
        {
            return "null";
        }
        return String.valueOf(array.get(index));
    }


    /**
     * Speichert die Daten in eine CSV-Datei
     */
    public static void saveToFile(String data, String filename)
        throws IOException
    {
        Files.write(Paths.get(filename), data.getBytes(StandardCharsets.UTF_8));
        System.out.println("Daten gespeichert in: " + filename);
    }


    /**
     * Beispiel-Verwendung
     */
    public static void main(String[] args)
    {
        try
        {
            String symbol = "MTB";
            LocalDate startDate = LocalDate.now().minusYears(1);  // 1 Jahr zurück
            LocalDate endDate = LocalDate.now();

            // Option 1: CSV-Format (Standard)
            System.out.println("Lade Kursdaten für " + symbol + " als CSV...");
            String csvData = downloadHistoricalData(symbol, startDate, endDate, OutputFormat.CSV);

            // Speichere CSV in Datei
            String csvFilename = symbol + "_historical_data.csv";
            saveToFile(csvData, csvFilename);

            // Zeige erste Zeilen CSV
            String[] lines = csvData.split("\n");
            System.out.println("\nErste 5 Zeilen (CSV):");
            for (int i = 0; i < Math.min(5, lines.length); i++ )
            {
                System.out.println(lines[i]);
            }

            // Option 2: JSON-Format
            System.out.println("\n\nLade Kursdaten für " + symbol + " als JSON...");
            String jsonData = downloadHistoricalData(symbol, startDate, endDate, OutputFormat.JSON);

            // Speichere JSON in Datei
            String jsonFilename = symbol + "_historical_data.json";
            saveToFile(jsonData, jsonFilename);

            // Zeige Anfang des JSON
            System.out.println("\nErste 500 Zeichen (JSON):");
            System.out.println(jsonData.substring(0, Math.min(500, jsonData.length())) + "...");

        }
        catch (IOException e)
        {
            System.err.println("Fehler beim Laden der Daten: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
