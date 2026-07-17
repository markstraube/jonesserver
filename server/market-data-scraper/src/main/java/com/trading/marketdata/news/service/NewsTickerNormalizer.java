package com.trading.marketdata.news.service;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class NewsTickerNormalizer {
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("SK HYNIX", "SKHY"),
            Map.entry("SKHYNIX", "SKHY"),
            Map.entry("000660.KS", "SKHY"),
            Map.entry("KR:000660", "SKHY"),
            Map.entry("000660", "SKHY"),
            Map.entry("SPACEX", "SPCX"),
            Map.entry("SPXC", "SPCX")
    );

    public String normalize(String raw) {
        if (raw == null) return null;
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (value.isBlank()) return null;
        return ALIASES.getOrDefault(value, value);
    }

    public List<String> normalizeAll(Collection<String> rawTickers) {
        if (rawTickers == null || rawTickers.isEmpty()) return List.of();
        return rawTickers.stream()
                .map(this::normalize)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    public String normalizeCsv(String csv) {
        if (csv == null || csv.isBlank()) return null;
        List<String> normalized = normalizeAll(Arrays.asList(csv.split(",")));
        return normalized.isEmpty() ? null : String.join(",", normalized);
    }
}
