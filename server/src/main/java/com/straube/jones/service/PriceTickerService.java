package com.straube.jones.service;


import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import com.straube.jones.dto.PriceEntry;
import com.straube.jones.dto.PriceTickerResponse;

/**
 * Service for retrieving stock price information from Yahoo Finance.
 * Scrapes Yahoo Finance HTML pages to extract current, pre-market, and after-market prices.
 * Uses stable data-testid attributes instead of volatile CSS classes for robustness.
 */
public class PriceTickerService
{
    private static final String TRADEGATE_FINANCE_URL = "https://www.tradegate.de/orderbuch.php?isin=";
    private static final int TIMEOUT_MS = 10000;

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

        return fetchPriceFromTradegate(isin);
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
            askValue = askValue.replace(".", "").replace(",", ".").replace(" ", "") ;
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
