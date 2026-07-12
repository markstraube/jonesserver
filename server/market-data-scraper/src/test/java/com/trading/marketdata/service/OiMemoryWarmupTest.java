package com.trading.marketdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Warm-up core: the JSON→cache-key path. The keys MUST be byte-identical to what the live
 * scanner writes (shared contractOiKey), otherwise the warmed memory is invisible to the
 * read side — asserted here by reading back through the exact producer-format key.
 */
class OiMemoryWarmupTest {

    private ConcurrentMapCache cache;
    private OiMemoryWarmup warmup;
    private static final LocalDate DAY = LocalDate.of(2026, 7, 9);

    @BeforeEach
    void setUp() {
        cache = new ConcurrentMapCache("oiContractDayMemory");
        warmup = new OiMemoryWarmup(null, null, new ObjectMapper());
    }

    @Test
    void oiProfileWarmsCallAndPutSeparately() {
        String oiProfile = """
                [{"expiry":"20260710","strike":1000.0,"callOpenInterest":11610,"putOpenInterest":4200}]""";
        int written = warmup.warmFromSnapshot(cache, "MU", DAY, oiProfile, null);

        assertEquals(2, written);
        assertEquals(11610L, cache.get(
                OptionActivityService.contractOiKey("MU", "20260710", 1000.0, "C", DAY), Long.class));
        assertEquals(4200L, cache.get(
                OptionActivityService.contractOiKey("MU", "20260710", 1000.0, "P", DAY), Long.class));
    }

    @Test
    void uaEntriesActAsSafetyNet() {
        String ua = """
                [{"expiry":"20260710","strike":970.0,"type":"CALL","openInterest":883,
                  "volume":12691,"aggressorProfile":{"status":"OK","someFutureField":1}}]""";
        int written = warmup.warmFromSnapshot(cache, "MU", DAY, null, ua);

        assertEquals(1, written);
        assertEquals(883L, cache.get(
                OptionActivityService.contractOiKey("MU", "20260710", 970.0, "C", DAY), Long.class));
    }

    @Test
    void putIfAbsentNeverClobbersLiveValues() {
        String key = OptionActivityService.contractOiKey("MU", "20260710", 1000.0, "C", DAY);
        cache.put(key, 99_999L); // a live scan already wrote today
        String oiProfile = """
                [{"expiry":"20260710","strike":1000.0,"callOpenInterest":11610,"putOpenInterest":null}]""";

        int written = warmup.warmFromSnapshot(cache, "MU", DAY, oiProfile, null);

        assertEquals(0, written); // call blocked by existing entry, put is null
        assertEquals(99_999L, cache.get(key, Long.class));
    }

    @Test
    void malformedAndMissingJsonIsNonFatal() {
        assertEquals(0, warmup.warmFromSnapshot(cache, "MU", DAY, "not json at all", null));
        assertEquals(0, warmup.warmFromSnapshot(cache, "MU", DAY, null, ""));
        assertEquals(0, warmup.warmFromSnapshot(cache, "MU", DAY, "{\"anObject\":1}", null));
        // Entries missing expiry/strike are skipped, valid siblings still land
        String mixed = """
                [{"strike":100.0,"callOpenInterest":5},
                 {"expiry":"20260717","strike":100.0,"callOpenInterest":7,"putOpenInterest":3}]""";
        assertEquals(2, warmup.warmFromSnapshot(cache, "MU", DAY, mixed, null));
    }
}
