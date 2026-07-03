package com.trading.marketdata.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.marketdata.model.NewsItem;
import com.trading.marketdata.model.QuoteData;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Scrapes quote data from Yahoo Finance.
 * Primary: v8 JSON API. Fallback: HTML scraping.
 */
@Component
public class YahooFinanceScraper {

    private static final Logger log = LoggerFactory.getLogger(YahooFinanceScraper.class);

    private static final String API_URL      = "https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&range=1d&includePrePost=true";
    private static final String OPTIONS_URL  = "https://query1.finance.yahoo.com/v7/finance/options/%s";
    private static final String QUOTE_URL    = "https://finance.yahoo.com/quote/%s/";
    private static final String NEWS_URL     = "https://finance.yahoo.com/rss/headline?s=%s";

    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:127.0) Gecko/20100101 Firefox/127.0",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Edge/125.0.0.0 Safari/537.36"
    );

    private static final Duration CRUMB_TTL = Duration.ofMinutes(20);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final ReentrantLock crumbLock = new ReentrantLock();

    private volatile String cachedCrumb;
    private volatile Map<String, String> cachedCookies;
    private volatile Instant crumbExpiry = Instant.EPOCH;

    @Value("${scraper.rate-limit-delay-min-ms:500}")
    private int minDelayMs;

    @Value("${scraper.rate-limit-delay-max-ms:1500}")
    private int maxDelayMs;

    public YahooFinanceScraper(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public record OptionsMetrics(Double putCallRatio, Double maxPain) {}

    public OptionsMetrics fetchOptionsMetrics(String ticker) {
        randomDelay();
        try {
            String userAgent = randomUserAgent();
            String crumb = getOrRefreshCrumb(userAgent);
            if (crumb == null) {
                return new OptionsMetrics(null, null);
            }
            Map<String, String> cookies = cachedCookies;

            // Step 3: Options-Chain mit Crumb abrufen
            String optionsUrl = String.format(
                    "https://query2.finance.yahoo.com/v7/finance/options/%s?crumb=%s",
                    ticker, java.net.URLEncoder.encode(crumb, java.nio.charset.StandardCharsets.UTF_8));

            org.jsoup.Connection.Response optResp = Jsoup.connect(optionsUrl)
                    .userAgent(userAgent)
                    .cookies(cookies)
                    .header("Accept", "application/json, text/plain, */*")
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .timeout(10000)
                    .execute();

            if (optResp.statusCode() == 401 || optResp.statusCode() == 403) {
                log.warn("Yahoo Finance options: {} für {} — Crumb-Cache wird invalidiert", optResp.statusCode(), ticker);
                crumbExpiry = Instant.EPOCH;
                return new OptionsMetrics(null, null);
            }
            if (optResp.statusCode() != 200) {
                log.warn("Yahoo Finance options: HTTP {} für {}", optResp.statusCode(), ticker);
                return new OptionsMetrics(null, null);
            }

            JsonNode results = objectMapper.readTree(optResp.body()).path("optionChain").path("result");
            if (!results.isArray() || results.isEmpty()) {
                log.warn("Yahoo Finance options: kein Ergebnis für {}", ticker);
                return new OptionsMetrics(null, null);
            }

            JsonNode optionsArr = results.get(0).path("options");
            if (!optionsArr.isArray() || optionsArr.isEmpty()) {
                return new OptionsMetrics(null, null);
            }

            long totalCallVol = 0, totalPutVol = 0;
            Map<Double, Long> callOi = new HashMap<>();
            Map<Double, Long> putOi  = new HashMap<>();

            for (JsonNode expiry : optionsArr) {
                for (JsonNode c : expiry.path("calls")) {
                    totalCallVol += c.path("volume").asLong(0);
                    double s = c.path("strike").asDouble(0);
                    if (s > 0) callOi.merge(s, c.path("openInterest").asLong(0), (a, b) -> a + b);
                }
                for (JsonNode p : expiry.path("puts")) {
                    totalPutVol += p.path("volume").asLong(0);
                    double s = p.path("strike").asDouble(0);
                    if (s > 0) putOi.merge(s, p.path("openInterest").asLong(0), (a, b) -> a + b);
                }
            }

            Double putCallRatio = totalCallVol > 0 ? (double) totalPutVol / totalCallVol : null;
            Double maxPain = calculateMaxPain(callOi, putOi);

            log.info("Yahoo Finance options für {}: putCallRatio={}, maxPain={}", ticker, putCallRatio, maxPain);
            return new OptionsMetrics(putCallRatio, maxPain);

        } catch (Exception e) {
            log.warn("Yahoo Finance options failed for {}: {}", ticker, e.getMessage());
            return new OptionsMetrics(null, null);
        }
    }

    private String getOrRefreshCrumb(String userAgent) {
        if (cachedCrumb != null && Instant.now().isBefore(crumbExpiry)) {
            return cachedCrumb;
        }
        crumbLock.lock();
        try {
            // Doppeltes Check nach dem Lock
            if (cachedCrumb != null && Instant.now().isBefore(crumbExpiry)) {
                return cachedCrumb;
            }
            log.info("Yahoo Finance: hole neuen Crumb...");

            org.jsoup.Connection.Response consent = Jsoup.connect("https://fc.yahoo.com/")
                    .userAgent(userAgent)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .timeout(10000)
                    .execute();
            Map<String, String> cookies = new HashMap<>(consent.cookies());

            org.jsoup.Connection.Response crumbResp = Jsoup.connect("https://query2.finance.yahoo.com/v1/test/getcrumb")
                    .userAgent(userAgent)
                    .cookies(cookies)
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .timeout(10000)
                    .execute();
            cookies.putAll(crumbResp.cookies());
            String crumb = crumbResp.body().trim();

            if (crumb == null || crumb.isBlank() || crumb.length() > 50 || crumbResp.statusCode() != 200) {
                log.warn("Yahoo Finance: Crumb fehlgeschlagen (status={}, body='{}')",
                         crumbResp.statusCode(), crumb);
                return null;
            }
            cachedCrumb = crumb;
            cachedCookies = cookies;
            crumbExpiry = Instant.now().plus(CRUMB_TTL);
            log.info("Yahoo Finance: Crumb gecacht für {} Minuten", CRUMB_TTL.toMinutes());
            return crumb;
        } catch (Exception e) {
            log.warn("Yahoo Finance: Crumb-Abruf fehlgeschlagen: {}", e.getMessage());
            return null;
        } finally {
            crumbLock.unlock();
        }
    }

    private Double calculateMaxPain(Map<Double, Long> callOi, Map<Double, Long> putOi) {
        List<Double> strikes = new ArrayList<>(callOi.keySet());
        putOi.keySet().stream().filter(s -> !strikes.contains(s)).forEach(strikes::add);
        if (strikes.isEmpty()) return null;

        double minPain = Double.MAX_VALUE;
        Double maxPainStrike = null;
        for (double candidate : strikes) {
            double pain = 0;
            for (Map.Entry<Double, Long> e : callOi.entrySet()) {
                if (candidate > e.getKey()) pain += (candidate - e.getKey()) * e.getValue();
            }
            for (Map.Entry<Double, Long> e : putOi.entrySet()) {
                if (candidate < e.getKey()) pain += (e.getKey() - candidate) * e.getValue();
            }
            if (pain < minPain) { minPain = pain; maxPainStrike = candidate; }
        }
        return maxPainStrike;
    }

    public QuoteData fetchQuote(String ticker) {
        randomDelay();
        String ua = randomUserAgent();
        try {
            return fetchViaApi(ticker, ua);
        } catch (Exception e) {
            log.warn("Yahoo API failed for {}, falling back to HTML scraping: {}", ticker, e.getMessage());
            try {
                return fetchViaHtml(ticker, ua);
            } catch (Exception ex) {
                throw new ScraperException("yahoo_finance", "All Yahoo sources failed for " + ticker, ex);
            }
        }
    }

    private QuoteData fetchViaApi(String ticker, String userAgent) {
        String url = String.format(API_URL, ticker);
        String body = webClient.get()
                .uri(url)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .block();

        if (body == null || body.isBlank()) {
            throw new ScraperException("yahoo_finance", "Empty response from Yahoo API for " + ticker);
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode result = root.path("chart").path("result").get(0);
            if (result == null || result.isMissingNode()) {
                throw new ScraperException("yahoo_finance", "No chart result in Yahoo API response for " + ticker);
            }

            JsonNode meta = result.path("meta");
            double price = meta.path("regularMarketPrice").asDouble(Double.NaN);
            double prevClose = meta.path("chartPreviousClose").asDouble(Double.NaN);
            double open = meta.path("regularMarketOpen").asDouble(Double.NaN);
            long volume = meta.path("regularMarketVolume").asLong(0L);

            double change = (Double.isNaN(price) || Double.isNaN(prevClose)) ? Double.NaN : price - prevClose;
            double changePct = (Double.isNaN(change) || prevClose == 0) ? Double.NaN : (change / prevClose) * 100.0;

            String marketCap = Optional.ofNullable(meta.get("marketCap"))
                    .filter(n -> !n.isNull())
                    .map(n -> formatMarketCap(n.asLong()))
                    .orElse(null);

            String marketState = meta.path("marketState").asText(null);
            Double preMarketPrice    = nanToNull(meta.path("preMarketPrice").asDouble(Double.NaN));
            Double preMarketChangePct = nanToNull(meta.path("preMarketChangePercent").asDouble(Double.NaN));
            Double postMarketPrice   = nanToNull(meta.path("postMarketPrice").asDouble(Double.NaN));
            Double postMarketChangePct = nanToNull(meta.path("postMarketChangePercent").asDouble(Double.NaN));

            return new QuoteData(
                    ticker,
                    nanToNull(price),
                    nanToNull(change),
                    nanToNull(changePct),
                    nanToNull(open),
                    nanToNull(meta.path("regularMarketDayHigh").asDouble(Double.NaN)),
                    nanToNull(meta.path("regularMarketDayLow").asDouble(Double.NaN)),
                    volume == 0 ? null : volume,
                    null,   // avgVolume not in v8
                    null,   // volumeRatio computed later
                    marketCap,
                    null,   // P/E not in v8 chart endpoint
                    null,
                    null,
                    marketState,
                    preMarketPrice,
                    preMarketChangePct,
                    postMarketPrice,
                    postMarketChangePct,
                    "yahoo_finance",
                    null,
                    true,
                    Instant.now()
            );
        } catch (ScraperException se) {
            throw se;
        } catch (Exception e) {
            throw new ScraperException("yahoo_finance", "Failed to parse Yahoo API response for " + ticker, e);
        }
    }

    private QuoteData fetchViaHtml(String ticker, String userAgent) {
        try {
            Document doc = Jsoup.connect(String.format(QUOTE_URL, ticker))
                    .userAgent(userAgent)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(5000)
                    .get();

            // Try multiple selectors for robustness against DOM changes
            Double price = parseDouble(
                    firstText(doc, "[data-field='regularMarketPrice']",
                            "fin-streamer[data-field='regularMarketPrice']",
                            "span[data-testid='qsp-price']"));
            Double changePct = parseDouble(
                    firstText(doc, "[data-field='regularMarketChangePercent']",
                            "fin-streamer[data-field='regularMarketChangePercent']"));

            return new QuoteData(
                    ticker, price, null, changePct,
                    null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null,
                    "yahoo_finance_html", null, price != null, Instant.now()
            );
        } catch (Exception e) {
            throw new ScraperException("yahoo_finance_html", "HTML scraping failed for " + ticker, e);
        }
    }

    public List<NewsItem> fetchNews(String ticker, int limit) {
        randomDelay();
        List<NewsItem> items = new ArrayList<>();
        try {
            String url = String.format(NEWS_URL, ticker);
            String body = webClient.get()
                    .uri(url)
                    .header("User-Agent", randomUserAgent())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .onErrorReturn("")
                    .block();

            if (body == null || body.isBlank()) return items;

            Document doc = Jsoup.parse(body, "", org.jsoup.parser.Parser.xmlParser());
            doc.select("item").stream().limit(limit).forEach(item -> {
                String headline = item.select("title").text();
                String link = item.select("link").text();
                if (link.isBlank()) link = item.select("link").attr("href");
                String pubDateStr = item.select("pubDate").text();
                Instant publishedAt = parsePubDate(pubDateStr);
                items.add(new NewsItem(headline, "Yahoo Finance RSS", link, publishedAt));
            });
        } catch (Exception e) {
            log.warn("Failed to fetch Yahoo news for {}: {}", ticker, e.getMessage());
        }
        return items;
    }

    // --- helpers ---

    private String firstText(Document doc, String... selectors) {
        for (String sel : selectors) {
            String text = doc.select(sel).text();
            if (!text.isBlank()) return text.replaceAll("[,%]", "").trim();
        }
        return null;
    }

    private Double parseDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Double.parseDouble(s.replaceAll("[^0-9.\\-]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double nanToNull(double d) {
        return Double.isNaN(d) ? null : d;
    }

    private String formatMarketCap(long cap) {
        if (cap >= 1_000_000_000_000L) return String.format("%.1fT", cap / 1_000_000_000_000.0);
        if (cap >= 1_000_000_000L) return String.format("%.1fB", cap / 1_000_000_000.0);
        if (cap >= 1_000_000L) return String.format("%.1fM", cap / 1_000_000.0);
        return String.valueOf(cap);
    }

    private Instant parsePubDate(String s) {
        if (s == null || s.isBlank()) return Instant.now();
        try {
            return Instant.from(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.parse(s));
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private void randomDelay() {
        try {
            long delay = ThreadLocalRandom.current().nextLong(minDelayMs, maxDelayMs + 1);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String randomUserAgent() {
        return USER_AGENTS.get(ThreadLocalRandom.current().nextInt(USER_AGENTS.size()));
    }
}
