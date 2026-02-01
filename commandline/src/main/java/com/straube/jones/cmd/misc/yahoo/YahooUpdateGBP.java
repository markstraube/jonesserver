package com.straube.jones.cmd.misc.yahoo;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import com.straube.jones.cmd.db.DBConnection;

public class YahooUpdateGBP
{
    public static void main(String[] args)
    {
        new YahooUpdateGBP().run();
    }


    public void run()
    {
        String selectIsinSql = "select cId from tYahoo where cCurrency='GBp' and cLast > 100 and Cisin not in (select distinct(cIsin) from tYahoo where cCurrency = 'GBp' and cLast > 100 and cSequence = 9482)";

        String updateSql = "UPDATE tYahoo SET cLast = cLast / 100 WHERE cid = ?";

        try (Connection conn = DBConnection.getStocksConnection();
                        PreparedStatement psSelect = conn.prepareStatement(selectIsinSql);
                        PreparedStatement psUpdate = conn.prepareStatement(updateSql))
        {
            if (conn == null)
            {
                System.err.println("Keine DB Verbindung (StocksDB)");
                return;
            }

            Set<String> ids = new HashSet<>();
            try (ResultSet rs = psSelect.executeQuery())
            {
                while (rs.next())
                {
                    ids.add(rs.getString(1));
                }
            }

            int updated = 0;
            for (String id : ids)
            {
                psUpdate.setString(1, id);
                updated += psUpdate.executeUpdate();
            }

            conn.commit();
            System.out.println("Updated rows: " + updated + " for " + ids.size() + " ISINs");
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }
}
