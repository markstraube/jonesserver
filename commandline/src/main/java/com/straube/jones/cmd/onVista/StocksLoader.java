package com.straube.jones.cmd.onVista;


import java.awt.image.BufferedImage;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import com.straube.jones.cmd.db.DBConnection;
import com.straube.jones.cmd.db.StockDataPoint;

public class StocksLoader
{
    // Private constructor to prevent instantiation
    private StocksLoader()
    {
        throw new UnsupportedOperationException("Utility class");
    }


    /**
     * Liest die Tabelle tStocks aus und liefert ein Map<String, List<StockDataPoint>>.
     * 
     * @param start Zeitstempel (Millis), default = now - 7 Tage
     * @param end Zeitstempel (Millis), default = now
     * @param isins Liste von ISINs, kann leer sein (liefert dann alle ISINs im Zeitbereich)
     * @return Map von ISIN auf Liste von StockDataPoint
     */
    public static Map<String, List<StockDataPoint>> loadFromDB(long start, long end, String[] isins)
    {
        Map<String, List<StockDataPoint>> result = new HashMap<>();
        try (Connection conn = DBConnection.getStocksConnection())
        {
            StringBuilder sql = new StringBuilder("SELECT cIsin, cDateLong, cLast FROM tStocks WHERE cDateLong >= ? AND cDateLong <= ?");
            if (isins != null && isins.length > 0)
            {
                sql.append(" AND cIsin IN (");
                for (int i = 0; i < isins.length; i++ )
                {
                    sql.append("?");
                    if (i < isins.length - 1)
                        sql.append(",");
                }
                sql.append(")");
            }
            sql.append(" ORDER BY cIsin, cDateLong");

            try (PreparedStatement ps = conn.prepareStatement(sql.toString()))
            {
                ps.setLong(1, start);
                ps.setLong(2, end);
                int idx = 3;
                if (isins != null && isins.length > 0)
                {
                    for (String isin : isins)
                    {
                        ps.setString(idx++ , isin);
                    }
                }
                try (ResultSet rs = ps.executeQuery())
                {
                    while (rs.next())
                    {
                        String isin = rs.getString("cIsin");
                        long date = rs.getLong("cDateLong");
                        LocalDate localDate = Instant.ofEpochMilli(date).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                        double price = rs.getDouble("cLast");
                        StockDataPoint point = new StockDataPoint(isin, localDate, price);
                        result.computeIfAbsent(isin, k -> new ArrayList<>()).add(point);
                    }
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return result;
    }


    /**
     * Erzeugt für alle ISINs im Zeitraum ein Chart und speichert es als PNG im Zielverzeichnis.
     * 
     * @param start Zeitstempel (Millis)
     * @param end Zeitstempel (Millis)
     * @param isins ISIN-Liste (kann leer sein)
     * @param width Bildbreite
     * @param height Bildhöhe
     * @param rootFolder Zielverzeichnis (z.B. HTML_ROOT_FOLDER)
     */
    public static void generateAndSaveCharts(long start, long end, String[] isins, int width, int height, String rootFolder)
    {
        String dir = String.format("%sx%s", width, height);
        File targetDir = new File(rootFolder, dir);
        if (!targetDir.exists())
        {
            targetDir.mkdirs();
        }
        else
        {
            targetDir.delete();
            targetDir.mkdirs();
        }

        Map<String, List<StockDataPoint>> data = loadFromDB(start, end, isins);
        for (Map.Entry<String, List<StockDataPoint>> entry : data.entrySet())
        {
            final String isin = entry.getKey();
            List<StockDataPoint> points = entry.getValue();
            try
            {
                BufferedImage chart = StockChartGenerator.generateChart(points, width, height, isin);
                File imageFile = new File(targetDir, isin + ".png");
                ImageIO.write(chart, "png", imageFile);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }
}
