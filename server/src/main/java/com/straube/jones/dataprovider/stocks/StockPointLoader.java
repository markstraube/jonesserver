package com.straube.jones.dataprovider.stocks;


import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import com.github.openjson.JSONArray;
import com.straube.jones.cmd.ariva.ArivaHistoricData;

public class StockPointLoader
{
    public static final String DATA_FOLDER = System.getProperty("user.home") + "/data";
    public static final String INDEX_FOLDER = DATA_FOLDER + "/onVista/index";

    private static final DateFormat dfISO = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMAN);

    private StockPointLoader()
    {}


    public static StockPoints load(String isin)
    {
        try
        {
            List<String> lines = Files.readAllLines(new File(INDEX_FOLDER, isin + ".json").toPath(), Charset.forName("UTF-8"));
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
            try
            {
                File indexFile = new File(INDEX_FOLDER, isin + ".json");
                List<String> lines = Files.readAllLines(indexFile.toPath(), Charset.forName("UTF-8"));
                String line = lines.get(0);
                JSONArray jar = new JSONArray(line);
                long startDate = jar.getLong(3);
                if (start < startDate)
                {
                    String name = jar.getString(1);
                    ArivaHistoricData ariva = new ArivaHistoricData(INDEX_FOLDER);
                    ariva.load("01.01.2022", "27.11.2022", isin, name);
                    lines = Files.readAllLines(indexFile.toPath(), Charset.forName("UTF-8"));
                }
                data.addLines(lines, fromDate, toDate, type);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        });
        return data;
    }
}
