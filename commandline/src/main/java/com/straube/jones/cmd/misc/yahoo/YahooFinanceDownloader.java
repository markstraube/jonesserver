package com.straube.jones.cmd.misc.yahoo;


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import com.straube.jones.cmd.currencies.CurrencyDB;
import com.straube.jones.cmd.db.DBConnection;

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
     * Lädt historische Kursdaten für alle Aktien aus YahooCodes.json
     * @param format Output-Format (CSV oder JSON)
     * @param daysBack Anzahl Tage zurück (z.B. 365 für 1 Jahr)
     * @return true wenn erfolgreich
     */
    public boolean fetchHistoricalData(OutputFormat format,
                                       int daysBack,
                                       boolean skipExisting,
                                       boolean reportMissing)
    {
        try
        {
            LOGGER.log(Level.INFO, "Loading YahooCodes.json");

            // Lade YahooCodes.json aus dem yahoo Ordner
            File yahooCodesFile = new File(rootFolder, "YahooCodes.json");
            if (!yahooCodesFile.exists())
            {
                LOGGER.log(Level.SEVERE, () -> "YahooCodes.json not found at: " + yahooCodesFile.getAbsolutePath());
                return false;
            }
            String jsonString = new String(Files.readAllBytes(yahooCodesFile.toPath()), StandardCharsets.UTF_8);
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
            int skipped = 0;

            // Iteriere über alle ISIN-Einträge
            for (String isin : stocksData.keySet())
            {
                // Überspringe ISINs mit Underscore (das sind zusätzliche Quotes)
                if (isin.contains("_"))
                {
                    skipped++ ;
                    LOGGER.log(Level.FINE, () -> "Skipping additional quote: " + isin);
                    continue;
                }

                JSONObject stockInfo = stocksData.getJSONObject(isin);

                // Hole Symbol aus dem Quote-Objekt
                if (!stockInfo.has("symbol"))
                {
                    LOGGER.log(Level.WARNING, () -> "No symbol found for ISIN: " + isin);
                    continue;
                }

                String code = stockInfo.getString("symbol");

                // Erstelle Dateinamen: <ISIN>_<code>.<format>
                String extension = format == OutputFormat.CSV ? "csv" : "json";
                File outputFile = new File(historicFolder, isin + "_" + code + "." + extension);

                if (outputFile.exists() && skipExisting)
                {
                    if (!reportMissing)
                    {
                        LOGGER.log(Level.FINE,
                                   () -> "File already exists, skipping: " + outputFile.getAbsolutePath());
                    }
                    continue;
                }
                else if (reportMissing)
                {
                    LOGGER.log(Level.INFO,
                               () -> "Nothing found for code: " + code + " " + stockInfo.toString());
                    continue;
                }

                try
                {
                    LOGGER.log(Level.INFO, () -> "Fetching data for " + code + " (ISIN: " + isin + ")");

                    // Lade die Daten
                    String data = downloadHistoricalData(code, startDate, endDate, format);

                    // Speichere in Datei
                    Files.write(outputFile.toPath(), data.getBytes(StandardCharsets.UTF_8));

                    count++ ;
                    if (count % 10 == 0)
                    {
                        final int currentCount = count;
                        final int totalStocks = stocksData.length() - skipped;
                        LOGGER.log(Level.INFO, () -> currentCount + " of " + totalStocks + " stocks fetched");
                    }

                    // Rate limiting
                    Thread.sleep(500);
                }
                catch (Exception e)
                {
                    errors++ ;
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
     * Lädt historische Kursdaten für die übergebenen Codes/Symbole
     * @param codes Liste von Yahoo-Symbolen (z.B. "MTB", "AAPL", "BBNI.JK")
     * @param format Output-Format (CSV oder JSON)
     * @param daysBack Anzahl Tage zurück (z.B. 365 für 1 Jahr)
     * @return Anzahl erfolgreich geladener Datensätze
     */
    public int updateHistoricalData(List<String> codes, OutputFormat format, int daysBack)
    {
        if (codes == null || codes.isEmpty())
        {
            LOGGER.log(Level.WARNING, "No codes provided for update");
            return 0;
        }

        LOGGER.log(Level.INFO, () -> "Updating historical data for " + codes.size() + " codes");

        // Erstelle historic Unterordner
        File historicFolder = new File(rootFolder, "historic");
        historicFolder.mkdirs();

        // Berechne Zeitraum
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(daysBack);

        int count = 0;
        int errors = 0;

        for (String code : codes)
        {
            try
            {
                LOGGER.log(Level.INFO, () -> "Fetching data for " + code);

                // Lade die Daten
                String data = downloadHistoricalData(code, startDate, endDate, format);

                // Erstelle Dateinamen: <code>.<format>
                String extension = format == OutputFormat.CSV ? "csv" : "json";
                File outputFile = new File(historicFolder, code + "." + extension);

                // Speichere in Datei
                Files.write(outputFile.toPath(), data.getBytes(StandardCharsets.UTF_8));

                count++;
                LOGGER.log(Level.INFO, () -> "Saved data for " + code + " to " + outputFile.getAbsolutePath());

                // Rate limiting
                Thread.sleep(500);
            }
            catch (Exception e)
            {
                errors++;
                LOGGER.log(Level.WARNING, () -> "Failed fetching data for " + code + ": " + e.getMessage());
            }
        }

        final int finalCount = count;
        final int finalErrors = errors;
        LOGGER.log(Level.INFO,
                   () -> "Update completed: " + finalCount
                                   + " stocks saved, "
                                   + finalErrors
                                   + " errors");

        return count;
    }

    /**
     * Lädt historische Kursdaten aus ./data/yahoo/historic in die Tabelle tYahoo
     * Die Dateinamen enthalten die ISIN vor dem Underscore (z.B. CZ0005112300_0NZF.L.json)
     * Die Metadaten enthalten die Währung, die mit CurrencyDB.getAsEuro umgerechnet wird
     * Alle historischen Daten je ISIN werden in tYahoo eingetragen
     * @throws SQLException wenn ein Datenbankfehler auftritt
     */
    public void uploadToDB() throws SQLException
    {
        LOGGER.log(Level.INFO, "Starte Upload von Yahoo historischen Daten in tYahoo Tabelle");
        
        // Definiere den historic Ordner
        File historicFolder = new File(rootFolder, "historic");
        if (!historicFolder.exists())
        {
            LOGGER.log(Level.SEVERE, () -> "Historic Ordner existiert nicht: " + historicFolder.getAbsolutePath());
            return;
        }
        
        // Filter für JSON-Dateien
        DirectoryStream.Filter<Path> filter = file -> {
            final String fileName = file.toFile().getName();
            return fileName.endsWith(".json");
        };
        
        Path dirName = historicFolder.toPath();
        AtomicInteger totalInserts = new AtomicInteger(0);
        AtomicInteger totalFiles = new AtomicInteger(0);
        AtomicInteger errorFiles = new AtomicInteger(0);
        
        try (final Connection connection = DBConnection.getStocksConnection())
        {
            try (DirectoryStream<Path> paths = Files.newDirectoryStream(dirName, filter))
            {
                for (Path path : paths)
                {
                    totalFiles.incrementAndGet();
                    try
                    {
                        // Extrahiere ISIN aus dem Dateinamen (vor dem Underscore)
                        String fileName = path.getFileName().toString();
                        int underscorePos = fileName.indexOf('_');
                        if (underscorePos == -1)
                        {
                            LOGGER.log(Level.WARNING, () -> "Dateiname enthält keinen Underscore, überspringe: " + fileName);
                            continue;
                        }
                        String isin = fileName.substring(0, underscorePos);
                        
                        // Lade und parse JSON-Datei
                        String jsonString = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                        JSONObject jsonObject = new JSONObject(jsonString);
                        
                        // Extrahiere Währung aus Metadaten
                        if (!jsonObject.has("meta"))
                        {
                            LOGGER.log(Level.WARNING, () -> "Keine Meta-Daten in Datei: " + fileName);
                            continue;
                        }
                        JSONObject meta = jsonObject.getJSONObject("meta");
                        if (!meta.has("currency"))
                        {
                            LOGGER.log(Level.WARNING, () -> "Keine Währung in Meta-Daten: " + fileName);
                            continue;
                        }
                        String currency = meta.getString("currency");
                        
                        // Extrahiere Daten-Array
                        if (!jsonObject.has("data"))
                        {
                            LOGGER.log(Level.WARNING, () -> "Keine Daten in Datei: " + fileName);
                            continue;
                        }
                        JSONArray dataArray = jsonObject.getJSONArray("data");
                        
                        // Batch-Statements vorbereiten
                        try (final PreparedStatement psDelete = connection.prepareStatement("DELETE FROM tYahoo WHERE cIsin = ? AND cSequence = ?");
                             final PreparedStatement psInsert = connection.prepareStatement(
                                 "INSERT INTO tYahoo (cID, cIsin, cLast, cCurrency, cDateLong, cDate, cSequence) VALUES(?,?,?,?,?,?,?)"))
                        {
                            int recordsInBatch = 0;
                            
                            // Iteriere über alle Datenpunkte
                            for (int i = 0; i < dataArray.length(); i++)
                            {
                                try
                                {
                                    JSONObject dataPoint = dataArray.getJSONObject(i);
                                    
                                    // Extrahiere Datum
                                    if (!dataPoint.has("date"))
                                    {
                                        continue;
                                    }
                                    String dateStr = dataPoint.getString("date");
                                    LocalDate localDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                                    long dateLong = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                                    
                                    // Extrahiere Close-Preis (oder adjClose falls vorhanden)
                                    Double close = null;
                                    if (dataPoint.has("adjClose") && !dataPoint.isNull("adjClose"))
                                    {
                                        close = dataPoint.getDouble("adjClose");
                                    }
                                    else if (dataPoint.has("close") && !dataPoint.isNull("close"))
                                    {
                                        close = dataPoint.getDouble("close");
                                    }
                                    
                                    if (close == null)
                                    {
                                        continue; // Überspringe Datenpunkte ohne Preis
                                    }
                                    
                                    // Konvertiere zu Euro
                                    Double closeInEuro = CurrencyDB.getAsEuro(currency, close, dateLong);
                                    
                                    // Berechne dayOfCentury (cSequence)
                                    int dayOfCentury = getDayOfCentury(dateLong);
                                    
                                    if (dayOfCentury == Integer.MAX_VALUE)
                                    {
                                        LOGGER.log(Level.WARNING, () -> "Ungültiger Timestamp für ISIN " + isin + ", Datum: " + dateStr);
                                        continue;
                                    }
                                    
                                    // Berechne cDate (SQL Timestamp)
                                    java.sql.Timestamp cDate = new java.sql.Timestamp(dateLong);
                                    
                                    // Delete-Statement zum Batch hinzufügen
                                    psDelete.setString(1, isin);
                                    psDelete.setInt(2, dayOfCentury);
                                    psDelete.addBatch();
                                    
                                    // Insert-Statement zum Batch hinzufügen
                                    psInsert.setString(1, java.util.UUID.randomUUID().toString());
                                    psInsert.setString(2, isin);
                                    psInsert.setDouble(3, closeInEuro);
                                    psInsert.setString(4, "EUR"); // Immer EUR nach Konvertierung
                                    psInsert.setLong(5, dateLong);
                                    psInsert.setTimestamp(6, cDate);
                                    psInsert.setInt(7, dayOfCentury);
                                    psInsert.addBatch();
                                    
                                    recordsInBatch++;
                                    totalInserts.incrementAndGet();
                                    
                                    // Batch alle 100 Einträge ausführen
                                    if (recordsInBatch >= 100)
                                    {
                                        psDelete.executeBatch();
                                        psInsert.executeBatch();
                                        connection.commit();
                                        recordsInBatch = 0;
                                    }
                                }
                                catch (Exception e)
                                {
                                    LOGGER.log(Level.WARNING, () -> "Fehler beim Verarbeiten eines Datenpunkts in " + fileName + ": " + e.getMessage());
                                }
                            }
                            
                            // Verbleibende Batch-Einträge ausführen
                            if (recordsInBatch > 0)
                            {
                                psDelete.executeBatch();
                                psInsert.executeBatch();
                                connection.commit();
                            }
                            
                            LOGGER.log(Level.INFO, () -> "Datei " + fileName + " verarbeitet, " + dataArray.length() + " Datenpunkte");
                        }
                    }
                    catch (Exception e)
                    {
                        errorFiles.incrementAndGet();
                        LOGGER.log(Level.WARNING, () -> "Fehler beim Verarbeiten der Datei " + path.getFileName() + ": " + e.getMessage());
                    }
                }
            }
            
            LOGGER.log(Level.INFO, () -> "Upload abgeschlossen: " 
                + totalFiles.get() + " Dateien verarbeitet, "
                + totalInserts.get() + " Records eingefügt, "
                + errorFiles.get() + " Fehler");
        }
        catch (IOException e)
        {
            LOGGER.log(Level.SEVERE, "Fehler beim Lesen des historic Ordners", e);
            throw new SQLException("Fehler beim Lesen des historic Ordners", e);
        }
    }
    
    /**
     * Berechnet die Anzahl der Tage seit dem 1.1.2000
     * @param timestamp Unix-Timestamp in Millisekunden
     * @return Anzahl der Tage seit 1.1.2000, oder Integer.MAX_VALUE bei ungültigem/zu frühem Timestamp
     */
    private static int getDayOfCentury(long timestamp)
    {
        // Referenzdatum: 1.1.2000 00:00:00 UTC
        LocalDate referenceDate = LocalDate.of(2000, 1, 1);
        long referenceDateMillis = referenceDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        
        // Prüfe ob Timestamp ungültig oder vor 1.1.2000
        if (timestamp < referenceDateMillis)
        {
            return Integer.MAX_VALUE;
        }
        
        // Konvertiere Timestamp zu LocalDate
        LocalDate date = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate();
        
        // Berechne Tage zwischen Referenzdatum und gegebenem Datum
        long days = java.time.temporal.ChronoUnit.DAYS.between(referenceDate, date);
        
        // Prüfe auf Overflow
        if (days > Integer.MAX_VALUE)
        {
            return Integer.MAX_VALUE;
        }
        
        return (int) days;
    }

    /**
     * Beispiel-Verwendung
     * @throws SQLException 
     */
    public static void main(String[] args) throws SQLException
    {
        String rootFolder = args.length > 0 ? args[0] : "./data";
        YahooFinanceDownloader downloader = new YahooFinanceDownloader(rootFolder);

        // Lade Daten für die letzten 365 Tage im CSV-Format
        //boolean success = downloader.fetchHistoricalData(OutputFormat.JSON, 365, true, false);
        //List<String> codes = List.of("GWO.TO", "LUN.TO", "PPL.TO");
        //int updatedCount = downloader.updateHistoricalData(codes, OutputFormat.JSON, 365);
        downloader.uploadToDB();
        //System.exit(success ? 0 : 1);
    }
}
