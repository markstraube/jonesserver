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
    private static Map<String, List<StockDataPoint>> loadFromDB(long start, long end, String[] isins)
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
                    // Map: ISIN -> Map<LocalDate, Double>
                    Map<String, Map<LocalDate, Double>> temp = new HashMap<>();
                    while (rs.next())
                    {
                        String isin = rs.getString("cIsin");
                        long dateLong = rs.getLong("cDateLong");
                        LocalDate localDate = Instant.ofEpochMilli(dateLong).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                        double price = rs.getDouble("cLast");
                        temp.computeIfAbsent(isin, k -> new HashMap<>()).put(localDate, price);
                    }

                    // Für jede ISIN: Zeitreihe pro Tag aufbauen
                    LocalDate startDate = Instant.ofEpochMilli(start).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                    LocalDate endDate = Instant.ofEpochMilli(end).atZone(java.time.ZoneId.systemDefault()).toLocalDate();

                    // Falls Startdatum auf Wochenende fällt, auf nächsten Montag setzen
                    while (startDate.getDayOfWeek().getValue() > 5)
                    { // 6=Samstag, 7=Sonntag
                        startDate = startDate.plusDays(1);
                        if (startDate.isAfter(endDate))
                            break;
                    }

                    List<String> isinList;
                    if (isins != null && isins.length > 0)
                    {
                        isinList = java.util.Arrays.asList(isins);
                    }
                    else
                    {
                        isinList = new ArrayList<>(temp.keySet());
                    }

                    for (String isin : isinList)
                    {
                        Map<LocalDate, Double> dateToPrice = temp.getOrDefault(isin, new HashMap<>());
                        List<StockDataPoint> points = new ArrayList<>();
                        double lastValue = 0.0;
                        boolean firstValueFound = false;
                        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1))
                        {
                            double value;
                            if (dateToPrice.containsKey(d))
                            {
                                lastValue = dateToPrice.get(d);
                                value = lastValue;
                                firstValueFound = true;
                            }
                            else if (!firstValueFound)
                            {
                                value = 0.0;
                            }
                            else
                            {
                                value = lastValue;
                            }
                            points.add(new StockDataPoint(isin, d, value));
                        }
                        result.put(isin, points);
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


    public static void main(String[] args)
    {
        // Beispielaufruf: Erzeuge Charts für alle ISINs im letzten Jahr
        long start = Instant.now().toEpochMilli() - 1000L * 60 * 60 * 24 * 365;
        long end = Instant.now().toEpochMilli(); // jetzt
        String[] isins = {"US0028962076"}; // Alle ISINs

        int width = 64;
        int height = 48;
        String rootFolder = "./charts"; // Zielverzeichnis

        generateAndSaveCharts(start, end, isins, width, height, rootFolder);
    }
}
