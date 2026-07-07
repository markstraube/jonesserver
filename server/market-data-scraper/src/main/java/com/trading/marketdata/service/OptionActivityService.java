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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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

    // "weekly-monthly" (default) or "chronological" (the pre-2026-07-06 behavior, kept for A/B)
    @Value("${unusual-activity.expiry-selection:weekly-monthly}")
    private String expirySelection;

    // Day-memory strikes outside the nearest-strikes window to keep re-scanning, per expiry.
    // 0 disables the sticky pass entirely.
    @Value("${unusual-activity.sticky-extra-strikes:8}")
    private int stickyExtraStrikes;

    @Value("${unusual-activity.min-volume-oi-ratio:1.0}")
    private double minVolumeOiRatio;

    @Value("${cache.invalid-contract.ttl-seconds:3600}")
    private long invalidContractTtlSeconds;

    @Value("${unusual-activity.min-notional:50000000}")
    private double minNotional;

    // Optional second gate on PREMIUM notional (volume x option price x 100, USD): actual
    // money spent instead of strike-referenced size. 0 disables (default) — calibrate with
    // live premium data first, then decide the threshold. Fail-open: a contract whose price
    // ticks didn't arrive is NOT suppressed by this gate (a missing tick must never eat a
    // signal), it's flagged with premiumNotionalUsd=null and logged.
    @Value("${unusual-activity.min-premium-notional:0}")
    private double minPremiumNotional;

    @Value("${unusual-activity.max-results:10}")
    private int maxResults;

    private final IbkrMarketDataService ibkrService;
    private final QuoteService quoteService;
    private final CacheManager cacheManager;
    private final MarketStateService marketStateService;

    public OptionActivityService(IbkrMarketDataService ibkrService, QuoteService quoteService,
                                  CacheManager cacheManager, MarketStateService marketStateService) {
        this.ibkrService = ibkrService;
        this.quoteService = quoteService;
        this.cacheManager = cacheManager;
        this.marketStateService = marketStateService;
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
        // Market-closed short circuit: without a live session there are no volume ticks, so
        // every volume attempt is a guaranteed timeout cascade (observed 2026-07-03: 86s scan
        // of pure timeouts). When CLOSED we serve OI from cache without any fetch, fetch OI
        // once for uncached contracts, and skip all volume re-fetches. UNKNOWN deliberately
        // does NOT short-circuit (fail-open, same rule as persistence).
        boolean marketClosed = marketStateService.getMarketState() == MarketStateService.MarketState.CLOSED;
        if (marketClosed) {
            log.info("Options activity scan for {}: market CLOSED, OI-only mode (no volume fetches)", ticker);
        }

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

        List<String> expiries = selectExpiries(chain.expirations());

        List<Double> candidateStrikes = chain.strikes().stream()
                .sorted(Comparator.comparingDouble(s -> Math.abs(s - price)))
                .limit((long) nearestStrikes * strikeOversampleFactor)
                .collect(Collectors.toList());

        log.info("Options activity scan for {}: price={}, expiries={}, candidateStrikes={}",
                upper, price, expiries, candidateStrikes);

        List<OptionsData.UnusualActivity> unusual = new ArrayList<>();
        List<OptionsData.OiLevel> oiProfile = new ArrayList<>();

        for (String expiry : expiries) {
            // --- Main pass: nearest-strikes window around the current price ---
            Set<Double> covered = new HashSet<>();
            int resolvedStrikes = 0;
            for (double strike : candidateStrikes) {
                if (resolvedStrikes >= nearestStrikes) break;

                if (scanStrike(upper, expiry, strike, marketClosed, unusual, oiProfile)) {
                    covered.add(strike);
                    resolvedStrikes++;
                } else {
                    // INFO deliberately (was DEBUG): silent error-200 rejections hid the missing
                    // monthly 1000 strike on 2026-07-03. A rejected candidate is a data-coverage
                    // decision and must be visible — especially since a spurious Gateway 200
                    // poisons the negative cache for its full TTL without any trace otherwise.
                    log.info("Options activity for {}: strike {} not listed for expiry {} (error 200, negative-cached {}s), trying next candidate",
                            upper, strike, expiry, invalidContractTtlSeconds);
                }
            }

            // --- Sticky pass: day-memory strikes that drifted out of the window ---
            // The nearest-strikes window follows the price, so a level scanned in the morning
            // can silently leave the profile after a large move (2026-07-06: MU gapped +3% and
            // the 950 put wall — 5,190 puts, defended to 28 cents two sessions earlier — fell
            // out of the visible window). Once a strike has been seen today it stays observable:
            // extras are chosen from today's memory by last-seen total OI (heaviest first, so
            // walls win over micro strikes), capped at stickyExtraStrikes. Marginal cost per
            // extra strike is two volume requests — OI is served from the option-oi cache.
            // NOTE the honest limitation: this cannot see a wall that was never inside any
            // window today. It stops the scan from *forgetting* walls, not from missing them.
            Set<Double> deadExtras = new HashSet<>();
            Map<Double, Long> memory = readDayMemory(upper, expiry);
            if (stickyExtraStrikes > 0 && !memory.isEmpty()) {
                List<Double> extras = memory.entrySet().stream()
                        .filter(en -> !covered.contains(en.getKey()))
                        .sorted(Map.Entry.<Double, Long>comparingByValue().reversed())
                        .limit(stickyExtraStrikes)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());
                if (!extras.isEmpty()) {
                    log.info("Options activity for {} {}: sticky day-memory strikes outside window: {}",
                            upper, expiry, extras);
                    for (double strike : extras) {
                        if (scanStrike(upper, expiry, strike, marketClosed, unusual, oiProfile)) {
                            covered.add(strike);
                        } else {
                            // Contract no longer resolvable (delisted / negative-cached): drop it
                            // from memory so it stops occupying one of the capped extra slots.
                            deadExtras.add(strike);
                            log.info("Options activity for {} {}: sticky strike {} no longer resolvable, evicting from day memory",
                                    upper, expiry, strike);
                        }
                    }
                }
            }

            updateDayMemory(upper, expiry, oiProfile, deadExtras);
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

    /**
     * Scan one strike (both rights) for one expiry. Contributes flagged contracts to
     * {@code unusual} and — when at least one right resolves — one row to {@code oiProfile}.
     *
     * @return true when the strike is listed for this expiry (at least one right resolved)
     */
    private boolean scanStrike(String ticker, String expiry, double strike, boolean marketClosed,
                               List<OptionsData.UnusualActivity> unusual,
                               List<OptionsData.OiLevel> oiProfile) {
        Long callOI = null, putOI = null;
        boolean anyValid = false;
        for (String right : new String[]{"C", "P"}) {
            ContractResult result = evaluateContract(ticker, expiry, strike, right, marketClosed);
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
                            ticker, expiry, strike, right,
                            String.format("%.2f", result.ratio),
                            String.format("%.0f", notional),
                            String.format("%.0f", minNotional));
                } else {
                    // Premium notional: what was actually paid, not what the strike references.
                    // Basis: last (today's or — untraded contract — previous session's print),
                    // fallback bid/ask mid, else null. Strike notional overstates cheap OTM
                    // contracts by up to ~100x; this is the corrective measure.
                    Double premium = premiumPrice(result);
                    Double premiumNotional = premium != null ? result.volume * premium * 100 : null;

                    if (minPremiumNotional > 0 && premiumNotional != null && premiumNotional < minPremiumNotional) {
                        log.info("Options activity for {} {} {} {}: strike-notional {} passed but premium-notional {} below floor {} — suppressed",
                                ticker, expiry, strike, right,
                                String.format("%.0f", notional),
                                String.format("%.0f", premiumNotional),
                                String.format("%.0f", minPremiumNotional));
                    } else {
                        if (minPremiumNotional > 0 && premiumNotional == null) {
                            log.info("Options activity for {} {} {} {}: no price ticks for premium gate — flagged anyway (fail-open)",
                                    ticker, expiry, strike, right);
                        }
                        String type = "C".equals(right) ? "CALL" : "PUT";
                        String sentiment = "CALL".equals(type) ? "BULLISH" : "BEARISH";
                        unusual.add(new OptionsData.UnusualActivity(
                                expiry, strike, type, result.volume, result.openInterest,
                                result.ratio, result.bid, result.ask, result.last,
                                premiumNotional, null, sentiment));
                    }
                }
            }
        }

        if (anyValid) {
            oiProfile.add(new OptionsData.OiLevel(expiry, strike, callOI, putOI));
        }
        return anyValid;
    }

    // -------------------------------------------------------------------------
    // Expiry selection
    // -------------------------------------------------------------------------

    private static final DateTimeFormatter EXPIRY_FMT = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
    private static final ZoneId US_MARKET_TZ = ZoneId.of("America/New_York");

    /**
     * Which expiry boards to scan.
     *
     * Mode "chronological" is the pre-2026-07-06 behavior: the first {@code expiryCount}
     * boards by date. Live batch on 2026-07-06 showed why that fails as a default — with
     * Mon/Wed weeklies listed, "first two" meant Fri+Mon for MU/AMD/INTC (Monday board all
     * zero OI, the monthly with the structural put positioning invisible) and literally
     * 0DTE+Wednesday for AVGO. The per-ticker numbers measured different regimes and were
     * not comparable across the batch.
     *
     * Mode "weekly-monthly" (default) makes the two slots semantic instead of positional:
     *   slot 1 — the nearest FRIDAY expiry: the front weekly board ("panic" positioning).
     *            Falls back to the nearest expiry of any weekday if the chain lists no
     *            Friday at all (holiday-shifted weeklies land here too, via the fallback).
     *   slot 2 — the nearest standard monthly (third Friday). If the front Friday IS the
     *            monthly (expiration week), the slot moves to the following monthly so the
     *            "structure" view never disappears.
     *   slots 3+ (expiries > 2) — chronological fill with whatever isn't picked yet.
     */
    private List<String> selectExpiries(Set<String> expirations) {
        List<String> chronological = expirations.stream()
                .sorted()
                .limit(Math.max(expiryCount, 0))
                .collect(Collectors.toList());
        if (expiryCount <= 0 || !"weekly-monthly".equalsIgnoreCase(expirySelection)) {
            return chronological;
        }

        LocalDate today = LocalDate.now(US_MARKET_TZ);
        List<LocalDate> future = new ArrayList<>();
        for (String raw : new TreeSet<>(expirations)) {
            try {
                LocalDate d = LocalDate.parse(raw, EXPIRY_FMT);
                if (!d.isBefore(today)) {
                    future.add(d);
                }
            } catch (java.time.format.DateTimeParseException e) {
                log.warn("Options activity: unparseable expiry '{}' in chain, ignoring", raw);
            }
        }
        if (future.isEmpty()) {
            log.warn("Options activity: no parseable future expiries in chain, falling back to chronological {}",
                    chronological);
            return chronological;
        }

        LinkedHashSet<LocalDate> picked = new LinkedHashSet<>();
        LocalDate anchor = future.stream()
                .filter(d -> d.getDayOfWeek() == DayOfWeek.FRIDAY)
                .findFirst()
                .orElse(future.get(0));
        picked.add(anchor);

        if (picked.size() < expiryCount) {
            future.stream()
                    .filter(OptionActivityService::isStandardMonthly)
                    .filter(d -> !picked.contains(d))
                    .findFirst()
                    .ifPresent(picked::add);
        }
        for (LocalDate d : future) {
            if (picked.size() >= expiryCount) break;
            picked.add(d);
        }

        List<String> selected = picked.stream()
                .sorted()
                .map(EXPIRY_FMT::format)
                .collect(Collectors.toList());
        log.info("Options activity expiry selection (weekly-monthly): anchor={}, selected={}, chain front={}",
                EXPIRY_FMT.format(anchor), selected,
                future.stream().limit(4).map(EXPIRY_FMT::format).collect(Collectors.toList()));
        return selected;
    }

    /** Standard monthly expiration: the third Friday of a month (day-of-month 15..21). */
    private static boolean isStandardMonthly(LocalDate d) {
        return d.getDayOfWeek() == DayOfWeek.FRIDAY && d.getDayOfMonth() >= 15 && d.getDayOfMonth() <= 21;
    }

    // -------------------------------------------------------------------------
    // Day memory ("Tagesgedächtnis") of scanned strikes
    // -------------------------------------------------------------------------

    /**
     * Cache key includes the NY trading date, so each session starts with a clean memory —
     * the cache TTL only has to comfortably outlive one session, it is not the day boundary.
     * (expireAfterWrite alone would never fire while polling continuously rewrites the key.)
     */
    private String dayMemoryKey(String ticker, String expiry) {
        return ticker + ":" + expiry + ":" + EXPIRY_FMT.format(LocalDate.now(US_MARKET_TZ));
    }

    @SuppressWarnings("unchecked")
    private Map<Double, Long> readDayMemory(String ticker, String expiry) {
        Cache cache = cacheManager.getCache("oiDayMemory");
        if (cache == null) return Map.of();
        Map<Double, Long> stored = cache.get(dayMemoryKey(ticker, expiry), Map.class);
        return stored != null ? stored : Map.of();
    }

    /**
     * Merge this scan's resolved strikes (with their last-seen total OI, used for sticky-slot
     * prioritization) into today's memory. Merge, not overwrite: strikes beyond the sticky cap
     * must survive rounds in which they weren't re-scanned, otherwise the memory would shrink
     * to window+cap and defeat its purpose.
     */
    private void updateDayMemory(String ticker, String expiry,
                                 List<OptionsData.OiLevel> oiProfile, Set<Double> deadStrikes) {
        Cache cache = cacheManager.getCache("oiDayMemory");
        if (cache == null) return;
        Map<Double, Long> merged = new HashMap<>(readDayMemory(ticker, expiry));
        merged.keySet().removeAll(deadStrikes);
        for (OptionsData.OiLevel level : oiProfile) {
            if (!expiry.equals(level.expiry()) || level.strike() == null) continue;
            long total = (level.callOpenInterest() != null ? level.callOpenInterest() : 0L)
                       + (level.putOpenInterest() != null ? level.putOpenInterest() : 0L);
            merged.put(level.strike(), total);
        }
        if (!merged.isEmpty()) {
            cache.put(dayMemoryKey(ticker, expiry), merged);
        }
    }

    private record ContractResult(Long volume, Long openInterest, Double ratio,
                                  Double bid, Double ask, Double last) {}

    /**
     * Premium basis for notional: last print first (a traded contract's own price), bid/ask
     * mid as fallback (untraded but quoted), null when neither is available. The wrapper
     * already filtered out IBKR's -1/0 "no quote" placeholders, so any stored value is real.
     */
    private static Double premiumPrice(ContractResult r) {
        if (r.last != null) return r.last;
        if (r.bid != null && r.ask != null) return (r.bid + r.ask) / 2.0;
        return null;
    }

    private ContractResult evaluateContract(String ticker, String expiry, double strike, String right,
                                             boolean marketClosed) {
        String contractKey = ticker + ":" + expiry + ":" + strike + ":" + right;

        Cache invalidCache = cacheManager.getCache("invalidOptionContract");
        if (invalidCache != null && invalidCache.get(contractKey) != null) {
            return null; // already confirmed not listed for this expiry — don't ask again
        }

        Cache oiCache = cacheManager.getCache("optionOpenInterest");
        Long cachedOI = oiCache != null ? oiCache.get(contractKey, Long.class) : null;

        Long volume;
        Long openInterest;
        Double bid = null, ask = null, last = null;

        try {
            if (cachedOI != null && marketClosed) {
                // Closed market: cached OI is the complete answer, a volume fetch can only time out.
                return new ContractResult(null, cachedOI, null, null, null, null);
            } else if (cachedOI != null) {
                // OI doesn't move intraday — reuse it, only ask IBKR for a fresh volume read.
                // Price ticks (bid/ask/last) ride on the same request as default ticks.
                openInterest = cachedOI;
                IbkrOptionContractActivity act = fetchWithRetry(ticker, expiry, strike, right, false);
                volume = act != null ? act.volume() : null;
                if (act != null) {
                    bid = act.bid(); ask = act.ask(); last = act.last();
                }
            } else {
                IbkrOptionContractActivity act = fetchWithRetry(ticker, expiry, strike, right, true);
                if (act == null) return null;
                volume = act.volume();
                openInterest = act.openInterest();
                bid = act.bid(); ask = act.ask(); last = act.last();
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
        if (volume == null && !marketClosed) {
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
                // The re-fetch carries the same default price ticks — backfill whatever the
                // first attempt was missing (never overwrite an already-captured price).
                if (refetch != null) {
                    if (bid == null) bid = refetch.bid();
                    if (ask == null) ask = refetch.ask();
                    if (last == null) last = refetch.last();
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
            return new ContractResult(volume, openInterest, null, bid, ask, last);
        }

        double ratio = (double) volume / openInterest;
        log.info("Options activity check {} {} {} {}: volume={}, openInterest={}, ratio={}",
                ticker, expiry, strike, right, volume, openInterest, String.format("%.2f", ratio));
        return new ContractResult(volume, openInterest, ratio, bid, ask, last);
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
