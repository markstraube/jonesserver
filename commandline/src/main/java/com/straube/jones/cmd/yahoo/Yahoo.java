package com.straube.jones.cmd.yahoo;


import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import com.straube.jones.cmd.db.DBConnection;

public class Yahoo
{

    public static void main(String[] args)
        throws Exception
    {
        List<String> onVistaIsins = new ArrayList<>();
        try (Connection connection = DBConnection.getStocksConnection())
        {
            // ISINs aus Datenbank laden
            try (PreparedStatement ps = connection.prepareStatement("SELECT cIsin FROM tOnVista");
                            ResultSet rs = ps.executeQuery())
            {
                while (rs.next())
                {
                    onVistaIsins.add(rs.getString("cIsin"));
                }
            }
        }
        InputStream inputStream = YahooFinanceDownloader.class.getClassLoader()
                                                              .getResourceAsStream("StocksCode.json");
        if (inputStream == null)
        {
            System.err.println("StocksCode.json not found in classpath");
            return;
        }
        String jsonString = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        JSONObject stocksData = new JSONObject(jsonString);

        System.out.println(stocksData.length() + " stocks loaded");

        // Iteriere über alle ISIN-Einträge
        onVistaIsins.forEach(isin -> {
            if (!stocksData.has(isin))
            {
                System.out.println("missing isin: " + isin);
            }
        });
    }
}
