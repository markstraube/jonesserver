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
 * Builds "unusual options activity" AND a per-strike open-interest profile directly from IBKR
 * structured data instead of scraping Barchart. IBKR has no single endpoint for either — both
 * are curated/computed products vendors sell, built on top of raw per-contract volume + open
 * interest. This service builds both from the same raw ingredients in one pass:
 *
 *   1. Resolve the underlying's conId (reqContractDetails)      — cached ~forever, static data
 *   2. Resolve the option chain (reqSecDefOptParams)             — cached ~12h, rarely changes
 *   3. Pick strikes near the current price, nearest expiry(s)
 *   4. Per contract: open interest (cached ~4h, published once daily, doesn't move intraday)
 *                     + volume (always fetched fresh, changes continuously)
 *   5a. unusualActivity: contracts where volume/openInterest exceeds a configurable threshold
 *   5b. oiProfile: call+put open interest for every scanned strike, regardless of today's
 *       volume — a rough resistance/support ladder (heavy OI is where dealer hedging flow
 *       concentrates as price approaches a strike). This is a byproduct of the same fetches
 *       behind unusualActivity, not an extra round of IBKR calls.
 *
 * All caching here is deliberately tiered to match how often each input actually changes,
 * not fetched fresh indiscriminately — see CacheConfig for the reasoning behind each TTL.
 */
@Service
public class OptionActivityService {

    private static final Logger log = LoggerFactory.getLogger(OptionActivityService.class);

    @Value("${unusual-activity.nearest-strikes:16}")
    private int nearestStrikes;

    @Value("${unusual-activity.strike-oversample-factor:3}")
    private int strikeOversampleFactor;

    @Value("${unusual-activity.expiries:1}")
    private int expiryCount;

    @Value("${unusual-activity.min-volume-oi-ratio:1.0}")
    private double minVolumeOiRatio;

    @Value("${unusual-activity.min-notional:50000000}")
    private double minNotional;

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

    /** Result of one scan: today's flagged unusual contracts, plus the full OI ladder scanned. */
    public record OptionActivityResult(
            List<OptionsData.UnusualActivity> unusualActivity,
            List<OptionsData.OiLevel> oiProfile
    ) {
        static OptionActivityResult empty() {
            return new OptionActivityResult(List.of(), List.of());
        }
    }

    public OptionActivityResult computeActivity(String ticker) {
        String upper = ticker.toUpperCase();

        Integer conId = getOrFetch("ibkrConId", upper, Integer.class, () -> ibkrService.fetchConId(upper));
        if (conId == null) {
            log.debug("Options activity for {}: no conId (IBKR not connected or lookup failed)", upper);
            return OptionActivityResult.empty();
        }

        IbkrOptionsChainResult chain = getOrFetch("optionChain", upper, IbkrOptionsChainResult.class,
                () -> ibkrService.fetchOptionsChain(upper, conId));
        if (chain == null || chain.strikes().isEmpty() || chain.expirations().isEmpty()) {
            log.debug("Options activity for {}: no option chain available", upper);
            return OptionActivityResult.empty();
        }

        QuoteData quote = quoteService.getQuote(upper);
        if (quote == null || quote.price() == null) {
            log.debug("Options activity for {}: no underlying price available, can't pick near-the-money strikes", upper);
            return OptionActivityResult.empty();
        }
        double price = quote.price();

        List<String> expiries = chain.expirations().stream()
                .sorted()
                .limit(expiryCount)
                .collect(Collectors.toList());

        List<Double> candidateStrikes = chain.strikes().stream()
                .sorted(Comparator.comparingDouble(s -> Math.abs(s - price)))
                .limit((long) nearestStrikes * strikeOversampleFactor)
                .collect(Collectors.toList());

        log.info("Options activity scan for {}: price={}, expiries={}, candidateStrikes={}",
                upper, price, expiries, candidateStrikes);

        List<OptionsData.UnusualActivity> unusual = new ArrayList<>();
        List<OptionsData.OiLevel> oiProfile = new ArrayList<>();

        for (String expiry : expiries) {
            int resolvedStrikes = 0;
            for (double strike : candidateStrikes) {
                if (resolvedStrikes >= nearestStrikes) break;

                Long callOI = null, putOI = null;
                boolean anyValid = false;
                for (String right : new String[]{"C", "P"}) {
                    ContractResult result = evaluateContract(upper, expiry, strike, right);
                    if (result == null) continue; // invalid for this expiry, or fetch failed
                    anyValid = true;
                    if ("C".equals(right)) callOI = result.openInterest; else putOI = result.openInterest;

                    if (result.ratio != null && result.ratio >= minVolumeOiRatio) {
                        // Notional floor: ratio alone over-flags illiquid contracts (tiny OI in the
                        // denominator makes a handful of lots look "unusual"). Strike-referenced
                        // notional (volume * strike * 100) makes the size threshold comparable
                        // across underlyings at very different price levels — a 19-lot trade on a
                        // $2,000 stock and a 9,000-lot trade on a $1,000 stock land on the same scale.
                        double notional = result.volume * strike * 100;
                        if (notional < minNotional) {
                            log.debug("Options activity for {} {} {} {}: ratio {} passed but notional {} below floor {}",
                                    upper, expiry, strike, right,
                                    String.format("%.2f", result.ratio),
                                    String.format("%.0f", notional),
                                    String.format("%.0f", minNotional));
                        } else {
                            String type = "C".equals(right) ? "CALL" : "PUT";
                            String sentiment = "CALL".equals(type) ? "BULLISH" : "BEARISH";
                            unusual.add(new OptionsData.UnusualActivity(
                                    expiry, strike, type, result.volume, result.openInterest,
                                    result.ratio, null, sentiment));
                        }
                    }
                }

                if (anyValid) {
                    oiProfile.add(new OptionsData.OiLevel(expiry, strike, callOI, putOI));
                    resolvedStrikes++;
                } else {
                    log.debug("Options activity for {}: strike {} not listed for expiry {}, trying next candidate",
                            upper, strike, expiry);
                }
            }
        }

        List<OptionsData.UnusualActivity> topUnusual = unusual.stream()
                .sorted(Comparator.comparingDouble(OptionsData.UnusualActivity::volumeOiRatio).reversed())
                .limit(maxResults)
                .collect(Collectors.toList());

        List<OptionsData.OiLevel> sortedOiProfile = oiProfile.stream()
                .sorted(Comparator.comparingDouble(OptionsData.OiLevel::strike))
                .collect(Collectors.toList());

        return new OptionActivityResult(topUnusual, sortedOiProfile);
    }

    private record ContractResult(Long volume, Long openInterest, Double ratio) {}

    private ContractResult evaluateContract(String ticker, String expiry, double strike, String right) {
        String contractKey = ticker + ":" + expiry + ":" + strike + ":" + right;

        Cache invalidCache = cacheManager.getCache("invalidOptionContract");
        if (invalidCache != null && invalidCache.get(contractKey) != null) {
            return null; // already confirmed not listed for this expiry — don't ask again
        }

        Cache oiCache = cacheManager.getCache("optionOpenInterest");
        Long cachedOI = oiCache != null ? oiCache.get(contractKey, Long.class) : null;

        Long volume;
        Long openInterest;

        try {
            if (cachedOI != null) {
                // OI doesn't move intraday — reuse it, only ask IBKR for a fresh volume read.
                openInterest = cachedOI;
                IbkrOptionContractActivity act = fetchWithRetry(ticker, expiry, strike, right, false);
                volume = act != null ? act.volume() : null;
            } else {
                IbkrOptionContractActivity act = fetchWithRetry(ticker, expiry, strike, right, true);
                if (act == null) return null;
                volume = act.volume();
                openInterest = act.openInterest();
                if (openInterest != null && oiCache != null) {
                    oiCache.put(contractKey, openInterest);
                }
            }
        } catch (com.trading.marketdata.ibkr.IbkrContractNotFoundException e) {
            if (invalidCache != null) invalidCache.put(contractKey, true);
            return null;
        }

        if (openInterest == null) {
            log.info("Options activity check {} {} {} {}: skipped (volume={}, openInterest=null)",
                    ticker, expiry, strike, right, volume);
            return null;
        }

        // Volume back-fill: a "successful" fetch can still come back without a volume tick when
        // the Gateway's internal option-model computation stalls delivery ("Model is not valid"
        // / PACED in the Gateway log) — the request completes with partial data. Confirmed live
        // 2026-07-02: ~half of all contracts on a heavy expiry day returned volume=null and
        // silently fell out of unusual-activity detection, systematically undercounting it.
        // One targeted volume-only re-read against the now-warm market data line closes the gap
        // at a bounded cost of a single extra request (deliberately no retry — worst case is one
        // timeout, not two).
        if (volume == null) {
            try {
                IbkrOptionContractActivity refetch =
                        ibkrService.fetchContractActivity(ticker, expiry, strike, right, false);
                if (refetch != null && refetch.volume() != null) {
                    volume = refetch.volume();
                    log.info("Options activity volume re-fetch {} {} {} {}: recovered volume={}",
                            ticker, expiry, strike, right, volume);
                } else {
                    log.info("Options activity volume re-fetch {} {} {} {}: still no volume, keeping OI-only",
                            ticker, expiry, strike, right);
                }
            } catch (com.trading.marketdata.ibkr.IbkrContractNotFoundException e) {
                // Contract just delivered OI, so error 200 here would be transient noise — do
                // NOT negative-cache it, just keep the OI-only result.
                log.warn("Options activity volume re-fetch {} {} {} {}: unexpected contract-not-found, keeping OI-only",
                        ticker, expiry, strike, right);
            }
        }

        if (volume == null || openInterest <= 0) {
            log.info("Options activity check {} {} {} {}: volume={}, openInterest={} (no ratio, OI-only)",
                    ticker, expiry, strike, right, volume, openInterest);
            return new ContractResult(volume, openInterest, null);
        }

        double ratio = (double) volume / openInterest;
        log.info("Options activity check {} {} {} {}: volume={}, openInterest={}, ratio={}",
                ticker, expiry, strike, right, volume, openInterest, String.format("%.2f", ratio));
        return new ContractResult(volume, openInterest, ratio);
    }

    /**
     * IB Gateway runs an internal options pricing-model computation (Greeks) for every
     * subscribed contract regardless of what generic ticks the API client actually asked for.
     * When that internal computation stalls (seen in Gateway's own log as repeated "Model is
     * not valid" / "Missing OptionModelParameters" for a contract, tied to a frozen/stale
     * underlying reference price) it appears to briefly hold up delivery of the tick we
     * actually requested, causing a client-side timeout even though nothing is structurally
     * wrong with that contract. A fresh second attempt after the first one gives up
     * consistently succeeds quickly, since the internal stall has resolved (or IBKR has given
     * up on it) by then — so one retry is worth the ~5s it might cost, versus a permanent gap.
     *
     * IbkrContractNotFoundException (error 200, contract genuinely doesn't exist for this
     * expiry — confirmed live) is NOT retried here; it propagates to the caller, which records
     * it in the negative cache instead. Retrying a confirmed-nonexistent contract is guaranteed
     * to fail again.
     */
    private IbkrOptionContractActivity fetchWithRetry(String ticker, String expiry, double strike,
                                                        String right, boolean needOpenInterest) {
        IbkrOptionContractActivity act = ibkrService.fetchContractActivity(ticker, expiry, strike, right, needOpenInterest);
        if (act != null) return act;
        log.info("Options activity retry {} {} {} {} (needOpenInterest={})", ticker, expiry, strike, right, needOpenInterest);
        return ibkrService.fetchContractActivity(ticker, expiry, strike, right, needOpenInterest);
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
