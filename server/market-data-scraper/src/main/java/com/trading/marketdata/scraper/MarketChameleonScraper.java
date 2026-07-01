package com.trading.marketdata.scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Scrapes IV Rank, IV Percentile and historical volatility from MarketChameleon.
 * Results are returned as a simple record for use by OptionsService.
 */
@Component
public class MarketChameleonScraper {

    private static final Logger log = LoggerFactory.getLogger(MarketChameleonScraper.class);
    private static final String OVERVIEW_URL = "https://marketchameleon.com/Overview/%s/";

    private static final java.util.List<String> USER_AGENTS = java.util.List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:127.0) Gecko/20100101 Firefox/127.0",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Edge/125.0.0.0 Safari/537.36"
    );

    @Value("${scraper.rate-limit-delay-min-ms:500}")
    private int minDelayMs;

    @Value("${scraper.rate-limit-delay-max-ms:1500}")
    private int maxDelayMs;

    public record IvMetrics(Double ivRank, Double ivPercentile, Double historicalVolatility) {}

    public IvMetrics fetchIvMetrics(String ticker) {
        randomDelay();
        try {
            String url = String.format(OVERVIEW_URL, ticker);
            Document doc = Jsoup.connect(url)
                    .userAgent(randomUserAgent())
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Referer", "https://www.google.com/")
                    .timeout(10000)
                    .get();

            log.info("MarketChameleon page for {}: title='{}', bodyLen={}", ticker,
                     doc.title(), doc.body() != null ? doc.body().text().length() : 0);

            Double ivRank = extractLabeledValue(doc, "IV Rank", "iv rank", "ivrank");
            Double ivPercentile = extractLabeledValue(doc, "IV Percentile", "iv percentile", "ivpct");
            Double hv = extractLabeledValue(doc, "HV 30", "30-day hv", "historical vol");

            log.debug("MarketChameleon extracted for {}: ivRank={}, ivPercentile={}, hv={}", ticker, ivRank, ivPercentile, hv);
            return new IvMetrics(ivRank, ivPercentile, hv);
        } catch (Exception e) {
            log.warn("MarketChameleon scrape failed for {}: {}", ticker, e.getMessage());
            return new IvMetrics(null, null, null);
        }
    }

    /**
     * Searches for a metric by any of the given label texts (case-insensitive) in
     * the page's table cells and data-label attributes.
     */
    private Double extractLabeledValue(Document doc, String... labels) {
        // Try data-label attributes
        for (String label : labels) {
            Elements elements = doc.select("[data-label*='" + label + "'], [aria-label*='" + label + "']");
            for (var el : elements) {
                Double val = parseDouble(el.text());
                if (val != null) return val;
            }
        }

        // Try table label-value pairs
        Elements tds = doc.select("td, th, div[class*=stat], span[class*=stat]");
        for (int i = 0; i < tds.size() - 1; i++) {
            String cellText = tds.get(i).text().trim().toLowerCase();
            for (String label : labels) {
                if (cellText.equals(label.toLowerCase()) || cellText.contains(label.toLowerCase())) {
                    Double val = parseDouble(tds.get(i + 1).text());
                    if (val != null) return val;
                }
            }
        }
        return null;
    }

    private Double parseDouble(String s) {
        if (s == null || s.isBlank() || s.equals("-") || s.equals("N/A")) return null;
        try {
            return Double.parseDouble(s.replaceAll("[%,$,\\s]", "").trim());
        } catch (NumberFormatException e) {
            return null;
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
