package com.straube.jones.cmd.currencies;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

public class CurrencyDB
{
    final static String dataRoot = System.getProperty("data.root", "./data");
    final static DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static JSONObject currencyDB;
    static
    {
        try
        {
            Path cdbPath = Paths.get(dataRoot, "onVista/eurorates/currencyDB.json");
            if (cdbPath.toFile().exists())
            {
                currencyDB = new JSONObject(new String(Files.readAllBytes(cdbPath)));
            }
            else
            {
                currencyDB = new JSONObject();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    static long OneDayMillis = 24 * 60 * 60 * 1000;

    public static Double getAsEuro(String currency, Double value, long dateMillis)
    {
        if ("EUR".equals(currency.toUpperCase()))
        { return value; }

        double rate = 1;
        long time = dateMillis;
        if (currencyDB.has(currency.toUpperCase()))
        {
            while (true)
            {
                Instant timeStamp = Instant.ofEpochMilli(time);
                ZonedDateTime zonedDate = timeStamp.atZone(ZoneId.systemDefault());
                DayOfWeek dayOfWeek = zonedDate.getDayOfWeek();
                if (dayOfWeek.equals(DayOfWeek.SUNDAY) || dayOfWeek.equals(DayOfWeek.SATURDAY))
                {
                    time = time - OneDayMillis;
                    continue;
                }
                String date = zonedDate.format(DF);
                if (!currencyDB.getJSONObject(currency.toUpperCase()).has(date))
                {
                    System.out.println(String.format("... corresponding date:%s not found for currency: %s -> moving one day back", date, currency.toUpperCase()));
                    time = time - OneDayMillis;
                    continue;
                }
                rate = currencyDB.getJSONObject(currency.toUpperCase()).getDouble(date);
                break;
            }
        }
        else
        {
            System.out.println(String.format("... unknown currency: %s -> skipping", currency));
        }
        return value / rate;
    }


    public static void update(long dateMillis, Map<String, Double> rates)
        throws JSONException,
        IOException
    {
        Instant timeStamp = Instant.ofEpochMilli(dateMillis);
        ZonedDateTime zonedDate = timeStamp.atZone(ZoneId.systemDefault());
        String date = zonedDate.format(DF);

        rates.forEach((currency, value) -> {
            if (currencyDB.has(currency))
            {
                currencyDB.getJSONObject(currency).put(date, value);
            }
        });
        Files.write(Paths.get(dataRoot, "onVista/eurorates/currencyDB.json"), currencyDB.toString(2).getBytes(), StandardOpenOption.CREATE);
    }


    /**
     * https://www.oenb.at/isawebstat/stabfrage/createReport;jsessionid=AB91D40801766A3CE43796947DF43A86?lang=DE&original=false&report=2.14.9
     * 
     * @param source
     * @return
     * @throws JSONException
     * @throws IOException
     */
    public static JSONObject importCSV(Path source)
        throws JSONException,
        IOException
    {
        String csvInput = new String(Files.readAllBytes(source));
        String colSeparator = ";";
        String recordSeparator = "\n";
        boolean firstLineIsHeader = true;

        JSONObject jsonCurrencies = new JSONObject();

        String[] records = csvInput.split(recordSeparator);

        String[] headers;
        if (firstLineIsHeader)
        {
            headers = records[0].split(colSeparator);
            records = java.util.Arrays.copyOfRange(records, 1, records.length);
        }
        else
        {
            headers = new String[records[0].split(colSeparator).length];
            for (int i = 0; i < headers.length; i++ )
            {
                headers[i] = "column" + (i + 1);
            }
        }
        for (String record : records)
        {
            String[] values = record.split(colSeparator);
            // 1,4,5 = 2020 03 19
            // 6 Referenzkurse der EZB EUR - HRK
            // 8 7,6080
            String currency = values[6].substring(values[6].length() - 3);
            if ("RY.".equals(currency)) // bug in CSV
            {
                currency = "TRY";
            }
            JSONObject jc;
            if (!jsonCurrencies.has(currency))
            {
                jc = new JSONObject();
                // jc.put("name", values[7]);
                jsonCurrencies.put(currency, jc);
            }
            else
            {
                jc = jsonCurrencies.getJSONObject(currency);
            }
            try
            {
                NumberFormat format = NumberFormat.getInstance(Locale.GERMANY);
                Number number = format.parse(values[8]);
                jc.put(String.format("%s-%s-%s", values[1], values[4], values[5]), number.doubleValue());
            }
            catch (ParseException e)
            {
                System.out.println(String.format("... skipping currency %s -> no quote.", values[6]));
            }
        }
        Files.write(Paths.get(dataRoot, "onVista/eurorates/currencyDB.json"), jsonCurrencies.toString(2).getBytes(), StandardOpenOption.CREATE);

        return jsonCurrencies;
    }


    public static void main(String[] args)
        throws Exception
    {
        CurrencyDB.importCSV(Paths.get("./data/onVista/eurorates/OeNB.csv"));
    }
}
