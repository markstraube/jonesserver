package com.straube.jones.cmd.onVista;


import java.io.File;
import java.io.FileWriter;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import com.straube.jones.cmd.currencies.EuroRates;
import com.straube.jones.cmd.html.HttpTools;

public class OnVistaFundamentals
{
    public static void main(String[] args)
        throws Exception
    {
        OnVistaFundamentals onVista = new OnVistaFundamentals(new File("C:\\Dev\\__GIT\\jonesserver\\data\\onVista"));

        DirectoryStream.Filter<Path> filter = file -> {
            final String fileName = file.toFile().getName();
            return (fileName.endsWith(".json"));
        };

        final Map<String, Long> mStockCounter = new HashMap<>();

        Path dirName = Path.of("C:\\Dev\\__GIT\\jonesserver\\data\\onVista\\finder2\\2022-12-02");

        try (DirectoryStream<Path> paths = Files.newDirectoryStream(dirName, filter))
        {
            paths.forEach((path) -> {
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

                            Long l = onVista.getStocksCount(isin, href);
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
        try (FileWriter w = new FileWriter(new File(onVista.rootFolder, "StocksCounter.json"), Charset.forName("UTF-8")))
        {
            jo.write(w, 4, 4);
        }
    }

    public final File rootFolder;
    public final File cacheFolder;
    public static final NumberFormat NF = NumberFormat.getInstance(Locale.ENGLISH);

    Map<String, Double> rates = new HashMap<>();

    public OnVistaFundamentals(File rootFolder)
    {
        this.rootFolder = new File(rootFolder, "fundamentals");
        this.cacheFolder = new File(this.rootFolder, "cache");
        this.cacheFolder.mkdirs();

        (new EuroRates(rootFolder)).load(rates);
    }


    public Long getStocksCount(String isin, String shortUrl)
    {
        String baseURL = String.format("https://www.onvista.de/aktien/unternehmensprofil/%s", shortUrl);
        File htmlFile = new File(this.cacheFolder, isin + ".html");
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
}
