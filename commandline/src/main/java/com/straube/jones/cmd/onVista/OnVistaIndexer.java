package com.straube.jones.cmd.onVista;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class OnVistaIndexer
{
    final File indexFolder;
    final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss");

    Map<String, FileWriter> writers = new HashMap<>();

    public boolean bStop = false;

    public static void main(String[] args)
        throws Exception
    {
        OnVistaIndexer.index(new File("./data/onVista/finder"), "./data");
    }


    public static void index(File targetFolder, String dataRoot)
    {
        final OnVistaIndexer indexer = new OnVistaIndexer(dataRoot + "/onVista");

        try
        {
            indexer.process(targetFolder);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }


    public void process(File workDir)
        throws IOException
    {
        try (final Stream<Path> walkStream = Files.walk(workDir.toPath()))
        {
            final AtomicBoolean bContinue = new AtomicBoolean(true);
            walkStream.filter(p -> p.toFile().getAbsolutePath().endsWith(".json")).forEach(f -> {
                if (bContinue.get() && !bStop)
                {
                    bContinue.set(indexToJson(f));
                }
            });
        }
        finally
        {
            close();
        }
    }


    public OnVistaIndexer(String dataRoot)
    {
        indexFolder = new File(dataRoot + "/index");
        indexFolder.mkdirs();
    }


    private boolean indexToJson(Path path)
    {
        try
        {
            JSONObject jo = new JSONObject(Files.readString(path));
            JSONArray stocks = jo.getJSONArray("stocks");
            AtomicBoolean bContinue = new AtomicBoolean(true);
            stocks.forEach(stock -> {
                try
                {
                    if (bContinue.get())
                    {
                        bContinue.set(registerToJson(stock, path.toFile()));
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            });
            return bContinue.get();
        }
        catch (Exception e)
        {
            System.out.println("Failed reading:" + path.toFile().getAbsolutePath());
            e.printStackTrace();
        }
        return false;
    }


    private boolean registerToJson(Object stock, File stocksFile)
        throws Exception
    {
        if (stock instanceof JSONObject)
        {
            Map<String, Object> m = ((JSONObject)stock).toMap();

            String date = String.valueOf(m.get("date"));
            if ("null".equals(date) || "n.a.".equals(date))
            { return true; }

            Map<String, Object> mFigures = (Map<String, Object>)(m.get("figures"));
            String testPerf4 = (String)(mFigures.get("PERFORMANCE_4_WEEKS"));
            if (testPerf4 == null || testPerf4.length() == 0)
            { return true; }

            String url = String.valueOf(m.get("url"));
            String[] s = url.split("-");
            final String isin = s[s.length - 1];

            String last = String.valueOf(m.get("last"));
            s = last.split(" ");
            String sValue = s[0].replace(".", "").replace(",", ".");
            if (!sValue.contains(".") || sValue.contains("%"))
            { return true; }
            final double value = Double.parseDouble(sValue);
            final String currency = s[1].trim();

            s = stocksFile.getParentFile().getName().split("-");
            final String year = s[0];
            s = date.split("/");
            final String time = s[1].trim() + ":00";
            s = s[0].split("\\.");
            final String day = s[0].trim();
            final String month = s[1].trim();
            final String stocksDateTime = String.format("%s-%s-%sT%s", year, month, day, time);

            final long stockDateLong = df.parse(stocksDateTime).getTime();

            String name = (String)m.get("name");// : "Microsoft",
            File jsonFile = new File(indexFolder, isin + ".json");
            FileWriter w = writers.get(isin);
            if (w == null)
            {
                if (jsonFile.exists())
                {
                    // set the last know timestamp
                    List<String> lines = FileUtils.readLines(jsonFile, "UTF-8");
                    String lastLine = lines.get(lines.size() - 1);
                    JSONArray jar = new JSONArray(lastLine);
                    Long timestamp = jar.getLong(3);
                    isKnownData(isin, timestamp);
                    if (stockDateLong < timestamp)
                    { return true; }
                }
                w = new FileWriter(jsonFile, Charset.forName("UTF-8"), true);
                writers.put(isin, w);
            }
            if (isKnownData(isin, stockDateLong))
            {
                return true; // do not report already reported data
            }
            try (FileWriter mw = new FileWriter(new File(indexFolder, isin + ".meta.json"), Charset.forName("UTF-8")))
            {
                String country = (String)m.get("country");
                String nsin = (String)m.get("nsin");// : "870747",
                String countryCode = (String)m.get("countryCode");// : "us",
                String branch = (String)m.get("branch");// : "Standardsoftware",

                JSONObject meta = new JSONObject();
                meta.put("name", name);
                meta.put("isin", isin);
                meta.put("country", country);
                meta.put("country-code", countryCode);
                meta.put("currency", currency);
                meta.put("branch", branch);
                meta.put("nsin", nsin);
                meta.put("url", url);

                meta.put("capitalization", mFigures.get("MARKET_CAPITALIZATION"));
                meta.put("turnover", mFigures.get("TURNOVER"));
                meta.put("dividend", mFigures.get("DIVIDEND_AMOUNT"));
                meta.put("perf4", mFigures.get("PERFORMANCE_4_WEEKS"));
                meta.put("perf26", mFigures.get("PERFORMANCE_6_MONTHS"));
                meta.put("perf52", mFigures.get("PERFORMANCE_52_WEEKS"));
                meta.write(mw, 0, 0);
            }
            JSONArray data = new JSONArray();
            data.put(isin);
            data.put(name);
            data.put(stocksDateTime);
            data.put(stockDateLong);
            data.put(value);

            data.write(w, 0, 0);
            w.write("\n");
        }
        return true;
    }

    private Map<String, Long> knownData = new HashMap<>();

    private boolean isKnownData(String isin, long timestamp)
    {
        String key = isin + timestamp;
        if (knownData.get(key) == null)
        {
            knownData.put(key, timestamp);
            return false;
        }
        return true;
    }


    private void close()
    {
        writers.forEach((k, v) -> {
            try
            {
                v.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        });
    }
}
