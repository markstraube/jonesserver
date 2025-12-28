package com.straube.jones.cmd.yahoo;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.straube.jones.cmd.db.DBConnection;

import org.json.JSONArray;
import org.json.JSONObject;

public class SymbolResolver
{
    private static final Logger LOGGER = Logger.getLogger(SymbolResolver.class.getName());
    private static final String YAHOO_SEARCH_URL = "https://query2.finance.yahoo.com/v1/finance/search?q=%s&newsCount=0&listsCount=0&enableFuzzyQuery=false";

    private SymbolResolver()
    {}


    public static List<String> getCodeForISIN(String isin)
        throws IOException
    {
        List<String> symbols = new ArrayList<>();
        JSONObject json = queryYahooSearchISIN(isin);

        if (json != null && json.has("quotes"))
        {
            JSONArray quotes = json.getJSONArray("quotes");
            for (int i = 0; i < quotes.length(); i++ )
            {
                JSONObject quote = quotes.getJSONObject(i);
                if (quote.has("symbol"))
                {
                    symbols.add(quote.getString("symbol"));
                }
            }
        }

        return symbols;
    }


    /**
    * Fragt Yahoo Search API für eine ISIN ab
    */
    private static JSONObject queryYahooSearchISIN(String isin)
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

    public static void updateOnVista()
    {
        LOGGER.info("Starting update of tOnVista symbols...");
        try (Connection conn = DBConnection.getStocksConnection())
        {
            String query = "SELECT cIsin FROM tOnVista WHERE cSymbol IS NULL OR cSymbol = ''";
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery())
            {
                int count = 0;
                while (rs.next())
                {
                    String isin = rs.getString("cIsin");
                    try
                    {
                        List<String> symbols = getCodeForISIN(isin);
                        if (!symbols.isEmpty())
                        {
                            String symbol = symbols.get(0);
                            updateSymbol(conn, isin, symbol);
                            count++;
                            LOGGER.info("Updated " + isin + " -> " + symbol);
                        }
                        else
                        {
                             LOGGER.info("No symbol found for " + isin);
                        }
                    }
                    catch (IOException e)
                    {
                        LOGGER.log(Level.WARNING, "Failed to resolve symbol for ISIN: " + isin, e);
                    }
                }
                conn.commit();
                LOGGER.info("Finished update. Updated " + count + " symbols.");
            }
        }
        catch (SQLException e)
        {
            LOGGER.log(Level.SEVERE, "Database error during updateOnVista", e);
        }
    }

    private static void updateSymbol(Connection conn, String isin, String symbol) throws SQLException
    {
        String update = "UPDATE tOnVista SET cSymbol = ? WHERE cIsin = ?";
        try (PreparedStatement stmt = conn.prepareStatement(update))
        {
            stmt.setString(1, symbol);
            stmt.setString(2, isin);
            stmt.executeUpdate();
        }
    }

    public static void main(String[] args)
    {
        updateOnVista();
    }   
}
