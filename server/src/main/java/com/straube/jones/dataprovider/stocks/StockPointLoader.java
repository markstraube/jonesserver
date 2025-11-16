package com.straube.jones.dataprovider.stocks;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import com.straube.jones.db.DBConnection;
import com.straube.jones.dto.TableDataResponse;

public class StockPointLoader
{
    public static final String DATA_FOLDER = System.getProperty("data.root", "/home/mark/Software/data");

    private StockPointLoader()
    {}


    public static TableDataResponse loadRaw(List<String> isins, long start, int type)
    {
        TableDataResponse data = new TableDataResponse();

        if (isins == null || isins.isEmpty())
        {
            return data;
        }

        // SQL-Query vorbereiten mit Platzhaltern für ISINs
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("SELECT cIsin, cLast, cDateLong, cDate FROM tYahoo WHERE cIsin IN (");
        for (int i = 0; i < isins.size(); i++)
        {
            queryBuilder.append("?");
            if (i < isins.size() - 1)
            {   
                queryBuilder.append(",");
            }
        }
        queryBuilder.append(") AND cDateLong >= ? ORDER BY cIsin, cDateLong ASC");

        try (Connection connection = DBConnection.getStocksConnection();
             PreparedStatement ps = connection.prepareStatement(queryBuilder.toString()))
        {
            // Parameter setzen
            int paramIndex = 1;
            for (String isin : isins)
            {
                ps.setString(paramIndex++, isin);
            }
            ps.setLong(paramIndex, start);

            // Query ausführen
            ResultSet rs = ps.executeQuery();
            while (rs.next())
            {
                String isin = rs.getString("cIsin");
                Double last = rs.getDouble("cLast");
                Long dateLong = rs.getLong("cDateLong");
                String date = rs.getString("cDate");

                data.addRow(isin, date, dateLong, last);
            }
        }
        catch (Exception exc)
        {
            exc.printStackTrace();
        }

        return data;
    }
}
