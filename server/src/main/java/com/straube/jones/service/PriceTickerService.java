package com.straube.jones.service;


import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import com.straube.jones.dataprovider.eurorates.CurrencyDB;
import com.straube.jones.db.DayCounter;
import com.straube.jones.dto.PriceEntry;
import com.straube.jones.dto.PriceTickerResponse;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.TimeUnit;

/**
 * Service for retrieving stock price information from Yahoo Finance. Scrapes Yahoo Finance HTML pages to
 * extract current, pre-market, and after-market prices. Uses stable data-testid attributes instead of
 * volatile CSS classes for robustness.
 */
public class PriceTickerService
{
    private static final String TRADEGATE_FINANCE_URL = "https://www.tradegate.de/orderbuch.php?isin=";
    private static final int TIMEOUT_MS = 10000;

    // Guava Cache: Key = ISIN_<(long)(Sekunden seit 1.1.2000 / 30)>, Value = PriceEntry
    private static final Cache<String, PriceEntry> priceCache = CacheBuilder.newBuilder()
                                                                            .expireAfterWrite(5,
                                                                                              TimeUnit.MINUTES)
                                                                            .build();

    /**
     * Retrieves current price ticker information for a stock by ISIN.
     * 
     * @param isin The ISIN of the stock
     * @return PriceTickerResponse containing all available price information
     * @throws IllegalArgumentException if ISIN is invalid or cannot be resolved
     * @throws IOException if Tradegate cannot be reached or parsed
     */
    public PriceTickerResponse getPriceByIsinFromTradegate(String isin)
        throws IOException
    {
        if (isin == null || isin.trim().isEmpty())
        { throw new IllegalArgumentException("ISIN cannot be null or empty"); }

        // Cache-Key: ISIN_<(long)(Sekunden seit dem 1.1.2000 / 30)>
        long secondsSince2000 = (System.currentTimeMillis()
                        - java.sql.Timestamp.valueOf("2000-01-01 00:00:00").getTime()) / 1000L;
        long slice = secondsSince2000 / 30L;
        String cacheKey = isin + "_" + slice;

        PriceEntry cached = priceCache.getIfPresent(cacheKey);
        if (cached != null)
        {
            PriceTickerResponse response = new PriceTickerResponse(isin, "EUR");
            List<PriceEntry> prices = new ArrayList<>();
            prices.add(cached);
            response.setPrices(prices);
            return response;
        }

        PriceTickerResponse fresh = fetchPriceFromTradegate(isin);
        if (fresh.getPrices() != null && !fresh.getPrices().isEmpty())
        {
            priceCache.put(cacheKey, fresh.getPrices().get(0));
        }
        return fresh;
    }


    /**
     * Fetches and parses price information from Tradegate.
     */
    private PriceTickerResponse fetchPriceFromTradegate(String isin)
        throws IOException
    {
        String url = TRADEGATE_FINANCE_URL + isin;

        try
        {
            Document doc = Jsoup.connect(url)
                                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                .timeout(TIMEOUT_MS)
                                .get();

            Elements bid = doc.select("#bid");
            if (bid.isEmpty())
            { throw new IOException("No price blocks found on Tradegate page"); }
            String bidValue = bid.first().text();
            bidValue = bidValue.replace(".", "").replace(",", ".").replace(" ", "");
            BigDecimal bidPrice = new BigDecimal(bidValue);

            Elements ask = doc.select("#ask");
            String askValue = ask.first().text();
            askValue = askValue.replace(".", "").replace(",", ".").replace(" ", "");
            BigDecimal askPrice = new BigDecimal(askValue);

            Elements high = doc.select("#high");
            String highValue = high.first().text();
            highValue = highValue.replace(".", "").replace(",", ".").replace(" ", "");
            BigDecimal highPrice = new BigDecimal(highValue);

            Elements low = doc.select("#low");
            String lowValue = low.first().text();
            lowValue = lowValue.replace(".", "").replace(",", ".").replace(" ", "");
            BigDecimal lowPrice = new BigDecimal(lowValue);

            Elements last = doc.select("#last");
            String lastValue = last.first().text();
            lastValue = lastValue.replace(".", "").replace(",", ".").replace(" ", "");
            BigDecimal lastPrice = new BigDecimal(lastValue);

            PriceEntry price = new PriceEntry(PriceEntry.PriceType.REGULAR,
                                              bidPrice,
                                              askPrice,
                                              highPrice,
                                              lowPrice,
                                              lastPrice,
                                              BigDecimal.valueOf(Math.round(CurrencyDB.convertFromEuro("USD",
                                                                                                       lastPrice.doubleValue(),
                                                                                                       DayCounter.yesterday())
                                                              * 100.0) / 100.0),
                                              Instant.now().toString(),
                                              "tradegate");

            // Parse price blocks
            List<PriceEntry> prices = new ArrayList<>();
            prices.add(price);

            PriceTickerResponse response = new PriceTickerResponse(isin, "EUR");
            response.setPrices(prices);

            return response;
        }
        catch (IOException e)
        {
            throw new IOException("Failed to fetch data from Tradegate: " + e.getMessage(), e);
        }
    }
}
