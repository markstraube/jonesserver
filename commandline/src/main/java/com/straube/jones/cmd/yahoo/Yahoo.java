package com.straube.jones.cmd.yahoo;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import com.straube.jones.cmd.html.HttpTools;

public class Yahoo {

    private static final Logger LOGGER = Logger.getLogger(Yahoo.class.getName());
    private File rootFolder;

    public Yahoo(String rootFolder)
    {
        this.rootFolder = new File(rootFolder, "yahoo");
        this.rootFolder.mkdirs();
    }

    /**
     * Die Methode holt historische Kursdaten von Yahoo Finance
     * 
     * https://de.finance.yahoo.com/quote/${CODE}/history/
     * 
     * lädt aus dem Classpath die Datei StocksCode.json und liest die Kürzel aus dem Feld code (bei mehreren Einträgen nur das erste) 
     * für jedes Kürzel werden die historischen Kursdaten abgeholt und in einer Datei ${CODE}.json im Ordner yahoo/historic gespeichert.
     * 
     * @return
     */
    public boolean fetchHistoricalData()
    {
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
            
            int count = 0;
            // Iteriere über alle ISIN-Einträge
            for (String isin : stocksData.keySet())
            {
                JSONObject stockInfo = stocksData.getJSONObject(isin);
                String code = stockInfo.getString("code");
                
                // Erstelle URL für Yahoo Finance
                String url = "https://de.finance.yahoo.com/quote/" + code + "/history/";
                
                // Erstelle Dateinamen: <ISIN>_<code>.html
                File htmlFile = new File(historicFolder, isin + "_" + code + ".html");
                
                try
                {
                    LOGGER.log(Level.INFO, () -> "Fetching data for " + code + " (ISIN: " + isin + ")");
                    
                    // Lade die HTML-Seite und speichere sie
                    HttpTools.downloadFromWebToFile(url, htmlFile, false);
                    
                    count++;
                    if (count % 10 == 0)
                    {
                        final int currentCount = count;
                        final int totalStocks = stocksData.length();
                        LOGGER.log(Level.INFO, () -> currentCount + " of " + totalStocks + " stocks fetched");
                    }
                }
                catch (Exception e)
                {
                    LOGGER.log(Level.WARNING, () -> "Error fetching data for " + code + " (ISIN: " + isin + "): " + e.getMessage());
                }
            }
            
            final int finalCount = count;
            LOGGER.log(Level.INFO, () -> "Done! " + finalCount + " historical data pages saved to " + historicFolder.getAbsolutePath());
            
            return true;
        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, "Error fetching historical data", e);
            return false;
        }
    }

    public static void main(String[] args)
    {
        String rootFolder = args.length > 0 ? args[0] : "./data";
        Yahoo yahoo = new Yahoo(rootFolder);
        boolean success = yahoo.fetchHistoricalData();
        System.exit(success ? 0 : 1);
    }
}
