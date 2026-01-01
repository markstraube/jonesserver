package com.straube.jones.db;


import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public class DayCounter
{

    private static final LocalDate REFERENCE_DATE = LocalDate.of(2000, 1, 1);

    private DayCounter()
    {}


    public static long get(long timestamp)
    {
        return get(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate());
    }


    public static long get(String dateString)
    {
        return get(dateString == null ? REFERENCE_DATE : LocalDate.parse(dateString));
    }


    public static long get(LocalDate date)
    {
        return ChronoUnit.DAYS.between(REFERENCE_DATE, date);
    }


    public static long now()
    {
        return get(LocalDate.now());
    }


    public static long before(long days)
    {
        return get(LocalDate.now().minusDays(days));
    }

    public static long toTimestamp(long dayCount)
    {
        LocalDate date = REFERENCE_DATE.plusDays(dayCount);
        return date.atStartOfDay(ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli();
    }
}
