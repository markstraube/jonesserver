package com.straube.jones.dataprovider.eurorates;


import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.json.JSONObject;

import com.straube.jones.db.DayCounter;

public class CurrencyDB
{
    final static String DATA_ROOT = System.getProperty("data.root", "/home/mark/Software/data");
    final static DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static JSONObject currencyDB;
    static
    {
        try
        {
            Path cdbPath = Paths.get(DATA_ROOT, "onVista/eurorates/currencyDB.json");
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

    public static Double getAsEuroOrOriginal(String currency, Double value, long dayCounter, boolean convertToEuro)
    {
        if (!convertToEuro)
        { return value; }
        else
        {
            return getAsEuro(currency, value, dayCounter);
        }
    }

    public static Double getAsEuro(String currency, Double value, long dayCounter)
    {
        if ("EUR".equals(currency.toUpperCase()))
        { return value; }

        double rate = 1;
        long time = DayCounter.toTimestamp(dayCounter);
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
                    //System.out.println(String.format("... corresponding date:%s not found for currency: %s -> moving one day back", date, currency.toUpperCase()));
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

    public static Double convertFromEuro(String currencyTo, Double value, long dayCounter)
    {
        if ("EUR".equals(currencyTo.toUpperCase()))
        { return value; }

        double rate = 1;
        long time = DayCounter.toTimestamp(dayCounter);
        if (currencyDB.has(currencyTo.toUpperCase()))
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
                if (!currencyDB.getJSONObject(currencyTo.toUpperCase()).has(date))
                {
                    //System.out.println(String.format("... corresponding date:%s not found for currency: %s -> moving one day back", date, currencyTo.toUpperCase()));
                    time = time - OneDayMillis;
                    continue;
                }
                rate = currencyDB.getJSONObject(currencyTo.toUpperCase()).getDouble(date);
                break;
            }
        }
        else
        {
            System.out.println(String.format("... unknown currency: %s -> skipping", currencyTo));
        }
        return value * rate;
    }

    public static Double convert(String currencyFrom, String currencyTo, Double value, long dayCounter)
    {
        if (currencyFrom.toUpperCase().equals(currencyTo.toUpperCase()))
        { return value; }

        double rate = 1;
        long time = DayCounter.toTimestamp(dayCounter);
        if (currencyDB.has(currencyFrom.toUpperCase()) && currencyDB.has(currencyTo.toUpperCase()))
        {
            double rateFrom = 1;
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
                if (!currencyDB.getJSONObject(currencyFrom.toUpperCase()).has(date))
                {
                    //System.out.println(String.format("... corresponding date:%s not found for currency: %s -> moving one day back", date, currencyFrom.toUpperCase()));
                    time = time - OneDayMillis;
                    continue;
                }
                rateFrom = currencyDB.getJSONObject(currencyFrom.toUpperCase()).getDouble(date);
                break;
            }
            time = DayCounter.toTimestamp(dayCounter);
            double rateTo = 1;
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
                if (!currencyDB.getJSONObject(currencyTo.toUpperCase()).has(date))
                {
                    //System.out.println(String.format("... corresponding date:%s not found for currency: %s -> moving one day back", date, currencyTo.toUpperCase()));
                    time = time - OneDayMillis;
                    continue;
                }
                rateTo = currencyDB.getJSONObject(currencyTo.toUpperCase()).getDouble(date);
                break;
            }
            rate = rateFrom / rateTo;
        }
        return value / rate;
    }
}
