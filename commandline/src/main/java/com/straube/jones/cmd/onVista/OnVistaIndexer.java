package com.straube.jones.cmd.onVista;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
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
            JSONArray stocks = jo.getJSONArray("values");
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
        if (stock instanceof JSONArray)
        {
            List<Object> m = ((JSONArray)stock).toList();

            String isin = ((String)m.get(0));
            Long dateLong = OnVistaModel.makeLong(m.get(9));

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
                    if (dateLong < timestamp)
                    { return true; }
                }
                w = new FileWriter(jsonFile, Charset.forName("UTF-8"), true);
                writers.put(isin, w);
            }
            if (isKnownData(isin, dateLong))
            {
                return true; // do not report already reported data
            }
            try (FileWriter mw = new FileWriter(new File(indexFolder, isin + ".meta.json"), Charset.forName("UTF-8")))
            {

                JSONArray data = new JSONArray();
                data.put(isin); // isin
                data.put((String)m.get(1)); // name
                Date d = new Date();
                d.setTime(dateLong);
                data.put(df.format(d));
                data.put(dateLong);
                data.put(OnVistaModel.makeDouble(m.get(7)));

                data.write(w, 0, 0);
                w.write("\n");
            }
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
