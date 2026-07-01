package com.trading.marketdata.scraper;


import com.trading.marketdata.model.ShortData;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Scrapes short interest and fundamental data from Finviz.
 */
@Component
public class FinvizScraper
{

    private static final Logger log = LoggerFactory.getLogger(FinvizScraper.class);
    private static final String QUOTE_URL = "https://finviz.com/quote.ashx?t=%s&p=d";

    private static final java.util.List<String> USER_AGENTS = java.util.List.of("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
                                                                                "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15",
                                                                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:127.0) Gecko/20100101 Firefox/127.0",
                                                                                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
                                                                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Edge/125.0.0.0 Safari/537.36");

    @Value("${scraper.rate-limit-delay-min-ms:500}")
    private int minDelayMs;

    @Value("${scraper.rate-limit-delay-max-ms:1500}")
    private int maxDelayMs;

    public ShortData fetchShortData(String ticker)
    {
        randomDelay();
        try
        {
            String url = String.format(QUOTE_URL, ticker);
            Document doc = Jsoup.connect(url)
                                .userAgent(randomUserAgent())
                                .header("Accept",
                                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                                .header("Accept-Language", "en-US,en;q=0.5")
                                .header("Referer", "https://finviz.com/")
                                .timeout(5000)
                                .get();

            // Finviz uses a snapshot table — find cells by label
            Double shortFloat = findMetric(doc, "Short Float");
            Double shortRatio = findMetric(doc, "Short Ratio");
            Double instOwn = findMetric(doc, "Inst Own");
            Double insiderOwn = findMetric(doc, "Insider Own");
            Long shortShares = findLongMetric(doc, "Short Interest");

            // Compute days-to-cover = shortShares / avgVolume (approx via shortRatio if available)
            Double daysToCover = shortRatio; // Finviz Short Ratio IS days-to-cover

            return new ShortData(ticker,
                                 shortFloat,
                                 shortShares,
                                 daysToCover,
                                 shortRatio,
                                 instOwn,
                                 insiderOwn,
                                 "finviz",
                                 null,
                                 true,
                                 Instant.now());
        }
        catch (Exception e)
        {
            throw new ScraperException("finviz", "Failed to fetch short data for " + ticker, e);
        }
    }


    public Double fetchPutCallRatio(String ticker)
    {
        randomDelay();
        try
        {
            String url = String.format(QUOTE_URL, ticker);
            Document doc = Jsoup.connect(url)
                                .userAgent(randomUserAgent())
                                .header("Accept",
                                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                                .header("Accept-Language", "en-US,en;q=0.5")
                                .header("Referer", "https://finviz.com/")
                                .timeout(5000)
                                .get();

            // Finviz label ist "P/C Ratio" — ggf. auch "Option/Short" prüfen
            Double putCallRatio = findMetric(doc, "P/C Ratio");
            if (putCallRatio == null)
            {
                log.debug("P/C Ratio not found for {} — trying 'Perf Month'", ticker);
            }
            log.debug("Finviz P/C Ratio for {}: {}", ticker, putCallRatio);
            return putCallRatio;

        }
        catch (Exception e)
        {
            throw new ScraperException("finviz", "Failed to fetch P/C Ratio for " + ticker, e);
        }
    }


    /**
     * Finds a metric value in the Finviz snapshot table by its label text. Finviz renders a table of
     * td[class=snapshot-td2-cp] pairs: label | value
     */
    Double findMetric(Document doc, String label)
    {
        // Try the standard Finviz layout (pairs of label-value cells)
        Elements rows = doc.select("table.snapshot-table2 tr, table[class*=snapshot] tr");
        for (Element row : rows)
        {
            Elements cells = row.select("td");
            for (int i = 0; i < cells.size() - 1; i++ )
            {
                if (cells.get(i).text().trim().equalsIgnoreCase(label))
                { return parsePercent(cells.get(i + 1).text().trim()); }
            }
        }
        // Fallback: search all td pairs
        Elements allTds = doc.select("td");
        for (int i = 0; i < allTds.size() - 1; i++ )
        {
            if (allTds.get(i).text().trim().equalsIgnoreCase(label))
            { return parsePercent(allTds.get(i + 1).text().trim()); }
        }
        return null;
    }


    Long findLongMetric(Document doc, String label)
    {
        Elements allTds = doc.select("td");
        for (int i = 0; i < allTds.size() - 1; i++ )
        {
            if (allTds.get(i).text().trim().equalsIgnoreCase(label))
            { return parseLong(allTds.get(i + 1).text().trim()); }
        }
        return null;
    }


    private Double parsePercent(String s)
    {
        if (s == null || s.isBlank() || s.equals("-"))
            return null;
        try
        {
            return Double.parseDouble(s.replaceAll("[%,]", "").trim());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }


    private Long parseLong(String s)
    {
        if (s == null || s.isBlank() || s.equals("-"))
            return null;
        try
        {
            String clean = s.replaceAll("[,]", "").trim();
            if (clean.endsWith("M"))
                return (long)(Double.parseDouble(clean.replace("M", "")) * 1_000_000);
            if (clean.endsWith("K"))
                return (long)(Double.parseDouble(clean.replace("K", "")) * 1_000);
            return Long.parseLong(clean.replaceAll("[^0-9]", ""));
        }
        catch (Exception e)
        {
            return null;
        }
    }


    private void randomDelay()
    {
        try
        {
            long delay = ThreadLocalRandom.current().nextLong(minDelayMs, maxDelayMs + 1);
            Thread.sleep(delay);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }


    private String randomUserAgent()
    {
        return USER_AGENTS.get(ThreadLocalRandom.current().nextInt(USER_AGENTS.size()));
    }
}
