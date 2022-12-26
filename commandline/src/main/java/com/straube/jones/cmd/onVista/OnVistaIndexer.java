package com.straube.jones.cmd.onVista;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
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
            JSONArray stocks = jo.getJSONArray("values");
            AtomicBoolean bContinue = new AtomicBoolean(true);
            stocks.forEach(stock -> {
                try
                {
                    if (bContinue.get())
                    {
                        bContinue.set(registerToJson(stock));
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


    private boolean registerToJson(Object stock)
        throws Exception
    {
        if (stock instanceof JSONArray)
        {
            List<Object> m = ((JSONArray)stock).toList();

            String isin = ((String)m.get(0));
            Long dateLong = OnVistaModel.makeLong(m.get(9));

            File jsonFile = new File(indexFolder, isin + ".json");
            if (jsonFile.exists())
            {
                // set the last know timestamp
                List<String> lines = FileUtils.readLines(jsonFile, "UTF-8");
                String lastLine = lines.get(lines.size() - 1);
                JSONArray jar = new JSONArray(lastLine);
                Long timestamp = jar.getLong(3);
                isKnownData(isin, timestamp);
                if (dateLong < timestamp)
                { return true; }
            }
            if (isKnownData(isin, dateLong))
            {
                return true; // do not report already reported data
            }
            String name = (String)m.get(1); // name
            long timestamp = dateLong;
            String sISODate = df.format(timestamp);
            String sFinish = OnVistaModel.makeDouble(m.get(7)).toString();
            String sOpen = sFinish;
            String sHigh = sFinish;
            String sLow = sFinish;
            String sPeaces = "0";
            String sVolume = "0";
            try (Writer w = new FileWriter(jsonFile, StandardCharsets.UTF_8, true))
            {
                w.write(String.format("[\"%s\",\"%s\",\"%s\",%d,%s,%s,%s,%s,%s,%s]%n", isin, name, sISODate, timestamp, sFinish, sOpen, sHigh, sLow, sPeaces, sVolume));
            }
            catch(Exception ignore)
            {
                ignore.printStackTrace();
            }
        }
        return true;
    }

    private Map<String, Long> knownData = new HashMap<>();

    private boolean isKnownData(String isin, long timestamp) {
        String key = isin + timestamp;
        return knownData.computeIfPresent(key, (k, v) -> timestamp) != null;
    }    
}
