package com.straube.jones.cmd.misc.ariva;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import com.straube.jones.cmd.html.HttpTools;

public class ArivaHistoricData
{
    private static final String COL_SEPARATOR = "\t";
    private static final String LINE_SEPARATOR = "\n";
    public final File rootFolder;

    DateTimeFormatter arivaFormatter = DateTimeFormatter.ofPattern("dd.MM.yy");
    private final DateFormat dfISO = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMAN);

    public static void main(String[] args)
    {
        ArivaHistoricData ariva = new ArivaHistoricData("C:/Dev/__GIT/jonesserver/data");
        ariva.preFetch("DK0010244425");

    }


    public ArivaHistoricData(String rootFolder)
    {
        this.rootFolder = new File(rootFolder, "ariva/historic");
        this.rootFolder.mkdirs();
    }


    public List<String> getData(String isin)
    {
        preFetch(isin);
        List<String> data = new ArrayList<>();
        Path dirName = Path.of(rootFolder.getAbsolutePath(), isin);

        List<File> files = listFilesInDirectory(dirName);
        files.forEach((file) -> {
            try
            {
                List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
                data.addAll(lines);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        });
        return data;
    }


    public List<File> listFilesInDirectory(Path directoryPath)
    {
        File[] files = directoryPath.toFile().listFiles((dir, name) -> name.endsWith(".json"));

        if (files == null)
        { return new ArrayList<>(); }

        Arrays.sort(files);
        return Arrays.asList(files);
    }


    public File preFetch(String isin)
    {
        File isinRootFolder = new File(rootFolder, isin);
        if (!isinRootFolder.exists())
        {
            isinRootFolder.mkdirs();
        }
        try
        {
            for (int offset = 11; offset >= 0; offset-- )
            {
                String dateSelector = getYearMonthLastDayOfMonthFromNow(offset);
                File dataFile = new File(isinRootFolder, String.format("%s-%s.json", dateSelector, isin));
                if (dataFile.exists() && !forceReload(dataFile, offset))
                {
                    continue;
                }
                String baseURL = String.format("https://www.ariva.de/%s/kurse/historische-kurse?go=1&boerse_id=1&month=%s&currency=EUR&clean_split=1&clean_bezug=1", isin, dateSelector);
                String response = HttpTools.downloadFromWebToString(baseURL);
                Document doc = Jsoup.parse(response);
                Elements elSecu = doc.select("#pageHistoricQuotes > div.column.twothirds > div.abstand.new > table > tbody > tr");
                final StringBuilder historic = new StringBuilder();
                if (elSecu != null && elSecu.size() > 1)
                {
                    elSecu.forEach(row -> {
                        row.children().forEach(col -> {
                            historic.append(col.text()).append(COL_SEPARATOR);
                        });
                        historic.append(LINE_SEPARATOR);
                    });
                }
                // from: Datum Erster Hoch Tief Schluss * Stücke Volumen
                // 18.10.23 95,67 $ 95,25 $ 94,61 $ 94,70 $ * 1.131.015 108 M $
                // to: ["DE0006916604","Pfeiffer Vacuum Technology","2020-06-15T17:35:00",1592235300000,150.6]
                String[] lines = historic.toString().split(LINE_SEPARATOR);
                StringBuilder bldr = new StringBuilder();
                for (int i = lines.length - 1; i > 0; i-- )
                {
                    String[] segs = lines[i].split(COL_SEPARATOR);
                    if (segs.length < 8)
                    {
                        continue;
                    }
                    String sDate = segs[0];
                    String sOpen = segs[1].replace(".", "").replace(',', '.');
                    String sHigh = segs[2].replace(".", "").replace(',', '.');
                    String sLow = segs[3].replace(".", "").replace(',', '.');
                    String sFinish = segs[4].replace(".", "").replace(',', '.');
                    String sPeaces = segs[6].replace(".", "");
                    String sVolume = segs[7].replace(".", "");
                    try
                    {
                        LocalDate localDate = LocalDate.parse(sDate, arivaFormatter);
                        Instant instant = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
                        Long timestamp = instant.toEpochMilli() + 23 * 60 * 60 * 1000;

                        String sISODate = dfISO.format(new Date(timestamp));
                        String row = String.format("[\"%s\",\"%s\",\"%s\",%d,%s,%s,%s,%s,%s,%s]%n",
                                                   isin,
                                                   isin,
                                                   sISODate,
                                                   timestamp,
                                                   sFinish.split(" ")[0],
                                                   sOpen.split(" ")[0],
                                                   sHigh.split(" ")[0],
                                                   sLow.split(" ")[0],
                                                   sPeaces,
                                                   sVolume);
                        bldr.append(row);
                    }
                    catch (Exception ignore)
                    {
                        ignore.printStackTrace();
                    }
                }
                try (FileWriter w = new FileWriter(dataFile, StandardCharsets.UTF_8, false))
                {
                    w.write(bldr.toString());
                }
            }
        }
        catch (Exception ignore)
        {
            ignore.printStackTrace();
        }
        return null;
    }


    private boolean forceReload(File dataFile, int offset)
    {
        // always reload data if offset is 0 (= current month)
        if (offset == 0 && dataFile.lastModified() < System.currentTimeMillis() - 24 * 60 * 60 * 1000)
        {
            try
            {
                Files.deleteIfExists(dataFile.toPath());
                return true;
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return false;
    }


    private String getYearMonthLastDayOfMonthFromNow(int offset)
    {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        while (offset > month)
        {
            year-- ;
            offset = offset - month;
            month = 12;
        }
        // Monate in der Calendar-Klasse sind von 0 (Januar) bis 11 (Dezember)
        calendar.set(year, month - offset, 1);
        return String.format("%s-%02d-%s", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
    }
}
