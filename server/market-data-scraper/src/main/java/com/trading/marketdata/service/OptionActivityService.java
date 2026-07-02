package com.trading.marketdata.service;

import com.trading.marketdata.ibkr.IbkrMarketDataService;
import com.trading.marketdata.ibkr.IbkrOptionContractActivity;
import com.trading.marketdata.ibkr.IbkrOptionsChainResult;
import com.trading.marketdata.model.OptionsData;
import com.trading.marketdata.model.QuoteData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds "unusual options activity" directly from IBKR structured data instead of scraping
 * Barchart's report. IBKR has no single endpoint for this — it's a curated/computed product
 * Barchart & co. sell, built on top of raw per-contract volume + open interest. This service
 * builds the same thing ourselves from the raw ingredients:
 *
 *   1. Resolve the underlying's conId (reqContractDetails)      — cached ~forever, static data
 *   2. Resolve the option chain (reqSecDefOptParams)             — cached ~12h, rarely changes
 *   3. Pick strikes near the current price, nearest expiry(s)
 *   4. Per contract: open interest (cached ~4h, published once daily, doesn't move intraday)
 *                     + volume (always fetched fresh, changes continuously — this is the
 *                       whole point of "unusual right now")
 *   5. Flag contracts where volume/openInterest exceeds a configurable threshold
 *
 * All caching here is deliberately tiered to match how often each input actually changes,
 * not fetched fresh indiscriminately — see CacheConfig for the reasoning behind each TTL.
 */
@Service
public class OptionActivityService {

    private static final Logger log = LoggerFactory.getLogger(OptionActivityService.class);

    @Value("${unusual-activity.nearest-strikes:8}")
    private int nearestStrikes;

    @Value("${unusual-activity.expiries:1}")
    private int expiryCount;

    @Value("${unusual-activity.min-volume-oi-ratio:1.0}")
    private double minVolumeOiRatio;

    @Value("${unusual-activity.max-results:10}")
    private int maxResults;

    private final IbkrMarketDataService ibkrService;
    private final QuoteService quoteService;
    private final CacheManager cacheManager;

    public OptionActivityService(IbkrMarketDataService ibkrService, QuoteService quoteService,
                                  CacheManager cacheManager) {
        this.ibkrService = ibkrService;
        this.quoteService = quoteService;
        this.cacheManager = cacheManager;
    }

    public List<OptionsData.UnusualActivity> computeUnusualActivity(String ticker) {
        String upper = ticker.toUpperCase();

        Integer conId = getOrFetch("ibkrConId", upper, Integer.class, () -> ibkrService.fetchConId(upper));
        if (conId == null) {
            log.debug("Unusual activity for {}: no conId (IBKR not connected or lookup failed)", upper);
            return List.of();
        }

        IbkrOptionsChainResult chain = getOrFetch("optionChain", upper, IbkrOptionsChainResult.class,
                () -> ibkrService.fetchOptionsChain(upper, conId));
        if (chain == null || chain.strikes().isEmpty() || chain.expirations().isEmpty()) {
            log.debug("Unusual activity for {}: no option chain available", upper);
            return List.of();
        }

        QuoteData quote = quoteService.getQuote(upper);
        if (quote == null || quote.price() == null) {
            log.debug("Unusual activity for {}: no underlying price available, can't pick near-the-money strikes", upper);
            return List.of();
        }
        double price = quote.price();

        List<String> expiries = chain.expirations().stream()
                .sorted()
                .limit(expiryCount)
                .collect(Collectors.toList());

        List<Double> strikes = chain.strikes().stream()
                .sorted(Comparator.comparingDouble(s -> Math.abs(s - price)))
                .limit(nearestStrikes)
                .collect(Collectors.toList());

        log.info("Unusual activity scan for {}: price={}, expiries={}, strikes={}",
                upper, price, expiries, strikes);

        List<OptionsData.UnusualActivity> results = new ArrayList<>();
        for (String expiry : expiries) {
            for (double strike : strikes) {
                for (String right : new String[]{"C", "P"}) {
                    OptionsData.UnusualActivity activity = evaluateContract(upper, expiry, strike, right);
                    if (activity != null) {
                        results.add(activity);
                    }
                }
            }
        }

        return results.stream()
                .sorted(Comparator.comparingDouble(OptionsData.UnusualActivity::volumeOiRatio).reversed())
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    private OptionsData.UnusualActivity evaluateContract(String ticker, String expiry, double strike, String right) {
        String oiCacheKey = ticker + ":" + expiry + ":" + strike + ":" + right;
        Cache oiCache = cacheManager.getCache("optionOpenInterest");
        Long cachedOI = oiCache != null ? oiCache.get(oiCacheKey, Long.class) : null;

        Long volume;
        Long openInterest;

        if (cachedOI != null) {
            // OI doesn't move intraday — reuse it, only ask IBKR for a fresh volume read.
            openInterest = cachedOI;
            IbkrOptionContractActivity act = ibkrService.fetchContractActivity(ticker, expiry, strike, right, false);
            volume = act != null ? act.volume() : null;
        } else {
            IbkrOptionContractActivity act = ibkrService.fetchContractActivity(ticker, expiry, strike, right, true);
            if (act == null) return null;
            volume = act.volume();
            openInterest = act.openInterest();
            if (openInterest != null && oiCache != null) {
                oiCache.put(oiCacheKey, openInterest);
            }
        }

        if (volume == null || openInterest == null || openInterest <= 0) {
            log.info("Unusual activity check {} {} {} {}: skipped (volume={}, openInterest={})",
                    ticker, expiry, strike, right, volume, openInterest);
            return null;
        }

        double ratio = (double) volume / openInterest;
        log.info("Unusual activity check {} {} {} {}: volume={}, openInterest={}, ratio={}",
                ticker, expiry, strike, right, volume, openInterest, String.format("%.2f", ratio));
        if (ratio < minVolumeOiRatio) return null;

        String type = "C".equals(right) ? "CALL" : "PUT";
        String sentiment = "CALL".equals(type) ? "BULLISH" : "BEARISH";

        return new OptionsData.UnusualActivity(expiry, strike, type, volume, openInterest, ratio, null, sentiment);
    }

    // -------------------------------------------------------------------------
    // Small helper: Spring's Cache#get(key, Callable) exists but its checked-exception
    // handling is awkward here; this wraps get-or-compute-and-store explicitly so a null
    // result (IBKR not connected, lookup failed) is never cached as a false "miss forever".
    // -------------------------------------------------------------------------
    private <T> T getOrFetch(String cacheName, String key, Class<T> type, java.util.function.Supplier<T> loader) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            T cached = cache.get(key, type);
            if (cached != null) return cached;
        }
        T fetched = loader.get();
        if (fetched != null && cache != null) {
            cache.put(key, fetched);
        }
        return fetched;
    }
}
