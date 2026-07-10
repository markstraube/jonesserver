package com.trading.marketdata.service;

import com.trading.marketdata.book.MarketDataBook;
import com.trading.marketdata.book.TickerBook;
import com.trading.marketdata.model.QuoteData;
import com.trading.marketdata.scraper.ScraperException;
import com.trading.marketdata.scraper.YahooFinanceScraper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

    private final YahooFinanceScraper yahooFinanceScraper;
    private final MarketDataBook book;
    private final MarketStateService marketStateService;
    private final CacheManager cacheManager;

    @Value("${book.ticker-stale-seconds:120}")
    private long tickerStaleSeconds;

    public QuoteService(YahooFinanceScraper yahooFinanceScraper,
                        MarketDataBook book,
                        MarketStateService marketStateService,
                        CacheManager cacheManager) {
        this.yahooFinanceScraper = yahooFinanceScraper;
        this.book = book;
        this.marketStateService = marketStateService;
        this.cacheManager = cacheManager;
    }

    /**
     * Quote priority:
     *   1. MarketDataBook (permanent IBKR streams for watchlist + anchor) — synchronous
     *      in-memory read, never triggers an IBKR request. Book reads are NOT cached: the
     *      Book is fresher than any cache could be.
     *   2. Yahoo Finance (non-Book tickers, Book empty, or Book stale during REGULAR) —
     *      cached 60s as before.
     *   3. Stale Book data as honest last resort when Yahoo also fails — better an old
     *      price flagged by its own timestamps than none.
     *
     * Staleness is market-state-aware: silence in PRE/POST/CLOSED is stillness, not a dead
     * feed, so only REGULAR silence beyond the threshold (or a lost connection) demotes the
     * Book below Yahoo.
     */
    public QuoteData getQuote(String ticker) {
        String upper = ticker.toUpperCase();

        TickerBook tb = book.find(upper);
        QuoteData fromBook = tb != null ? toQuoteData(upper, tb) : null;
        if (fromBook != null) {
            if (!isStale(tb)) {
                log.debug("Book quote for {}: price={}", upper, fromBook.price());
                return fromBook;
            }
            log.warn("Book quote for {} is stale (connectionLost={}) — trying Yahoo fallback",
                    upper, book.isConnectionLost());
            QuoteData yahoo = fetchYahooCached(upper);
            if (yahoo != null && yahoo.dataAvailable()) {
                return yahoo;
            }
            return fromBook; // stale but real — last known values, honestly aged
        }

        QuoteData yahoo = fetchYahooCached(upper);
        if (yahoo != null) {
            return yahoo;
        }
        return QuoteData.empty(upper, "No quote source available");
    }

    /**
     * Stale means: the feed behind the values is demonstrably gone (connection lost) or the
     * market is REGULAR and this ticker's liveness fields (volume/last — volume ticks on
     * every trade) have been silent beyond the threshold. Outside REGULAR silence is normal.
     */
    private boolean isStale(TickerBook tb) {
        if (book.isConnectionLost()) return true;
        if (marketStateService.getMarketState() != MarketStateService.MarketState.REGULAR) {
            return false;
        }
        Instant now = Instant.now();
        Long volumeAge = tb.volume().get().ageSeconds(now);
        Long lastAge = tb.last().get().ageSeconds(now);
        Long freshest = minNonNull(volumeAge, lastAge);
        return freshest == null || freshest > tickerStaleSeconds;
    }

    private static Long minNonNull(Long a, Long b) {
        if (a == null) return b;
        if (b == null) return a;
        return Math.min(a, b);
    }

    private QuoteData toQuoteData(String upper, TickerBook tb) {
        Double last = tb.last().value();
        Double bid = tb.bid().value();
        Double ask = tb.ask().value();
        Double price = last != null ? last
                     : (bid != null && ask != null) ? (bid + ask) / 2.0
                     : null;
        if (price == null) return null; // Book entry exists but no usable quote yet

        Double close = tb.close().value();
        Double change = close != null ? price - close : null;
        Double changePct = (change != null && close != 0) ? (change / close) * 100.0 : null;

        return new QuoteData(
                upper,
                price,
                change,
                changePct,
                tb.open().value(),
                tb.high().value(),
                tb.low().value(),
                tb.volume().value(),
                null,   // avgVolume — not an IBKR tick
                null,   // volumeRatio
                null,   // marketCap
                null,   // P/E
                null,   // 52w high
                null,   // 52w low
                null,   // marketState
                null,   // preMarketPrice
                null,   // preMarketChangePct
                null,   // postMarketPrice
                null,   // postMarketChangePct
                "ibkr",
                null,
                true,
                Instant.now()
        );
    }

    /** Yahoo fallback with the 60s "quotes" cache (only successful results are cached, so a
     *  failed scrape can be retried immediately instead of pinning an empty result). */
    private QuoteData fetchYahooCached(String upper) {
        Cache cache = cacheManager.getCache("quotes");
        if (cache != null) {
            QuoteData cached = cache.get(upper, QuoteData.class);
            if (cached != null) return cached;
        }
        try {
            QuoteData data = yahooFinanceScraper.fetchQuote(upper);
            log.debug("Yahoo quote for {}: price={}", upper, data != null ? data.price() : null);
            if (cache != null && data != null && data.dataAvailable()) {
                cache.put(upper, data);
            }
            return data;
        } catch (ScraperException e) {
            log.error("Yahoo quote failed for {}: {}", upper, e.getMessage());
            return QuoteData.empty(upper, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error fetching quote for {}: {}", upper, e.getMessage());
            return QuoteData.empty(upper, "Unexpected error: " + e.getMessage());
        }
    }
}
