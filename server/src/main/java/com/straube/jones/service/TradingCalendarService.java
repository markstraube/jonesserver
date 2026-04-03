package com.straube.jones.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Service that provides a trading-day calendar for Tradegate and Nasdaq exchanges.
 * Holiday dates are loaded once from {@code trading-calendar.json} in the classpath.
 */
@Service
public class TradingCalendarService
{
    private static final String CALENDAR_RESOURCE = "trading-calendar.json";

    /** Map from ISO date string (yyyy-MM-dd) → holiday name, for Tradegate. */
    private Map<LocalDate, String> tradegateHolidays = new HashMap<>();

    /** Map from ISO date string (yyyy-MM-dd) → holiday name, for Nasdaq. */
    private Map<LocalDate, String> nasdaqHolidays = new HashMap<>();

    @PostConstruct
    public void loadCalendar()
    {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = new ClassPathResource(CALENDAR_RESOURCE).getInputStream())
        {
            JsonNode root = mapper.readTree(is);
            tradegateHolidays = parseHolidays(root.get("tradegate"));
            nasdaqHolidays    = parseHolidays(root.get("nasdaq"));
        }
        catch (IOException e)
        {
            System.err.println("TradingCalendarService: Failed to load " + CALENDAR_RESOURCE + ": " + e.getMessage());
        }
    }

    private static Map<LocalDate, String> parseHolidays(JsonNode array)
    {
        Map<LocalDate, String> map = new HashMap<>();
        if (array != null && array.isArray())
        {
            for (JsonNode entry : array)
            {
                String dateStr = entry.path("date").asText();
                String name    = entry.path("name").asText();
                if (!dateStr.isEmpty())
                {
                    map.put(LocalDate.parse(dateStr), name);
                }
            }
        }
        return map;
    }

    // -------------------------------------------------------------------------
    // Public query API
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the given date is a Tradegate exchange holiday.
     */
    public boolean isTradegateHoliday(LocalDate date)
    {
        return tradegateHolidays.containsKey(date);
    }

    /**
     * Returns {@code true} if the given date is a Nasdaq exchange holiday.
     */
    public boolean isNasdaqHoliday(LocalDate date)
    {
        return nasdaqHolidays.containsKey(date);
    }

    /**
     * Returns the human-readable holiday name for the given Tradegate holiday date,
     * or {@code null} if the date is not a holiday.
     */
    public String getTradegateHolidayName(LocalDate date)
    {
        return tradegateHolidays.get(date);
    }

    /**
     * Returns the human-readable holiday name for the given Nasdaq holiday date,
     * or {@code null} if the date is not a holiday.
     */
    public String getNasdaqHolidayName(LocalDate date)
    {
        return nasdaqHolidays.get(date);
    }

    /**
     * Convenience: returns holiday info for today (Europe/Berlin timezone)
     * as a {@link TodayHolidayInfo} value object.
     */
    public TodayHolidayInfo getTodayHolidayInfo()
    {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Europe/Berlin"));
        return new TodayHolidayInfo(
            today,
            getTradegateHolidayName(today),
            getNasdaqHolidayName(today)
        );
    }

    // -------------------------------------------------------------------------
    // Value object returned by the controller
    // -------------------------------------------------------------------------

    public static class TodayHolidayInfo
    {
        private final LocalDate date;
        private final String tradegateHoliday;
        private final String nasdaqHoliday;

        public TodayHolidayInfo(LocalDate date, String tradegateHoliday, String nasdaqHoliday)
        {
            this.date             = date;
            this.tradegateHoliday = tradegateHoliday;
            this.nasdaqHoliday    = nasdaqHoliday;
        }

        public LocalDate getDate()              { return date; }
        public String getTradegateHoliday()     { return tradegateHoliday; }
        public String getNasdaqHoliday()        { return nasdaqHoliday; }
        public boolean isTradegateHoliday()     { return tradegateHoliday != null; }
        public boolean isNasdaqHoliday()        { return nasdaqHoliday != null; }
    }
}
