package com.straube.jones.cmd.ariva;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import com.straube.jones.cmd.html.HttpTools;

public class ArivaHistoricData
{
    public final File rootFolder;
    private final DateFormat dfArivaHistoric = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN);
    private final DateFormat dfAriva = new SimpleDateFormat("yyyy-MM-dd", Locale.GERMAN);
    private final DateFormat dfISO = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMAN);

    public ArivaHistoricData(String rootFolder)
    {
        this.rootFolder = new File(rootFolder, "ariva/historic");
        this.rootFolder.mkdirs();
    }


    public List<String> load(String isin, String name)
    {
        List<String> result = new ArrayList<>();
        String startDate = dfArivaHistoric.format(LocalDate.now().minusYears(1).toEpochDay() * 24 * 60 * 60 * 1000);
        String endDate = dfArivaHistoric.format(LocalDate.now().toEpochDay() * 24 * 60 * 60 * 1000);

        File dataFile = new File(rootFolder, isin + ".json");
        if (dataFile.exists() && (dataFile.lastModified() > System.currentTimeMillis() - 24 * 60 * 60 * 1000))
        {
            try
            {
                List<String> lines = Files.readAllLines(dataFile.toPath(), StandardCharsets.UTF_8);
                return lines;
            }
            catch (IOException e)
            {
                return new ArrayList<>();
            }
        }
        try
        {
            String baseURL = String.format("https://www.ariva.de/%s/historische_kurse", isin);
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
            try (FileWriter w = new FileWriter(dataFile, StandardCharsets.UTF_8))
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
                    String row = String.format("[\"%s\",\"%s\",\"%s\",%d,%s,%s,%s,%s,%s,%s]%n", isin, name, sISODate, timestamp, sFinish, sOpen, sHigh, sLow, sPeaces, sVolume);
                    w.write(row);
                    result.add(row);
                }
            }
            return result;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return result;
        }
    }


    public static long calcLastWorkDay()
    {
        LocalDateTime today = LocalDateTime.now();
        DayOfWeek day = DayOfWeek.of(today.get(ChronoField.DAY_OF_WEEK));
        if (day == DayOfWeek.SUNDAY)
        {
            return today.minusDays(2).toInstant(ZoneOffset.ofHours(1)).toEpochMilli();
        }
        else if (day == DayOfWeek.SATURDAY)
        { return today.minusDays(1).toInstant(ZoneOffset.ofHours(1)).toEpochMilli(); }
        return today.toInstant(ZoneOffset.ofHours(1)).toEpochMilli();
    }
}
