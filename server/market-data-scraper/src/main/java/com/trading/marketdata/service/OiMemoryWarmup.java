package com.trading.marketdata.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.marketdata.persistence.SnapshotEntity;
import com.trading.marketdata.persistence.SnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuilds the {@code oiContractDayMemory} cache from persisted snapshots at startup.
 *
 * Why this exists: the memory is Caffeine — pure in-memory — with a 6-day TTL, and it feeds
 * the OI-delta join behind positionInference, the single most valuable derived signal. A
 * 5-minute restart would otherwise wipe up to 6 days of accumulated per-contract OI and
 * degrade positionInference to UNKNOWN for DAYS while the memory re-accumulates. The raw
 * material survives every restart in MySQL: each persisted snapshot carries the scanned OI
 * window (oiProfileJson: per strike, calls and puts separately) and the flagged contracts
 * (unusualActivityJson: per contract). One warm-up pass turns "restart costs a week of
 * inference" into "restart costs one scan cycle".
 *
 * Mechanics: a lightweight (id, ticker, ts) index query over the lookback window, the LAST
 * snapshot id per (ticker, ET-day) selected in Java (OI is struck once daily — any snapshot
 * of the day carries the same figures; the last one has the day's final scanned strike
 * window), a batch load of just those rows, JSON parsed defensively as trees (no coupling
 * to the evolving record shapes), keys built by the SAME method the scanner uses
 * (OptionActivityService.contractOiKey), and putIfAbsent so anything a live scan already
 * wrote today wins. Known limitation, accepted: strikes that drifted out of the scan window
 * intraday are only present in earlier snapshots of that day and are not warmed — they sit
 * far from the current spot, where the next scans rarely look.
 *
 * Failure is non-fatal by design: no DB, no warm-up, the scraper runs — the memory then
 * accumulates organically exactly as it did before this class existed.
 */
@Component
public class OiMemoryWarmup {

    private static final Logger log = LoggerFactory.getLogger(OiMemoryWarmup.class);
    private static final ZoneId ET = ZoneId.of("America/New_York");

    private final SnapshotRepository repository;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;

    @Value("${oi.memory.warmup-enabled:true}")
    boolean enabled = true;

    /** Aligned with the cache TTL (6d) and the read side's 5-day lookback + today. */
    @Value("${oi.memory.warmup-lookback-days:6}")
    int lookbackDays = 6;

    public OiMemoryWarmup(SnapshotRepository repository, CacheManager cacheManager,
                          ObjectMapper objectMapper) {
        this.repository = repository;
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!enabled) return;
        Cache cache = cacheManager.getCache("oiContractDayMemory");
        if (cache == null) return;
        try {
            long t0 = System.nanoTime();
            List<Long> ids = lastSnapshotIdPerTickerDay();
            int entries = 0;
            int rows = 0;
            for (SnapshotEntity snapshot : repository.findAllById(ids)) {
                rows++;
                LocalDate day = snapshot.getSnapshotTs().atZone(ET).toLocalDate();
                entries += warmFromSnapshot(cache, snapshot.getTicker(), day,
                        snapshot.getOiProfileJson(), snapshot.getUnusualActivityJson());
            }
            log.info("OI_MEMORY_WARMUP rows={} entries={} lookbackDays={} tookMs={}",
                    rows, entries, lookbackDays, (System.nanoTime() - t0) / 1_000_000);
        } catch (Exception e) {
            // Non-fatal by design: the scraper must come up without a DB; the memory then
            // simply accumulates organically as it did before warm-up existed.
            log.warn("OI_MEMORY_WARMUP failed (non-fatal, memory accumulates organically): {}",
                    e.getMessage());
        }
    }

    /** Last persisted snapshot id per (ticker, ET trading day) in the lookback window —
     *  chosen over the index projection without touching the JSON columns. */
    private List<Long> lastSnapshotIdPerTickerDay() {
        Instant from = ZonedDateTime.now(ET).minusDays(lookbackDays).toInstant();
        Map<String, Long> lastPerKey = new HashMap<>(); // "ticker|date" → id (rows arrive ts-ascending)
        for (Object[] row : repository.findIdIndexSince(from)) {
            Long id = (Long) row[0];
            String ticker = (String) row[1];
            LocalDate day = ((Instant) row[2]).atZone(ET).toLocalDate();
            lastPerKey.put(ticker + "|" + day, id); // ascending order → last write wins
        }
        return new ArrayList<>(lastPerKey.values());
    }

    /**
     * Parses one snapshot's OI JSON and warms the cache for its (ticker, day). Trees, not
     * record deserialization: the JSON shapes gain fields over time (aggressorProfile,
     * enrichment) and the warm-up needs exactly four of them — tree extraction cannot be
     * broken by additive schema evolution. putIfAbsent: same-day values a live scan already
     * wrote are identical anyway; never clobber fresher state on principle.
     *
     * @return number of cache entries written
     */
    int warmFromSnapshot(Cache cache, String ticker, LocalDate day,
                         String oiProfileJson, String unusualActivityJson) {
        int written = 0;
        // oiProfile: the scanned window — one row per strike, calls and puts separately.
        for (JsonNode level : parseArray(oiProfileJson)) {
            String expiry = text(level, "expiry");
            Double strike = number(level, "strike");
            if (expiry == null || strike == null) continue;
            written += putIfAbsent(cache, ticker, expiry, strike, "C", longOf(level, "callOpenInterest"), day);
            written += putIfAbsent(cache, ticker, expiry, strike, "P", longOf(level, "putOpenInterest"), day);
        }
        // UA entries: usually a subset of the profile, but kept as a safety net for flagged
        // contracts at the window's edge.
        for (JsonNode ua : parseArray(unusualActivityJson)) {
            String expiry = text(ua, "expiry");
            Double strike = number(ua, "strike");
            Long oi = longOf(ua, "openInterest");
            if (expiry == null || strike == null || oi == null) continue;
            String right = "PUT".equals(text(ua, "type")) ? "P" : "C";
            written += putIfAbsent(cache, ticker, expiry, strike, right, oi, day);
        }
        return written;
    }

    private int putIfAbsent(Cache cache, String ticker, String expiry, double strike,
                            String right, Long oi, LocalDate day) {
        if (oi == null) return 0;
        String key = OptionActivityService.contractOiKey(ticker, expiry, strike, right, day);
        return cache.putIfAbsent(key, oi) == null ? 1 : 0;
    }

    private Iterable<JsonNode> parseArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.isArray() ? node : List.of();
        } catch (Exception e) {
            return List.of(); // one malformed row must not abort the whole warm-up
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Double number(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || !v.isNumber() ? null : v.asDouble();
    }

    private static Long longOf(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || !v.isNumber() ? null : v.asLong();
    }
}
