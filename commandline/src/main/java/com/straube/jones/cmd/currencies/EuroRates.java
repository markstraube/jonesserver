package com.straube.jones.cmd.currencies;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.straube.jones.cmd.html.HttpTools;

public class EuroRates
{
    public static void main(String[] args)
        throws IOException
    {
        EuroRates euroRates = new EuroRates("C:\\Dev\\__GIT\\jonesserver\\data\\onVista\\eurorates");

        Map<String, Double> rates = new HashMap<>();

        euroRates.load(rates);
    }

    public final File rootFolder;
    public final File cacheFolder;
    public static final NumberFormat NF = NumberFormat.getInstance(Locale.ENGLISH);
    public static final SimpleDateFormat DF = new SimpleDateFormat("d.m.yyyy");
    public static final SimpleDateFormat ISO = new SimpleDateFormat("yyyy-mm-dd");

    public EuroRates(String rootFolder)
    {
        this.rootFolder = new File(rootFolder, "eurorates");
        this.cacheFolder = new File(this.rootFolder, "cache");
        this.cacheFolder.mkdirs();
    }


    public long load(Map<String, Double> rates)
    {
        String baseURL = "https://wechselkurse-euro.de/";
        
        long timestamp = System.currentTimeMillis();
        Date d = new Date();
        d.setTime(timestamp);
        String filename = ISO.format(d) + ".html";
        File htmlFile = new File(this.cacheFolder,filename);
        try
        {
            String response = HttpTools.downloadFromWebToFile(baseURL, htmlFile, false);
            //String response = HttpTools.downloadFromWebToString(baseURL);
            Document doc = Jsoup.parse(response);
            String timestampString = doc.selectFirst("#page > h2.ecb").text();
            timestamp = extractTimestamp(timestampString);
            rates.put("TIMESTAMP", timestamp * 1d);

            Elements rows = doc.select("#page > div.table_responsive.mb10social > table > tbody > tr");
            extractRates(rows, rates);

            rows = doc.select("#page > div.table_responsive.mb20-mt > table > tbody > tr");
            extractRates(rows, rates);

            rates.put("EUR", 1d);

            d = new Date();
            d.setTime(timestamp);
            filename = ISO.format(d) + ".json";

            JSONObject jo = new JSONObject(rates);
            try (FileWriter w = new FileWriter(new File(rootFolder, filename), Charset.forName("UTF-8")))
            {
                jo.write(w, 4, 4);
            }
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
