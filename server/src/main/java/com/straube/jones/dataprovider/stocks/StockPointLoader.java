package com.straube.jones.dataprovider.stocks;


import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import com.straube.jones.cmd.ariva.ArivaHistoricData;

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
            List<String> lines = ariva.load(isin);
            data.addLines(lines, fromDate, toDate, type);
        });
        return data;
    }


    public static boolean prefetchIsin(String isin)
    {
        ArivaHistoricData ariva = new ArivaHistoricData(DATA_FOLDER);
        return ariva.preFetch(isin) != null;
    }
}
