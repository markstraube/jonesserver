package com.straube.jones.dataprovider.yahoo;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import com.straube.jones.db.DBConnection;

public class SymbolResolver
{
    private static final Logger LOGGER = Logger.getLogger(SymbolResolver.class.getName());
    private static final String YAHOO_SEARCH_URL = "https://query2.finance.yahoo.com/v1/finance/search?q=%s&newsCount=0&listsCount=0&enableFuzzyQuery=false";

    private SymbolResolver()
    {}


    /**
    * checks if the supplied code is a ISIN and resolves it to a Yahoo Finance symbol or return the code itself.
    * @param code
    * @return
    */
    public static String resolveIsin(String code)
    {
        if (code != null && code.length() == "US0378331005".length())
        {
            //assuming it is a ISIN and lookup the symbol in tStockCodes Table
            return code;
        }
        String isin = null;
        try (Connection conn = DBConnection.getStocksConnection())
        {
            // 1. Check tSelectedStocks for existing symbol
            // Using cIndex as per text instructions. If DB has cImdex, this will fail and need adjustment.
            String selectSymbolSql = "SELECT cIsin FROM tStockCodes WHERE cSymbol = ?";
            try (PreparedStatement ps = conn.prepareStatement(selectSymbolSql))
            {
                ps.setString(1, code);
                try (ResultSet rs = ps.executeQuery())
                {
                    if (rs.next())
                    {
                        isin = rs.getString("cIsin");
                    }
                }
            }
            if (isin == null)
            {
                // Not found
                throw new RuntimeException("No ISIN found for symbol: " + code);
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error resolving ISIN for symbol: " + code, e);
        }
        return isin;
    }


    /**
     * checks if the supplied code is a ISIN and resolves it to a Yahoo Finance symbol or return the code itself.
     * @param code
     * @return
     */
    public static String resolveCode(String code)
    {
        String symbol = null;
        if (code != null && code.length() == "US0378331005".length())
        {
            //assuming it is a ISIN and lookup the symbol in tStockCodes Table
            String isin = code;

            try (Connection conn = DBConnection.getStocksConnection())
            {
                // 1. Check tSelectedStocks for existing symbol
                // Using cIndex as per text instructions. If DB has cImdex, this will fail and need adjustment.
                String selectSymbolSql = "SELECT cSymbol FROM tStockCodes WHERE cIsin = ? AND cIndex = 1";
                try (PreparedStatement ps = conn.prepareStatement(selectSymbolSql))
                {
                    ps.setString(1, isin);
                    try (ResultSet rs = ps.executeQuery())
                    {
                        if (rs.next())
                        {
                            symbol = rs.getString("cSymbol");
                        }
                    }
                }
                if (symbol == null)
                {
                    // Not found
                    List<String> symbols = getCodeForISIN(isin);
                    if (symbols.isEmpty())
                    { throw new RuntimeException("No symbol found for ISIN: " + isin); }

                    // Insert into tSelectedStocks
                    String insertCodesSql = "INSERT INTO tStockCodes (cSymbol, cIsin, cIndex) VALUES (?, ?, ?)";
                    try (PreparedStatement psInsert = conn.prepareStatement(insertCodesSql))
                    {
                        int idx = 1;
                        for (String s : symbols)
                        {
                            psInsert.setString(1, s);
                            psInsert.setString(2, isin);
                            psInsert.setInt(3, idx++ );
                            try
                            {
                                psInsert.executeUpdate();
                            }
                            catch (SQLException e)
                            {
                                // Ignore if exists, or log
                                System.err.println("Failed to insert symbol " + s + ": " + e.getMessage());
                            }
                        }
                    }
                    symbol = symbols.get(0);
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException("Error resolving symbol for ISIN: " + isin, e);
            }
        }
        else
        {
            //not a ISIN, return the code itself
            symbol = code;
        }
        return symbol;
    }


    private static List<String> getCodeForISIN(String isin)
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
        //TODO: update symbol in tStockCodes Table
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

}
