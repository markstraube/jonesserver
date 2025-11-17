package com.straube.jones.dataprovider.stocks;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        Map<String, Double> normalizationValues = (type == 1) ? new HashMap<>() : null;

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

                double value = last;
                if (type == 1)
                {
                    Double base = normalizationValues.get(isin);
                    if (base == null)
                    {
                        if (last == 0.0d)
                        {
                            // Skip rows until we find the first non-zero price to normalize against
                            continue;
                        }
                        normalizationValues.put(isin, last);
                        base = last;
                    }

                    if (base != 0.0d)
                    {
                        value = (last / base - 1.0d) * 100.0d;
                    }
                }

                data.addRow(isin, date, dateLong, value);
            }
        }
        catch (Exception exc)
        {
            exc.printStackTrace();
        }

        return data;
    }
}
