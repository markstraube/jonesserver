package com.straube.jones.cmd.ariva;


import java.io.File;
import java.io.FileWriter;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import com.straube.jones.cmd.html.HttpTools;

public class ArivaHistoricData
{

    public static void main(String[] args)
        throws Exception
    {
        ArivaHistoricData ariva = new ArivaHistoricData("C:\\Dev\\__GIT\\jonesserver\\data\\ariva\\historic");
        String startDate = "01.01.2022";
        String endDate = "27.11.2022";

        DirectoryStream.Filter<Path> filter = file -> {
            final String fileName = file.toFile().getName();
            return (fileName.endsWith(".json"));
        };

        Path dirName = Path.of("C:\\Dev\\__GIT\\jonesserver\\data\\onVista\\finder2\\2022-11-25");

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
                            String name = String.valueOf(list.get(1));

                            ariva.load(startDate, endDate, isin, name);
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

    public final File rootFolder;
    private static final DateFormat dfAriva = new SimpleDateFormat("yyyy-MM-dd", Locale.GERMAN);
    private static final DateFormat dfISO = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMAN);

    public ArivaHistoricData(String rootFolder)
    {
        this.rootFolder = new File(rootFolder);
        this.rootFolder.mkdirs();
    }


    public boolean load(String startDate, String endDate, String isin, String name)
    {
        String baseURL = String.format("https://www.ariva.de/%s/historische_kurse", isin);
        try
        {
            String response = HttpTools.downloadFromWebToString(baseURL);
            Document doc = Jsoup.parse(response);
            Elements elSecu = doc.select("#pageHistoricQuotes > div.column.third.last > div.formRow.abstand > form > input[name=secu]");
            String secu = elSecu.attr("value");
            Elements elBoerseId = doc.select("#pageHistoricQuotes > div.column.third.last > div.formRow.abstand > form > input[name=boerse_id]");
            String boerseId = elBoerseId.attr("value");
            String trenner = ";";
            String trennerEncoded = "%3b";
            String downloadUrl = String.format("https://www.ariva.de/quote/historic/historic.csv?secu=%s&boerse_id=%s&clean_split=1&clean_payout=0&clean_bezug=1&min_time=%s&max_time=%s&trenner=%s&go=Download",
                                               secu,
                                               boerseId,
                                               startDate,
                                               endDate,
                                               trennerEncoded);
            String historic = HttpTools.downloadFromWebToString(downloadUrl);
            // from: Datum; Erster; Hoch; Tief; Schlusskurs; Stuecke; Volumen
            // 2021-12-27; 264,20; 266,80; 263,00; 264,00; 22.025; 5.829.269
            // to: ["DE0006916604","Pfeiffer Vacuum Technology","2020-06-15T17:35:00",1592235300000,150.6]
            String[] lines = historic.split("\n");
            File dataFile = new File(rootFolder, isin + ".json");
            try (FileWriter w = new FileWriter(dataFile, Charset.forName("UTF-8")))
            {
                for (int i = lines.length - 1; i > 0; i-- )
                {
                    String[] segs = lines[i].split(trenner);
                    if (segs.length < 7)
                    {
                        continue;
                    }
                    String sDate = segs[0];
                    String sOpen = segs[1].replace(".", "").replace(',', '.');
                    String sHigh = segs[2].replace(".", "").replace(',', '.');
                    String sLow = segs[3].replace(".", "").replace(',', '.');
                    String sFinish = segs[4].replace(".", "").replace(',', '.');
                    String sPeaces = segs[5].replace(".", "");
                    String sVolume = segs[6].replace(".", "");
                    long timestamp = dfAriva.parse(sDate).getTime() + 23 * 60 * 60 * 1000;
                    String sISODate = dfISO.format(new Date(timestamp));

                    w.write(String.format("[\"%s\",\"%s\",\"%s\",%d,%s,%s,%s,%s,%s,%s]%n", isin, name, sISODate, timestamp, sFinish, sOpen, sHigh, sLow, sPeaces, sVolume));
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
