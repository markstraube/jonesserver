package com.straube.jones.service;


import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.jsoup.Jsoup;

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
    private static final String TRADEGATE_FINANCE_URL = "https://www.tradegatebsx.com/refresh.php?isin=";
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

        String cacheKey = calcCacheKey(isin);

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


    private String calcCacheKey(String isin)
    {
        // Cache-Key: ISIN_<(long)(Sekunden seit dem 1.1.2000 / 30)>
        long secondsSince2000 = (System.currentTimeMillis()
                        - java.sql.Timestamp.valueOf("2000-01-01 00:00:00").getTime()) / 1000L;
        long slice = secondsSince2000 / 60L;
        String cacheKey = isin + "_" + slice;
        return cacheKey;
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
            String jsonResponse = Jsoup.connect(url)
                                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                .timeout(TIMEOUT_MS)
                                .ignoreContentType(true)
                                .execute()
                                .body();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonResponse);

            BigDecimal bidPrice = parseGermanDecimal(root.path("bid"));
            BigDecimal askPrice = parseGermanDecimal(root.path("ask"));
            BigDecimal highPrice = parseGermanDecimal(root.path("high"));
            BigDecimal lowPrice = parseGermanDecimal(root.path("low"));
            BigDecimal lastPrice = parseGermanDecimal(root.path("last"));

            BigDecimal referencePrice = null;
            if (lastPrice != null)
            {
                referencePrice = BigDecimal.valueOf(Math.round(CurrencyDB.convertFromEuro("USD",
                                                                                         lastPrice.doubleValue(),
                                                                                         DayCounter.yesterday())
                                        * 100.0) / 100.0);
            }

            PriceEntry price = new PriceEntry(PriceEntry.PriceType.REGULAR,
                                              bidPrice,
                                              askPrice,
                                              highPrice,
                                              lowPrice,
                                              lastPrice,
                                              referencePrice,
                                              Instant.now().toString(),
                                              "tradegate");

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


    private BigDecimal parseGermanDecimal(JsonNode node)
    {
        if (node == null || node.isMissingNode() || node.isNull())
        {
            return null;
        }
        if (node.isNumber())
        {
            return node.decimalValue();
        }
        String text = node.asText();
        if (text == null || text.trim().isEmpty())
        {
            return null;
        }
        text = text.replace(".", "").replace(",", ".").replace(" ", "");
        
        try{
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
