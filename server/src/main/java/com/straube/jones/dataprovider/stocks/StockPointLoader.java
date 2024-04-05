package com.straube.jones.dataprovider.stocks;


import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.straube.jones.cmd.ariva.ArivaHistoricData;
import com.straube.jones.cmd.db.DBConnection;

public class StockPointLoader
{
    public static final String DATA_FOLDER = System.getProperty("data.root", "C:/Dev/__GIT/jonesserver/data");
    public static final String INDEX_FOLDER = DATA_FOLDER + "/onVista/index";

    private StockPointLoader()
    {}


    public static StockPoints load(String isin)
    {
        try
        {
            List<String> lines = Files.readAllLines(new File(INDEX_FOLDER, isin + ".json").toPath(), StandardCharsets.UTF_8);
            StockPoints p = new StockPoints(lines);
            return p;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }


    public static TableData loadRaw(List<String> isins, long start, int type)
    {
        TableData data = new TableData();

        long fromDate = start;
        long toDate = System.currentTimeMillis();

        isins.forEach(isin -> {
            ArivaHistoricData ariva = new ArivaHistoricData(DATA_FOLDER);
            List<String> lines = ariva.getData(isin);
            data.addLines(lines, fromDate, toDate, type);
        });
        return data;
    }


    public static boolean prefetchIsin(String isin)
    {
        ArivaHistoricData ariva = new ArivaHistoricData(DATA_FOLDER);
        return ariva.preFetch(isin) != null;
    }


    public static Map<Long, Double> loadRawForBranch(String branch, String country, Long start)
    {
        Map<String, Map<Long, Double>> values = new HashMap<>();

        String query1 = "SELECT cIsin, cLast, cDateLong, cDate from tStocks where cDateLong >= ? ORDER BY cIsin, cDateLong ASC";

        String query2 = "SELECT cIsin, cLast, cDateLong, cDate from tStocks where cIsin IN (SELECT cIsin FROM tOnVista where cBranch in (${branches})) and cDateLong >= ? ORDER BY cIsin, cDateLong ASC";

        String query3 = "SELECT cIsin, cLast, cDateLong, cDate from tStocks where cIsin IN (SELECT cIsin FROM tOnVista where cBranch in (${branches}) and cCountryCode in (${countries}))"
                        + " and cDateLong >= ? ORDER BY cIsin, cDateLong ASC";

        String finalQuery = query1;

        final StringBuilder psBranches = new StringBuilder();
        final List<String> branches = new ArrayList<>();
        final StringBuilder psCountries = new StringBuilder();
        final List<String> countries = new ArrayList<>();

        if (branch != null && branch.length() > 0)
        {
            branches.addAll(Arrays.asList(branch.split(",")));
            branches.forEach(item -> {
                psBranches.append("?,");
            });
            psBranches.setLength(psBranches.length() - 1);
            finalQuery = query2.replace("${branches}", psBranches);
        }
        if (country != null && country.length() > 0)
        {
            countries.addAll(Arrays.asList(country.split(",")));
            countries.forEach(item -> {
                psCountries.append("?,");
            });
            psCountries.setLength(psCountries.length() - 1);
            if (!branches.isEmpty())
            {
                finalQuery = query3.replace("${branches}", psBranches).replace("${countries}", psCountries);
            }
            else
            {
                finalQuery = query3.replace("${countries}", psCountries);
            }
        }
        try (Connection connection = DBConnection.getStocksConnection())
        {
            try (PreparedStatement ps = connection.prepareStatement(finalQuery))
            {
                AtomicInteger index = new AtomicInteger(0);
                branches.forEach(b -> {
                    try
                    {
                        ps.setString(index.incrementAndGet(), b);
                    }
                    catch (SQLException e)
                    {
                        e.printStackTrace();
                    }
                });
                countries.forEach(c -> {
                    try
                    {
                        ps.setString(index.incrementAndGet(), c);
                    }
                    catch (SQLException e)
                    {
                        e.printStackTrace();
                    }
                });
                ps.setLong(index.incrementAndGet(), start);
                ps.execute();
                ResultSet rs = ps.getResultSet();
                Double refValue = 1d;
                Map<Long, Double> mIsin = new HashMap<>();
                String lastIsin = "";
                while (rs.next())
                {
                    Double last = rs.getDouble("cLast");
                    String currentIsin = rs.getString("cIsin");
                    if (!lastIsin.equals(currentIsin))
                    {
                        values.put(lastIsin, mIsin);
                        lastIsin = currentIsin;
                        mIsin.clear();

                        refValue = last;
                    }
                    mIsin.put(rs.getLong("cDateLong"), last / refValue);
                }
                values.put(lastIsin, mIsin);// dont forget the last map
            }
        }
        catch (Exception exc)
        {
            exc.printStackTrace();
        }
        final StringBuilder refIsin = new StringBuilder();
        int size = 0;
        values.forEach((isin, m) -> {
            if (m.size() > size)
            {
                refIsin.setLength(0);
                refIsin.append(isin);
            }
        });
        Map<Long, Double> data = new TreeMap<>();
        Map<Long, Double> refMap = values.get(refIsin.toString());
        refMap.keySet().forEach(timestamp -> {
            AtomicReference<Double> d = new AtomicReference<>((double)0);
            AtomicLong cnt = new AtomicLong();
            values.forEach((isin, m) -> {
                double dd = d.get();
                Double ddd = m.get(timestamp);
                if (ddd != null)
                {
                    d.getAndSet(dd += ddd);
                    cnt.incrementAndGet();
                }
            });
            double r = d.get() / cnt.get();
            data.put(timestamp, r);
        });
        return data;
    }
}
