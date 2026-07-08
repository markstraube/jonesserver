package com.trading.marketdata.scraper;

import com.trading.marketdata.model.OptionsData;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Scrapes options data (Put/Call Ratio, IV Rank, Max Pain, unusual activity) from Barchart.
 */
@Component
public class BarchartScraper {

    private static final Logger log = LoggerFactory.getLogger(BarchartScraper.class);
    private static final String OPTIONS_URL = "https://www.barchart.com/stocks/quotes/%s/options";
    private static final String UNUSUAL_URL = "https://www.barchart.com/stocks/quotes/%s/unusual-activity";

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

    public OptionsData fetchOptionsData(String ticker) {
        randomDelay();
        try {
            String url = String.format(OPTIONS_URL, ticker);
            Document doc = Jsoup.connect(url)
                    .userAgent(randomUserAgent())
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .header("Referer", "https://www.barchart.com/")
                    .timeout(5000)
                    .get();

            Double putCallRatio = extractPutCallRatio(doc);
            Double ivRank = extractIvRank(doc);
            Double maxPain = extractMaxPain(doc);
            List<OptionsData.UnusualActivity> unusual = fetchUnusualActivity(ticker);

            return new OptionsData(
                    ticker, putCallRatio, ivRank, null, null, null,
                    unusual, null, maxPain, "barchart", null, true, Instant.now()
            );
        } catch (ScraperException se) {
            throw se;
        } catch (Exception e) {
            throw new ScraperException("barchart", "Failed to fetch options data for " + ticker, e);
        }
    }

    private Double extractPutCallRatio(Document doc) {
        // Barchart shows P/C ratio in various table or stat formats
        for (Element el : doc.select("div.bc-futures-options-quotes-totals__item, div[class*=put-call], span[class*=put-call]")) {
            String text = el.text().toLowerCase();
            if (text.contains("put") && text.contains("call")) {
                String val = el.select("span, strong").text().trim();
                Double d = parseDouble(val);
                if (d != null) return d;
            }
        }
        // Generic fallback: look for "Put/Call" label in any table
        Elements tds = doc.select("td");
        for (int i = 0; i < tds.size() - 1; i++) {
            String label = tds.get(i).text().toLowerCase();
            if (label.contains("put/call") || label.contains("p/c ratio")) {
                return parseDouble(tds.get(i + 1).text());
            }
        }
        return null;
    }

    private Double extractIvRank(Document doc) {
        Elements tds = doc.select("td, div[class*=iv-rank], span[class*=iv-rank]");
        for (int i = 0; i < tds.size() - 1; i++) {
            String label = tds.get(i).text().toLowerCase();
            if (label.contains("iv rank") || label.contains("iv percentile")) {
                return parseDouble(tds.get(i + 1).text());
            }
        }
        return null;
    }

    private Double extractMaxPain(Document doc) {
        Elements tds = doc.select("td");
        for (int i = 0; i < tds.size() - 1; i++) {
            String label = tds.get(i).text().toLowerCase();
            if (label.contains("max pain") || label.contains("maximum pain")) {
                Double val = parseDouble(tds.get(i + 1).text());
                log.debug("Barchart maxPain candidate: label='{}' value='{}' parsed={}", 
                         tds.get(i).text(), tds.get(i + 1).text(), val);
                if (val != null && val > 0.5) return val;
            }
        }
        for (Element el : doc.select("span[class*=max-pain], div[class*=max-pain], td[class*=max-pain]")) {
            Double val = parseDouble(el.text());
            if (val != null && val > 0.5) return val;
        }
        return null;
    }

    private List<OptionsData.UnusualActivity> fetchUnusualActivity(String ticker) {
        List<OptionsData.UnusualActivity> result = new ArrayList<>();
        try {
            randomDelay();
            String url = String.format(UNUSUAL_URL, ticker);
            Document doc = Jsoup.connect(url)
                    .userAgent(randomUserAgent())
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Referer", "https://www.barchart.com/")
                    .timeout(5000)
                    .get();

            // Table rows for unusual activity
            Elements rows = doc.select("table tbody tr, div[class*=unusual] div[class*=row]");
            for (Element row : rows) {
                Elements cells = row.select("td, div[class*=cell]");
                if (cells.size() < 6) continue;

                String expiry = cells.get(1).text().trim();
                Double strike = parseDouble(cells.get(2).text());
                String type = cells.get(3).text().trim().toUpperCase();
                Long volume = parseLong(cells.get(4).text());
                Long oi = parseLong(cells.get(5).text());
                Double iv = cells.size() > 6 ? parseDouble(cells.get(6).text()) : null;

                Double volOiRatio = (volume != null && oi != null && oi > 0)
                        ? (double) volume / oi
                        : null;

                // Barchart delivers no per-contract bid/ask/last and therefore no premium
                // notional and no aggressor classification — those fields exist only on the
                // IBKR path (OptionActivityService). UNKNOWN is the honest label here: no
                // quote to locate the last print against, so no direction is claimed.
                result.add(new OptionsData.UnusualActivity(
                        expiry, strike, type, volume, oi, volOiRatio,
                        null, null, null, null, iv, null, "UNKNOWN", null
                ));
                if (result.size() >= 10) break;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch unusual activity for {}: {}", ticker, e.getMessage());
        }
        return result;
    }

    private Double parseDouble(String s) {
        if (s == null || s.isBlank() || s.equals("-")) return null;
        try {
            return Double.parseDouble(s.replaceAll("[%,$,]", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(String s) {
        if (s == null || s.isBlank() || s.equals("-")) return null;
        try {
            String clean = s.replaceAll("[,]", "").trim();
            if (clean.endsWith("K")) return (long)(Double.parseDouble(clean.replace("K","")) * 1_000);
            if (clean.endsWith("M")) return (long)(Double.parseDouble(clean.replace("M","")) * 1_000_000);
            return Long.parseLong(clean.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
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
