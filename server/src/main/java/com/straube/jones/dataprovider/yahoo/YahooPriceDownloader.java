package com.straube.jones.dataprovider.yahoo;


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.logging.Level;

import com.straube.jones.db.DBConnection;

public class YahooPriceDownloader
{

    public static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(YahooPriceDownloader.class.getName());

    public static void fetchPrices(int daysBack, String rootFolder)
        throws Exception
    {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(daysBack);

        try (Connection conn = DBConnection.getStocksConnection();
                        PreparedStatement psSelect = conn.prepareStatement("SELECT cSymbol, cIsin FROM tSelectedStocks"))
        {
            ResultSet rs = psSelect.executeQuery();
            while (rs.next())
            {
                String symbol = rs.getString("cSymbol");
                String isin = rs.getString("cIsin");

                String rawJson = downloadRawJson(symbol, startDate, endDate);
                File t = new File(rootFolder);
                if (!t.exists())
                {
                    t.mkdirs();
                }
                File f = new File(rootFolder, isin + "_" + symbol + ".json");
                Files.writeString(f.toPath(), rawJson, StandardCharsets.UTF_8);
            }
        }
    }

    public static void fetchPrices(int daysBack, String rootFolder, String symbol, String isin)
        throws Exception
    {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(daysBack);

        String rawJson = downloadRawJson(symbol, startDate, endDate);
        File t = new File(rootFolder);
        if (!t.exists())
        {
            t.mkdirs();
        }
        File f = new File(rootFolder, isin + "_" + symbol + ".json");
        Files.writeString(f.toPath(), rawJson, StandardCharsets.UTF_8);
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


    public static void main(String[] args)
        throws Exception
    {
        String rootFolder = "./data/yahoo/daily";
        int daysBack = 2;
        if (args.length > 0)
        {
            rootFolder = args[0];
        }
        if (args.length > 1)
        {
            String daysBackStr = args[1];
            daysBack = Integer.parseInt(daysBackStr);
        }
        System.out.println("Starting Yahoo Price download to: " + rootFolder + " for the past " + daysBack + " days.");
        YahooPriceDownloader.fetchPrices(daysBack, rootFolder);
        System.out.println("Download finished.");
    }
}
