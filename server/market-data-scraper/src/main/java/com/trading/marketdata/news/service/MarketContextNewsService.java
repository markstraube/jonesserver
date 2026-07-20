package com.trading.marketdata.news.service;

import com.trading.marketdata.model.NewsItem;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects non-ticker macro/market news that can materially move AI and semiconductor stocks.
 *
 * The normal NewsService is intentionally ticker-centric. That misses causal market events such
 * as wars, oil shocks, rates/inflation surprises, trade restrictions and export controls when the
 * source article is not tagged to MU/SNDK/AMD/etc. This service supplies a small, cached macro feed
 * that is ingested into the same story/classification/condensation pipeline under the synthetic
 * source scope {@code MACRO}.
 */
@Service
public class MarketContextNewsService {
    public static final String MACRO_SCOPE = "MACRO";

    private static final Logger log = LoggerFactory.getLogger(MarketContextNewsService.class);
    private static final String GOOGLE_NEWS_RSS =
            "https://news.google.com/rss/search?q=%s&hl=en-US&gl=US&ceid=US:en";

    private final WebClient webClient;

    @Value("${news.market-context.enabled:true}")
    boolean enabled;
    @Value("${news.market-context.cache-seconds:300}")
    long cacheSeconds;
    @Value("${news.market-context.max-items-per-query:8}")
    int maxItemsPerQuery;
    @Value("${news.market-context.queries:Iran war oil Hormuz semiconductor AI|geopolitics oil inflation yields technology stocks|chip export controls China semiconductors AI|Federal Reserve rates inflation technology stocks}")
    String configuredQueries;

    private volatile Instant cacheExpiresAt = Instant.EPOCH;
    private volatile List<NewsItem> cached = List.of();

    public MarketContextNewsService(WebClient webClient) {
        this.webClient = webClient;
    }

    public List<NewsItem> getNews() {
        if (!enabled) return List.of();
        Instant now = Instant.now();
        if (now.isBefore(cacheExpiresAt)) return cached;
        synchronized (this) {
            now = Instant.now();
            if (now.isBefore(cacheExpiresAt)) return cached;
            cached = fetchAll();
            cacheExpiresAt = now.plusSeconds(Math.max(30, cacheSeconds));
            return cached;
        }
    }

    private List<NewsItem> fetchAll() {
        Map<String, NewsItem> deduped = new LinkedHashMap<>();
        for (String query : configuredQueries.split("\\|")) {
            String trimmed = query.trim();
            if (trimmed.isEmpty()) continue;
            try {
                fetchQuery(trimmed).forEach(item -> deduped.putIfAbsent(normalize(item.headline()), item));
            } catch (Exception e) {
                log.warn("MARKET_CONTEXT_NEWS_FAILED query='{}' message={}", trimmed, e.getMessage());
            }
        }
        List<NewsItem> result = new ArrayList<>(deduped.values());
        result.sort((a, b) -> b.publishedAt().compareTo(a.publishedAt()));
        log.info("MARKET_CONTEXT_NEWS_FETCH items={} queries={}", result.size(), configuredQueries.split("\\|").length);
        return List.copyOf(result);
    }

    private List<NewsItem> fetchQuery(String query) {
        String encoded = URLEncoder.encode(query + " when:3d", StandardCharsets.UTF_8);
        String xml = webClient.get()
                .uri(String.format(GOOGLE_NEWS_RSS, encoded))
                .header("User-Agent", "Mozilla/5.0")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(8))
                .onErrorReturn("")
                .block();
        if (xml == null || xml.isBlank()) return List.of();

        Document doc = Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser());
        List<NewsItem> items = new ArrayList<>();
        doc.select("item").stream().limit(Math.max(1, maxItemsPerQuery)).forEach(item -> {
            String headline = item.select("title").text();
            if (headline.isBlank()) return;
            String link = item.select("link").text();
            if (link.isBlank()) link = item.select("link").attr("href");
            Instant publishedAt = parsePubDate(item.select("pubDate").text());
            items.add(new NewsItem(headline, "Google News RSS / Market Context", link, publishedAt));
        });
        return items;
    }

    private static Instant parsePubDate(String value) {
        if (value == null || value.isBlank()) return Instant.now();
        try {
            return Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(value));
        } catch (Exception ignored) {
            return Instant.now();
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
