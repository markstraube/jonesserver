package com.straube.jones.cmd.misc.yahoo;


import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

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
        File rootFolder = new File("./data", "yahoo");
        // Lade YahooCodes.json aus dem yahoo Ordner
        File yahooCodesFile = new File(rootFolder, "YahooCodes.json");
        if (!yahooCodesFile.exists())
        {
            System.err.println("YahooCodes.json not found at: " + yahooCodesFile.getAbsolutePath());
            return;
        }
        String jsonString = new String(Files.readAllBytes(yahooCodesFile.toPath()), StandardCharsets.UTF_8);
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
