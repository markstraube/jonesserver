package com.trading.marketdata.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import com.trading.marketdata.news.model.NewsContext;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MarketSnapshot(
        String ticker,
        Instant timestamp,
        String marketState,
        QuoteData quote,
        OptionsData options,
        ShortData shortData,
        List<NewsItem> news,
        NewsContext newsContext,
        DerivedMetrics derived,
        AuctionData auction,
        DataQuality dataQuality // additive; only present for Book symbols
) {}
