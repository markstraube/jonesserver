package com.trading.marketdata.news.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NewsTickerNormalizerTest {

    @Test
    void canonicalizesKnownAliasesAndDeduplicates() {
        assertEquals(List.of("MU", "SKHY", "SPCX"), NewsTickerNormalizer.normalizeAll(List.of(
                "MU", "sk hynix", "000660.KS", "KR:000660", "SpaceX", "SPXC")));
    }

    @Test
    void normalizesPersistedCsvProjection() {
        assertEquals("MU,SKHY,SNDK", NewsTickerNormalizer.normalizeCsv(
                "SK HYNIX,SKHY,000660.KS,MU,SNDK"));
    }
}
