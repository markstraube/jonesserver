package com.straube.jones.cmd.onVista;


import java.io.File;
import java.io.FileWriter;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class OnVistaHistoricData
{
    public static void main(String[] args)
        throws Exception
    {
        OnVistaHistoricData ariva = new OnVistaHistoricData("C:/Dev/__GIT/jonesserver/data/");
        String isin = "US0381691080"; // VW
        ariva.load("finanzenNet-historic.html", isin, "Applied Blockchain");
    }

    private final File rootFolder;
    private final DateFormat dfOnVista = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN);
    private final DateFormat dfISO = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMAN);

    public OnVistaHistoricData(String rootFolder)
    {
        this.rootFolder = new File(rootFolder);
        this.rootFolder.mkdirs();
    }


    public boolean load(String filename, String isin, String name)
    {
        File htmlFile = new File(rootFolder, filename);
        File dataFile = new File(rootFolder, isin + ".json");
        try
        {
            String response = FileUtils.readFileToString(htmlFile);
            Document doc = Jsoup.parse(response);
            Elements elRows = doc.select("body > div.table-responsive > table > tbody > tr");
            try (FileWriter w = new FileWriter(dataFile, Charset.forName("UTF-8")))
            {
                int size = elRows.size();
                for (int i=size-1; i>=0; i--)
                {
                    Element e = elRows.get(i);
                    Elements columns = e.getElementsByTag("td");
                    String sDate = columns.get(0).text();
                    String sOpen = columns.get(1).text().replace(".", "").replace(',', '.');
                    String sHigh = columns.get(2).text().replace(".", "").replace(',', '.');
                    String sLow = columns.get(3).text().replace(".", "").replace(',', '.');
                    String sFinish = columns.get(4).text().replace(".", "").replace(',', '.');
                    String sPeaces = "0"; //columns.get(5).text().replace(".", "");
                    String sVolume = columns.get(5).text().replace(".", "");
                    long timestamp = dfOnVista.parse(sDate).getTime() + 23 * 60 * 60 * 1000;
                    String sISODate = dfISO.format(new Date(timestamp));

                    w.write(String.format("[\"%s\",\"%s\",\"%s\",%d,%s,%s,%s,%s,%s,%s]\n", isin, name, sISODate, timestamp, sFinish, sOpen, sHigh, sLow, sPeaces, sVolume));
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
                return false;
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
