package com.straube.jones.cmd.onVista;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import com.straube.jones.cmd.html.HttpTools;

public class OnVistaFundamentals
{
    public static void reloadAllCounter(String dataRoot, String dateString)
        throws Exception
    {
        System.out.println(String.format("... reloading all stocks counter for date:%s.", dateString));
        File fundamentalFolder = new File(dataRoot, "onVista/fundamentals");
        File cacheFolder = new File(fundamentalFolder, "cache");
        cacheFolder.mkdirs();

        DirectoryStream.Filter<Path> filter = file -> {
            final String fileName = file.toFile().getName();
            return (fileName.endsWith(".json"));
        };

        final Map<String, Long> mStockCounter = new HashMap<>();

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
                            String href = String.valueOf(list.get(1));

                            Long l = getStocksCount(isin, href, cacheFolder);
                            mStockCounter.put(isin, l);
                        }
                    });
                }
                catch (Exception ignore)
                {
                    ignore.printStackTrace();
                }
            });
        }
        JSONObject jo = new JSONObject(mStockCounter);
        try (FileWriter w = new FileWriter(new File(fundamentalFolder, "StocksCounter.json"), Charset.forName("UTF-8")))
        {
            jo.write(w, 4, 4);
        }
    }


    public static Long getStocksCount(String isin, String shortUrl, File cacheFolder)
    {
        String baseURL = String.format("https://www.onvista.de/aktien/unternehmensprofil/%s", shortUrl);
        File htmlFile = new File(cacheFolder, isin + ".html");
        AtomicLong stockCount = new AtomicLong(0);

        try
        {
            String response = HttpTools.downloadFromWebToFile(baseURL, htmlFile, false);
            Document doc = Jsoup.parse(response);
            Elements rows = doc.select("#allStocks");
            if (rows != null && !rows.isEmpty())
            {
                List<Node> nodes = rows.first().parent().parent().nextSibling().childNode(0).childNode(0).childNode(0).childNode(0).childNode(0).childNode(1).childNodes();
                nodes.forEach((r -> {
                    if (stockCount.get() == 0)
                    {
                        try
                        {
                            String href = r.childNode(0).childNode(0).childNode(0).attr("href");
                            if (href.contains(isin))
                            {
                                String count = r.childNode(1).childNode(0).attr("value");
                                stockCount.set(parseLong(count));
                            }
                            else
                            {
                                href = r.childNode(0).childNode(0).childNode(0).childNode(0).childNode(0).attr("href");
                                if (href.contains(isin))
                                {
                                    String count = r.childNode(1).childNode(0).attr("value");
                                    stockCount.set(parseLong(count));
                                }
                            }
                        }
                        catch (Exception ignore)
                        {
                            System.err.println(String.format("### Failed parsing #allStocks for isin:%s", isin));
                        }
                    }
                }));
            }
            if (stockCount.get() == 0)
            {
                rows = doc.select("#marketData");
                if (rows != null && !rows.isEmpty())
                {
                    try
                    {
                        String count = rows.first().parent().parent().nextSibling().nextSibling().childNode(0).childNode(0).childNode(0).childNode(1).childNode(0).attr("value");
                        stockCount.set(parseLong(count));
                    }
                    catch (Exception ignore)
                    {
                        System.err.println(String.format("### Failed parsing #marketData for isin:%s", isin));
                    }
                }
            }
            if (stockCount.get() == 0)
            {
                System.out.println(String.format("### No stocks counter found for isin:%s", isin));
            }
        }
        catch (Exception ignore)
        {
            System.err.println(String.format("### Unexpected error for isin:%s", isin));
        }
        return stockCount.get();
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


    public static Map<String, Object> getStocksCounter(String rootFolder) throws IOException
    {
            byte[] buf = Files.readAllBytes(Paths.get(rootFolder, "fundamentals", "StocksCounter.json"));
            JSONObject jo = new JSONObject(new String(buf, StandardCharsets.UTF_8));
            return jo.toMap();
    }
}
