package com.trading.marketdata.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * One news item. Two producers with different field coverage:
 *   RSS scrape (Yahoo)  — headline, source, url, publishedAt; no article body reachable
 *                         (links are paywalled/bot-blocked for the highest-value outlets).
 *   IBKR News API       — headline, providerCode (e.g. BRFG, BRFUPDN, DJNL), articleId,
 *                         publishedAt, and — the whole point — fullText fetched via
 *                         reqNewsArticle over the existing Gateway socket. No url: IBKR
 *                         articles are not addressable outside the API, the text IS the
 *                         payload. fullText may lag the headline by a moment (async fetch)
 *                         or stay null when the article fetch failed.
 * source is "ibkr:<providerCode>" for IBKR items so downstream consumers can tell the
 * guarantee classes apart without a schema change.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NewsItem(
        String headline,
        String source,
        String url,
        Instant publishedAt,
        String providerCode,
        String articleId,
        String fullText
) {
    /** RSS/scraper items — the pre-IBKR shape, kept so existing call sites don't change. */
    public NewsItem(String headline, String source, String url, Instant publishedAt) {
        this(headline, source, url, publishedAt, null, null, null);
    }

    /** Same item with the article body attached (records are immutable). */
    public NewsItem withFullText(String text) {
        return new NewsItem(headline, source, url, publishedAt, providerCode, articleId, text);
    }
}
