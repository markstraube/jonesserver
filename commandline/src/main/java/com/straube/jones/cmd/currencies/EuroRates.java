package com.straube.jones.cmd.currencies;


import java.io.File;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.straube.jones.cmd.html.HttpTools;

public class EuroRates
{
    public final File rootFolder;
    public final File cacheFolder;
    public static final NumberFormat NF = NumberFormat.getInstance(Locale.ENGLISH);
    public static final SimpleDateFormat DF = new SimpleDateFormat("d.M.yyyy");
    public static final SimpleDateFormat ISO = new SimpleDateFormat("yyyy-MM-dd");

    public EuroRates(File folder)
    {
        this.rootFolder = new File(folder, "eurorates");
        this.cacheFolder = new File(this.rootFolder, "cache");
        this.cacheFolder.mkdirs();
    }


    public long load()
    {
        Map<String, Double> rates = new HashMap<>();
        String baseURL = "https://wechselkurse-euro.de/";
        
        long timestamp = System.currentTimeMillis();
        Date d = new Date(timestamp);
        String filename = ISO.format(d) + ".html";
        File htmlFile = new File(this.cacheFolder,filename);
        try
        {
            String response = HttpTools.downloadFromWebToFile(baseURL, htmlFile, false);
            //String response = HttpTools.downloadFromWebToString(baseURL);
            Document doc = Jsoup.parse(response);
            String timestampString = doc.selectFirst("#page > h2.ecb").text();
            timestamp = extractTimestamp(timestampString);

            Elements rows = doc.select("#page > div.table_responsive.mb10social > table > tbody > tr");
            extractRates(rows, rates);

            rows = doc.select("#page > div.table_responsive.mb20-mt > table > tbody > tr");
            extractRates(rows, rates);

            CurrencyDB.update(timestamp, rates);
        }
        catch (Exception ignore)
        {
            ignore.printStackTrace();
        }
        return timestamp;
    }


    private void extractRates(Elements rows, Map<String, Double> rates)
        throws ParseException
    {
        int sz = rows.size();
        for (int i = 0; i < sz; i++ )
        {
            Element e = rows.get(i);
            Elements es = e.select("td:nth-child(4) > a");
            if (es.isEmpty())
            {
                continue;
            }
            String currency = es.first().text();

            es = e.select("td:nth-child(6)");
            if (es.isEmpty())
            {
                continue;
            }
            String rate = es.first().text();
            Number nRate = NF.parse(rate);
            rates.put(currency, nRate.doubleValue());
        }

    }


    private long extractTimestamp(String timestampString)
    {
        try
        {
            String[] segs = timestampString.split(" ");
            Date date = DF.parse(segs[segs.length - 1]);
            return date.getTime();
        }
        catch (Exception ignore)
        {
            ignore.printStackTrace();
        }
        return 0;
    }
}
