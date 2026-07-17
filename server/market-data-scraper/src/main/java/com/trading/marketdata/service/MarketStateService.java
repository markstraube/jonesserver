package com.trading.marketdata.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Determines the current US equity market state (OVERNIGHT / PRE / REGULAR / POST / CLOSED / UNKNOWN)
 * via the crumb-free Yahoo v8 chart endpoint, using SPY as the market proxy so the answer
 * is independent of any individual ticker's quirks.
 *
 * Primary source is the "marketState" field in the chart meta when Yahoo provides it;
 * otherwise the state is computed from meta.currentTradingPeriod (pre/regular/post epoch
 * windows), which also covers holidays: on a non-trading day "now" falls outside all
 * windows of the most recent session and the state resolves to CLOSED.
 *
 * Fail-open by design: any error yields UNKNOWN — callers must treat UNKNOWN as "no
 * statement", never as CLOSED, so a Yahoo outage cannot suppress persistence of good data.
 * Result is cached for 60 seconds to keep the endpoint out of the hot path.
 */
@Service
public class MarketStateService {

    private static final Logger log = LoggerFactory.getLogger(MarketStateService.class);
    private static final String CHART_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/SPY?interval=1d&range=1d&includePrePost=true";
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    public enum MarketState { OVERNIGHT, PRE, REGULAR, POST, CLOSED, UNKNOWN }

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private volatile MarketState cachedState = MarketState.UNKNOWN;
    private volatile Instant cachedAt = Instant.EPOCH;

    public MarketStateService(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public MarketState getMarketState() {
        Instant now = Instant.now();
        if (now.isBefore(cachedAt.plus(CACHE_TTL))) {
            return cachedState;
        }
        MarketState fresh = fetchState(now);
        cachedState = fresh;
        cachedAt = now;
        return fresh;
    }

    private MarketState fetchState(Instant now) {
        try {
            String body = webClient.get()
                    .uri(CHART_URL)
                    .header("User-Agent", "Mozilla/5.0")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(4));
            if (body == null) return MarketState.UNKNOWN;

            JsonNode meta = objectMapper.readTree(body)
                    .path("chart").path("result").path(0).path("meta");
            if (meta.isMissingNode()) return MarketState.UNKNOWN;

            // Preferred: Yahoo's own verdict when present (e.g. "PRE", "REGULAR", "POST",
            // "CLOSED", "PREPRE", "POSTPOST"). Prefixes map onto our coarser enum.
            String yahooState = meta.path("marketState").asText(null);
            if (yahooState != null) {
                if (yahooState.equals("PREPRE") || yahooState.equals("POSTPOST")) {
                    return MarketState.OVERNIGHT;
                }
                if (yahooState.startsWith("PRE")) return MarketState.PRE;
                if (yahooState.equals("REGULAR")) return MarketState.REGULAR;
                if (yahooState.startsWith("POST")) return MarketState.POST;
                // Yahoo/SPY can report CLOSED while IBKR still supplies overnight trading
                // for eligible US equities. Resolve that scheduled session explicitly.
                if (yahooState.equals("CLOSED")) {
                    return isOvernightWindow(now) ? MarketState.OVERNIGHT : MarketState.CLOSED;
                }
            }

            // Fallback: compute from the most recent session's trading windows.
            JsonNode period = meta.path("currentTradingPeriod");
            long epoch = now.getEpochSecond();
            if (inWindow(period.path("regular"), epoch)) return MarketState.REGULAR;
            if (inWindow(period.path("pre"), epoch)) return MarketState.PRE;
            if (inWindow(period.path("post"), epoch)) return MarketState.POST;
            if (!period.isMissingNode()) {
                return isOvernightWindow(now) ? MarketState.OVERNIGHT : MarketState.CLOSED;
            }
            return MarketState.UNKNOWN;
        } catch (Exception e) {
            log.warn("Market state lookup failed ({}), treating as UNKNOWN", e.getMessage());
            return MarketState.UNKNOWN;
        }
    }

    /**
     * IBKR overnight session for eligible US equities: approximately 20:00-04:00 ET,
     * Sunday evening through Friday morning. This identifies the active trading session;
     * it does not claim that the primary exchange itself is open.
     */
    static boolean isOvernightWindow(Instant instant) {
        ZonedDateTime ny = instant.atZone(NEW_YORK);
        DayOfWeek day = ny.getDayOfWeek();
        LocalTime time = ny.toLocalTime();
        boolean evening = !time.isBefore(LocalTime.of(20, 0));
        boolean earlyMorning = time.isBefore(LocalTime.of(4, 0));

        if (evening) {
            return day == DayOfWeek.SUNDAY || day == DayOfWeek.MONDAY
                    || day == DayOfWeek.TUESDAY || day == DayOfWeek.WEDNESDAY
                    || day == DayOfWeek.THURSDAY;
        }
        if (earlyMorning) {
            return day == DayOfWeek.MONDAY || day == DayOfWeek.TUESDAY
                    || day == DayOfWeek.WEDNESDAY || day == DayOfWeek.THURSDAY
                    || day == DayOfWeek.FRIDAY;
        }
        return false;
    }

    private boolean inWindow(JsonNode window, long epoch) {
        if (window.isMissingNode()) return false;
        long start = window.path("start").asLong(Long.MAX_VALUE);
        long end = window.path("end").asLong(Long.MIN_VALUE);
        return epoch >= start && epoch < end;
    }
}
