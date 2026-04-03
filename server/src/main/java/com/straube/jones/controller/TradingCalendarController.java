package com.straube.jones.controller;

import com.straube.jones.service.TradingCalendarService;
import com.straube.jones.service.TradingCalendarService.TodayHolidayInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller exposing the trading-day calendar for Tradegate and Nasdaq.
 */
@RestController
@RequestMapping("/api/trading-calendar")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Trading Calendar API", description = "Exchange holiday information for Tradegate and Nasdaq")
public class TradingCalendarController
{
    private final TradingCalendarService tradingCalendarService;

    public TradingCalendarController(TradingCalendarService tradingCalendarService)
    {
        this.tradingCalendarService = tradingCalendarService;
    }

    /**
     * Returns holiday status for today (Europe/Berlin timezone).
     * The response indicates whether Tradegate and/or Nasdaq are closed today
     * and, if so, provides the holiday name.
     *
     * <pre>
     * {
     *   "date": "2026-04-03",
     *   "tradegate": { "holiday": true,  "name": "Karfreitag" },
     *   "nasdaq":    { "holiday": true,  "name": "Good Friday" }
     * }
     * </pre>
     */
    @GetMapping("/today")
    @Operation(
        summary = "Holiday status for today",
        description = "Returns whether today is a Tradegate and/or Nasdaq exchange holiday. "
                    + "The date is evaluated in the Europe/Berlin timezone. "
                    + "If a field is null, the exchange is open on that day."
    )
    public ResponseEntity<Map<String, Object>> getToday()
    {
        TodayHolidayInfo info = tradingCalendarService.getTodayHolidayInfo();
        return ResponseEntity.ok(buildResponse(info.getDate(), info.getTradegateHoliday(), info.getNasdaqHoliday()));
    }

    /**
     * Returns holiday status for an arbitrary date (ISO format: yyyy-MM-dd).
     */
    @GetMapping("/date")
    @Operation(
        summary = "Holiday status for a specific date",
        description = "Returns whether the given date (ISO format yyyy-MM-dd) is a Tradegate and/or Nasdaq holiday."
    )
    public ResponseEntity<Map<String, Object>> getByDate(
        @RequestParam String date)
    {
        LocalDate localDate;
        try
        {
            localDate = LocalDate.parse(date);
        }
        catch (Exception e)
        {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Invalid date format. Use yyyy-MM-dd");
            return ResponseEntity.badRequest().body(error);
        }

        String tradegate = tradingCalendarService.getTradegateHolidayName(localDate);
        String nasdaq    = tradingCalendarService.getNasdaqHolidayName(localDate);
        return ResponseEntity.ok(buildResponse(localDate, tradegate, nasdaq));
    }

    // -------------------------------------------------------------------------

    private static Map<String, Object> buildResponse(LocalDate date, String tradegate, String nasdaq)
    {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("date", date.toString());

        Map<String, Object> tradegateInfo = new LinkedHashMap<>();
        tradegateInfo.put("holiday", tradegate != null);
        tradegateInfo.put("name", tradegate);
        response.put("tradegate", tradegateInfo);

        Map<String, Object> nasdaqInfo = new LinkedHashMap<>();
        nasdaqInfo.put("holiday", nasdaq != null);
        nasdaqInfo.put("name", nasdaq);
        response.put("nasdaq", nasdaqInfo);

        return response;
    }
}
