package com.straube.jones.dataprovider.stocks;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.straube.jones.cmd.db.DBConnection;

public class StocksLoader
{
    private static final Map<String, List<StockItem>> stockItems = new HashMap<>();

    private static final long REFRESH_CYCLE = 1_000 * 60 * 60 * 6; // every 6 hours
    private static long nextRefresh = (System.currentTimeMillis() / REFRESH_CYCLE) + 1;

    private StocksLoader()
    {}


    public static Map<String, List<StockItem>> load()
    {
        if (stockItems.size() > 0 && (System.currentTimeMillis() / REFRESH_CYCLE < nextRefresh))
        { return stockItems; }
        nextRefresh++ ;

        String query = "SELECT * FROM tOnVista2 ORDER BY cName asc";

        try
        {
            Connection connection = DBConnection.getStocksConnection();
            try (PreparedStatement ps = connection.prepareStatement(query))
            {
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
                stockItems.put("stockItems", stockList);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return stockItems;
    }
}
