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

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.straube.jones.cmd.currencies.EuroRates;
import com.straube.jones.cmd.html.HttpTools;

public class OnVistaFundamentals
{
    public static void main(String[] args)
        throws Exception
    {
        OnVistaFundamentals onVista = new OnVistaFundamentals("C:\\Dev\\__GIT\\jonesserver\\data\\onVista");

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

    public OnVistaFundamentals(String rootFolder)
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

        try
        {
            String response = HttpTools.downloadFromWebToFile(baseURL, htmlFile, false);
            Document doc = Jsoup.parse(response);
            Elements rows = doc.select("#__next > div.ov-content > div > section > div.col.col-12.inner-spacing--medium-top.ov-snapshot-tabs > div > section > div.col.grid.col--sm-4.col--md-8.col--lg-9.col--xl-9 > div");
            int sz = rows.size();
            for (int i = 0; i < sz; i++ )
            {
                Element e = rows.get(i);
                Elements es = e.select("div > div > div > div > table > tbody > tr");
                if (es.isEmpty())
                {
                    continue;
                }
                int sz2 = es.size();
                for (int j = 0; j < sz2; j++ )
                {
                    Elements es2 = es.get(j).select("tr > td > div > div > > div > a");
                    if (es2.isEmpty())
                    {
                        continue;
                    }
                    String href = es2.first().attr("href");
                    if (href == null || !href.contains(isin))
                    {
                        continue;
                    }
                    Elements es3 = es.get(j).select("tr > td:nth-child(3) > data");
                    if (es3.isEmpty())
                    {
                        continue;
                    }
                    String capitalization = es3.first().attr("value");
                    Elements es4 = es.get(j).select("tr > td:nth-child(4) > data");
                    if (es4.isEmpty())
                    {
                        continue;
                    }
                    String quote = es4.first().attr("value");
                    String currency = es.get(j).select("tr > td:nth-child(4) > data > span").first().text().toUpperCase();
                    Number nQuote = NF.parse(quote);
                    Number nCapitalization = NF.parse(capitalization);
                    if (rates.get(currency) == null)
                    {
                        break;
                    }
                    Double d = nCapitalization.doubleValue() / nQuote.doubleValue() * rates.get(currency);
                    return d.longValue();
                }
            }
        }
        catch (Exception ignore)
        {
            ignore.printStackTrace();
        }
        return 0L;
    }
}
