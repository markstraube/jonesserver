package com.straube.jones.cmd.onVista;


import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.straube.jones.cmd.currencies.CurrencyDB;
import com.straube.jones.cmd.db.DBConnection;
import com.straube.jones.cmd.db.StocksModel;

public class StocksParser
{
    public static void insertFinderJsonToStocksTable(Path path)
        throws SQLException
    {
        System.out.println(String.format("... processing folder %s", path.toFile().getName()));
        Instant timeStamp = Instant.parse(path.getFileName() + "T06:00:00.00Z");
        ZonedDateTime zonedDate = timeStamp.atZone(ZoneId.systemDefault());
        DayOfWeek dayOfWeek = zonedDate.getDayOfWeek();
        if (dayOfWeek.equals(DayOfWeek.SUNDAY) || dayOfWeek.equals(DayOfWeek.SATURDAY))
        {
            System.out.println(" -> skipping due to weekend");
            return;
        }
        Long timeStampLong = timeStamp.toEpochMilli();
        Timestamp sqlTimestamp = Timestamp.valueOf(zonedDate.toLocalDateTime());

        StringBuilder stockColumns = new StringBuilder();
        StringBuilder stockValues = new StringBuilder();
        StocksModel.getModel().forEach(col -> {
            stockColumns.append(col.colName).append(",");
            stockValues.append("?,");
        });
        stockColumns.trimToSize();
        stockColumns.deleteCharAt(stockColumns.length() - 1).trimToSize();
        stockValues.trimToSize();
        stockValues.deleteCharAt(stockValues.length() - 1).trimToSize();

        AtomicLong counter = new AtomicLong();
        DirectoryStream.Filter<Path> filter = file -> {
            final String fileName = file.toFile().getName();
            return (fileName.endsWith(".json"));
        };
        try (final Connection connection = DBConnection.getStocksConnection();
                        final PreparedStatement psInsert = connection.prepareStatement("INSERT INTO tStocks (" + stockColumns.toString() + ") VALUES(" + stockValues + ")");
                        final PreparedStatement psDelete = connection.prepareStatement("DELETE FROM tStocks WHERE cDateLong=?"))
        {
            psDelete.setLong(1, timeStampLong);
            psDelete.execute();
            connection.commit();
            try (DirectoryStream<Path> folder = Files.newDirectoryStream(path, filter))
            {
                folder.forEach(entry -> {
                    try
                    {
                        String jsonString = FileUtils.readFileToString(entry.toFile(), "UTF-8");
                        JSONObject jo = new JSONObject(jsonString);
                        JSONArray ar = jo.getJSONArray("values");
                        ar.forEach(e -> {
                            try
                            {
                                if (e instanceof JSONArray)
                                {
                                    List<Object> list = ((JSONArray)e).toList();
                                    setParams(psInsert, list, timeStampLong, sqlTimestamp);
                                    psInsert.addBatch();
                                    counter.incrementAndGet();
                                }
                            }
                            catch (Exception e2)
                            {
                                System.out.println(String.format("... ERR: %s -> dumping object %s", e2.getMessage(), e.toString()));
                                
                            }
                        });
                        psInsert.executeBatch();
                        connection.commit();
                    }
                    catch (Exception e1)
                    {
                        e1.printStackTrace();
                    }
                });
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        System.out.println(String.format(" -> %d Records inserted.", counter.get()));
    }


    public static PreparedStatement setParams(PreparedStatement stmnt, List<Object> params, long timeStamp, Timestamp sqlTimestamp)
    {
        try
        {
            String primaryKey = UUID.randomUUID().toString();
            stmnt.setString(1, primaryKey);
            stmnt.setString(2, String.valueOf(params.get(0))); // ISIN

            double d = Double.parseDouble(String.valueOf(params.get(7))); // Quote
            String c = String.valueOf(params.get(10));

            stmnt.setDouble(3, CurrencyDB.getAsEuro(c, d, timeStamp));
            stmnt.setString(4, "EUR"); // Currency
            stmnt.setLong(5, timeStamp);
            stmnt.setTimestamp(6, sqlTimestamp);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return stmnt;
    }
}
