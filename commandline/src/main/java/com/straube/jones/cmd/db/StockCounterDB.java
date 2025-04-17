package com.straube.jones.cmd.db;


import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.straube.jones.cmd.html.HttpTools;

public class StockCounterDB
{
    final static String dataRoot = System.getProperty("data.root", "./data");
    private static JSONObject stockCounterDB;
    static
    {
        File fStocksCounter = new File(dataRoot, "onVista/fundamentals/StocksCounter.json");
        if (!fStocksCounter.exists())
        {
            stockCounterDB = new JSONObject();
        }
        else
        {
            try
            {
                stockCounterDB = new JSONObject(new String(Files.readAllBytes(fStocksCounter.toPath())));
            }
            catch (Exception ignore)
            {
                ignore.printStackTrace();
            }
        }
    }

    public static void reloadAllCounter(String dataRoot, String dateString, boolean reloadFromCache)
        throws Exception
    {
        System.out.println(String.format("... reloading all stocks counter for date:%s.", dateString));
        File fundamentalFolder = new File(dataRoot, "onVista/fundamentals");
        File cacheFolder = new File(fundamentalFolder, dateString);
        if (reloadFromCache)
        {
            if (!cacheFolder.exists())
            {
                System.out.println(String.format("... cache folder %s does not exist - giving up.", cacheFolder.getAbsolutePath()));
            }
        }
        else
        {
            cacheFolder.mkdirs();
            DirectoryStream.Filter<Path> filter = file -> {
                final String fileName = file.toFile().getName();
                return (fileName.endsWith(".json"));
            };
            Path dirName = Path.of(dataRoot, "onVista/finder2/", dateString);
            try (DirectoryStream<Path> paths = Files.newDirectoryStream(dirName, filter))
            {
                paths.forEach(path -> {
                    try
                    {
                        String buf = new String(Files.readAllBytes(path));
                        JSONObject jo = new JSONObject(buf);
                        JSONArray ja = jo.getJSONArray("values");
                        ja.forEach(e -> {
                            if (e instanceof JSONArray)
                            {
                                List<Object> list = ((JSONArray)e).toList();
                                String isin = String.valueOf(list.get(0));
                                String shortUrl = String.valueOf(list.get(1));
                                String baseURL = String.format("https://www.onvista.de/aktien/unternehmensprofil/%s", shortUrl);
                                File htmlFile = new File(cacheFolder, isin + ".html");

                                try
                                {
                                    HttpTools.downloadFromWebToFile(baseURL, htmlFile, false);
                                }
                                catch (Exception ignore)
                                {
                                    System.err.println(String.format("### Unexpected error downloading isin:%s", isin));
                                }
                            }
                        });
                    }
                    catch (Exception ignore)
                    {
                        ignore.printStackTrace();
                    }
                });
            }
        }
        stockCounterDB = parseStockCounter(fundamentalFolder, cacheFolder);
        Files.write(Paths.get(dataRoot, "onVista/fundamentals/StocksCounter.json"), stockCounterDB.toString(2).getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
    }


    private static JSONObject parseStockCounter(File fundamentalFolder, File cacheFolder)
    {
        JSONObject newCounterDB = new JSONObject();

        DirectoryStream.Filter<Path> filter = file -> {
            final String fileName = file.toFile().getName();
            return (fileName.endsWith(".html"));
        };
        Path htmlPath = cacheFolder.toPath();
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(htmlPath, filter))
        {
            paths.forEach(path -> {
                try
                {
                    long stockCount = 0;
                    String fileName = path.toFile().getName();
                    String isin = fileName.split("\\.")[0];
                    String content = new String(Files.readAllBytes(path));
                    int pos = content.indexOf("Anzahl Aktien");
                    int pos2 = content.indexOf("Anzahl Aktien", pos + 1);
                    if (pos2 < 0)
                    {
                        int valuePos = content.indexOf("Anzahl Aktien", pos + 1);
                        if (valuePos - pos < 250) // no value;
                        {
                            int startPos = content.indexOf("\"", valuePos) + 1;
                            int endPos = content.indexOf("\"", startPos);
                            String value = content.substring(startPos, endPos);
                            stockCount = parseLong(value);
                        }
                    }
                    else
                    {
                        pos = content.indexOf(isin, pos2);
                        int valuePos = content.indexOf("value", pos + 1);
                        if (valuePos - pos < 300) // no value;
                        {
                            int startPos = content.indexOf("\"", valuePos) + 1;
                            int endPos = content.indexOf("\"", startPos);
                            String value = content.substring(startPos, endPos);
                            stockCount = parseLong(value);
                        }
                    }
                    newCounterDB.put(isin, stockCount);
                }
                catch (Exception exc)
                {
                    System.out.println(String.format(" ... skipped loading file %s -> due to error: %s", path.toFile().getName(), exc.getMessage()));
                    exc.printStackTrace();
                }
            });
        }
        catch (Exception ignore)
        {
            ignore.printStackTrace();
        }
        return newCounterDB;
    }


    private static long parseLong(String s)
    {
        if (s != null || !s.isEmpty())
        {
            try
            {
                Double d = Double.parseDouble(s);
                return d.longValue();
            }
            catch (Exception e)
            {}
        }
        return 0L;
    }


    public static Map<String, Object> getStocksCounter(String rootFolder)
        throws IOException
    {
        byte[] buf = Files.readAllBytes(Paths.get(rootFolder, "fundamentals", "StocksCounter.json"));
        JSONObject jo = new JSONObject(new String(buf, StandardCharsets.UTF_8));
        return jo.toMap();
    }


    public static long getStockCounter(String isin)
    {
        if (!stockCounterDB.has(isin))
        { return 0L; }
        return stockCounterDB.getLong(isin);
    }
}
