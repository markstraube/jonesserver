package com.straube.jones.dataprovider.stocks;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.straube.jones.db.DBConnection;

public class StocksLoader
{
    private static final Map<String, List<StockItem>> stockItems = new HashMap<>();

    private static final long REFRESH_CYCLE = 1_000 * 60 * 60 * 6; // every 6 hours
    private static long nextRefresh = (System.currentTimeMillis() / REFRESH_CYCLE) + 1;

    private StocksLoader()
    {}


    public static Map<String, List<StockItem>> load()
    {
        return load(null);
    }


    public static Map<String, List<StockItem>> load(List<String> isins)
    {
        if (stockItems.size() > 0 && (System.currentTimeMillis() / REFRESH_CYCLE < nextRefresh))
        {
            if (isins == null || isins.isEmpty())
            {
                return stockItems;
            }
            else
            {
                // Filter by ISINs
                List<StockItem> filteredList = stockItems.get("stockItems")
                                                         .stream()
                                                         .filter(item -> isins.contains(item.getISIN()))
                                                         .collect(java.util.stream.Collectors.toList());
                Map<String, List<StockItem>> result = new HashMap<>();
                result.put("stockItems", filteredList);
                return result;
            }
        }
        nextRefresh++ ;

        String query = "SELECT * FROM tOnVista";
        if (isins != null && !isins.isEmpty())
        {
            String placeholders = String.join(",", java.util.Collections.nCopies(isins.size(), "?"));
            query += " WHERE cIsin IN (" + placeholders + ")";
        }
        query += " ORDER BY cName asc";

        try (Connection connection = DBConnection.getStocksConnection())
        {
            try (PreparedStatement ps = connection.prepareStatement(query))
            {
                if (isins != null && !isins.isEmpty())
                {
                    for (int i = 0; i < isins.size(); i++ )
                    {
                        ps.setString(i + 1, isins.get(i));
                    }
                }
                ps.execute();
                ResultSet rs = ps.getResultSet();
                long id = 1;
                List<StockItem> stockList = new ArrayList<>();
                while (rs.next())
                {
                    StockItem item = new StockItem(rs, id);
                    stockList.add(item);
                    id++ ;
                }
                if (isins == null || isins.isEmpty())
                {
                    stockItems.put("stockItems", stockList);
                }
                Map<String, List<StockItem>> result = new HashMap<>();
                result.put("stockItems", stockList);
                return result;
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        Map<String, List<StockItem>> emptyResult = new HashMap<>();
        emptyResult.put("stockItems", new ArrayList<>());
        return emptyResult;
    }
}
