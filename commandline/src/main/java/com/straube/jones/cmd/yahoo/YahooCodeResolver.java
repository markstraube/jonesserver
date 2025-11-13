package com.straube.jones.cmd.yahoo;


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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import com.straube.jones.cmd.db.DBConnection;

public class YahooCodeResolver
{
    /* OUTPUT EXAMPLE:
        {
            "explains": [],
            "count": 1,
            "quotes": [
                {
                "exchange": "JKT",
                "shortname": "Bank Negara Indonesia  (Persero",
                "quoteType": "EQUITY",
                "symbol": "BBNI.JK",
                "index": "quotes",
                "score": 20005,
                "typeDisp": "Equity",
                "longname": "PT Bank Negara Indonesia (Persero) Tbk",
                "exchDisp": "Jakarta",
                "sector": "Financial Services",
                "industry": "Banks—Regional",
                "isYahooFinance": true
                }
            ],
            "news": [],
            "nav": [],
            "lists": [],
            "researchReports": [],
            "screenerFieldResults": [],
            "totalTime": 29,
            "timeTakenForQuotes": 421,
            "timeTakenForNews": 0,
            "timeTakenForAlgowatchlist": 400,
            "timeTakenForPredefinedScreener": 400,
            "timeTakenForCrunchbase": 0,
            "timeTakenForNav": 400,
            "timeTakenForResearchReports": 0,
            "timeTakenForScreenerField": 0,
            "timeTakenForCulturalAssets": 0,
            "timeTakenForSearchLists": 0
        }
     */
    private static final Logger LOGGER = Logger.getLogger(YahooCodeResolver.class.getName());
    private static final String YAHOO_SEARCH_URL = "https://query2.finance.yahoo.com/v1/finance/search?q=%s&newsCount=0&listsCount=0&enableFuzzyQuery=false";

    private File rootFolder;

    public YahooCodeResolver(String rootFolder)
    {
        this.rootFolder = new File(rootFolder, "yahoo");
        this.rootFolder.mkdirs();
    }


    /**
     * Lädt ISINs aus tOnVista, fragt Yahoo Search API ab und speichert Codes
     */
    public void resolve()
        throws Exception
    {
        // 1. Lade ISINs aus tOnVista
        List<String> isins = loadISINsFromDatabase();
        LOGGER.log(Level.INFO, () -> isins.size() + " ISINs loaded from tOnVista");

        // 2. JSON-Objekt für Ergebnisse (ISIN -> Quote-Object)
        JSONObject codesMap = new JSONObject();

        int count = 0;
        int errors = 0;

        // 3. Iteriere über alle ISINs
        for (String isin : isins)
        {
            try
            {
                LOGGER.log(Level.INFO, () -> "Resolving ISIN: " + isin);

                // Yahoo Search API abfragen
                JSONObject searchResult = queryYahooSearch(isin);

                if (searchResult != null && searchResult.has("quotes"))
                {
                    JSONArray quotes = searchResult.getJSONArray("quotes");

                    if (quotes.length() > 0)
                    {
                        // Erstes Quote-Objekt unter ISIN speichern
                        JSONObject firstQuote = quotes.getJSONObject(0);
                        codesMap.put(isin, firstQuote);

                        // Weitere Quote-Objekte unter ISIN_<counter> speichern
                        if (quotes.length() > 1)
                        {
                            final int quoteCount = quotes.length();
                            LOGGER.log(Level.WARNING,
                                       () -> "Multiple quotes found for ISIN " + isin
                                                       + ": "
                                                       + quoteCount
                                                       + " results, saving all");
                            
                            for (int i = 1; i < quotes.length(); i++)
                            {
                                JSONObject additionalQuote = quotes.getJSONObject(i);
                                String key = isin + "_" + i;
                                codesMap.put(key, additionalQuote);
                            }
                        }
                    }
                    else
                    {
                        LOGGER.log(Level.WARNING, () -> "No quotes found for ISIN: " + isin);
                    }
                }

                count++ ;
                if (count % 10 == 0)
                {
                    final int currentCount = count;
                    final int total = isins.size();
                    LOGGER.log(Level.INFO, () -> currentCount + " of " + total + " ISINs processed");
                }

                // Rate limiting - Yahoo mag keine zu schnellen Anfragen
                Thread.sleep(500);
            }
            catch (Exception e)
            {
                errors++ ;
                LOGGER.log(Level.WARNING, () -> "Error resolving ISIN " + isin + ": " + e.getMessage());
            }
        }

        // 4. Speichere Ergebnisse in Codes.json
        File outputFile = new File(rootFolder, "Codes.json");
        Files.write(outputFile.toPath(), codesMap.toString(2).getBytes(StandardCharsets.UTF_8));

        final int finalCount = count;
        final int finalErrors = errors;
        LOGGER.log(Level.INFO,
                   () -> "Completed: " + finalCount
                                   + " ISINs processed, "
                                   + finalErrors
                                   + " errors, saved to "
                                   + outputFile.getAbsolutePath());
    }


    /**
     * Lädt alle ISINs aus der tOnVista Tabelle
     */
    private List<String> loadISINsFromDatabase()
        throws Exception
    {
        List<String> isins = new ArrayList<>();

        try (Connection connection = DBConnection.getStocksConnection())
        {
            try (PreparedStatement ps = connection.prepareStatement("SELECT cIsin FROM tOnVista");
                            ResultSet rs = ps.executeQuery())
            {
                while (rs.next())
                {
                    isins.add(rs.getString("cIsin"));
                }
            }
        }

        return isins;
    }


    /**
     * Fragt Yahoo Search API für eine ISIN ab
     */
    private JSONObject queryYahooSearch(String isin)
        throws IOException
    {
        String urlString = String.format(YAHOO_SEARCH_URL, URLEncoder.encode(isin, StandardCharsets.UTF_8));

        LOGGER.log(Level.FINE, () -> "Yahoo Search URL: " + urlString);

        URL url = new URL(urlString);
        HttpURLConnection conn = null;

        try
        {
            conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent",
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200)
            { throw new IOException("HTTP Error Code: " + responseCode + " for ISIN: " + isin); }

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
            if (conn != null)
            {
                try
                {
                    conn.disconnect();
                }
                catch (Exception e)
                {
                    // Ignore disconnect errors
                }
            }
        }
    }


    public static void main(String[] args)
    {
        try
        {
            String dataPath = args.length > 0 ? args[0] : "../data";

            LOGGER.log(Level.INFO, "Starting Yahoo Code Resolver...");
            LOGGER.log(Level.INFO, () -> "Data path: " + dataPath);

            YahooCodeResolver resolver = new YahooCodeResolver(dataPath);
            resolver.resolve();

            LOGGER.log(Level.INFO, "Yahoo Code Resolver completed successfully");
        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, "Error in Yahoo Code Resolver", e);
            System.exit(1);
        }
    }
}
