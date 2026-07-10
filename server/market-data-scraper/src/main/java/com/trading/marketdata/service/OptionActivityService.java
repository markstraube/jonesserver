package com.trading.marketdata.service;

import com.trading.marketdata.analysis.AggressorClassifier;
import com.trading.marketdata.analysis.AggressorProfile;
import com.trading.marketdata.book.MarketDataBook;
import com.trading.marketdata.book.SubscriptionManager;
import com.trading.marketdata.book.TickerBook;
import com.trading.marketdata.ibkr.HistoricalRequestBudget;
import com.trading.marketdata.ibkr.IbkrDayTicks;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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

    // A last print older than this (vs. its own IBKR trade timestamp, tick 45/88) is
    // classified STALE instead of being located inside a quote it never traded against.
    @Value("${unusual-activity.aggressor-stale-seconds:300}")
    private long aggressorStaleSeconds;

    // --- UA stage 2: per-contract aggressor classification from historical ticks ---
    // Package-private (not private) so the escalation tests can set them without Spring.
    // Default OFF: the stage consumes historical-data pacing budget and stays opt-in until
    // the live SPY verification from the rebuild prompt has been run on this installation.
    @Value("${ua.aggressor.enabled:false}")
    boolean aggressorEnabled;

    @Value("${ua.aggressor.max-candidates-per-cycle:4}")
    int aggressorMaxCandidates;

    // Request-equivalents per scan cycle (BID_ASK pages count double, see
    // HistoricalRequestBudget). One budget spans a whole scheduled watchlist sweep;
    // an on-demand scan gets its own fresh budget — see computeActivity.
    @Value("${ua.aggressor.max-requests-per-cycle:20}")
    int aggressorMaxRequests;

    @Value("${ua.aggressor.sweep-window-ms:500}")
    long aggressorSweepWindowMs;

    @Value("${ua.aggressor.block-min-contracts:100}")
    long aggressorBlockMinContracts;

    // Spread-leg markers matched (case-insensitive contains) against specialConditions.
    // The code set is exchange-dependent and poorly documented — this default is the
    // documented single/multi-leg-of-combo mnemonics; the definitive list is built from
    // our own wire observations via the UA_AGGRESSOR_CONDITIONS debug log below.
    @Value("${ua.aggressor.spread-condition-markers:SLAN,SLAI,SLCN,SLFT,MLET,MLAT,MLCT,MLFT,TLET,TLAT,TLCT,TLFT,MESL,TESL}")
    List<String> aggressorSpreadMarkers;

    // Session start (ET) for the historical tick window. Options print 09:30–16:15 ET;
    // useRth=0 in the fetch keeps anything outside that window if it exists.
    @Value("${ua.aggressor.session-start:09:30}")
    String aggressorSessionStart;

    private final IbkrMarketDataService ibkrService;
    private final QuoteService quoteService;
    private final CacheManager cacheManager;
    private final MarketStateService marketStateService;
    private final MarketDataBook book;
    private final SubscriptionManager subscriptionManager;

    /** Serializes scans per ticker: the Book's scan fields expect a single writer, and a
     *  concurrent duplicate scan of the same ticker would only double the IBKR request load. */
    private final Map<String, Object> scanLocks = new java.util.concurrent.ConcurrentHashMap<>();

    public OptionActivityService(IbkrMarketDataService ibkrService, QuoteService quoteService,
                                  CacheManager cacheManager, MarketStateService marketStateService,
                                  MarketDataBook book, SubscriptionManager subscriptionManager) {
        this.ibkrService = ibkrService;
        this.quoteService = quoteService;
        this.cacheManager = cacheManager;
        this.marketStateService = marketStateService;
        this.book = book;
        this.subscriptionManager = subscriptionManager;
    }

    /**
     * The effective stage-2 config of THIS process, logged once at startup. Diagnostic for
     * config-source confusion: the packaged application.properties can be silently REPLACED
     * by an external spring.config.location / config/ directory next to the jar — then a
     * flag flipped in src/main/resources never reaches the runtime and stage 2 stays off
     * without any error (observed 2026-07-10: jar contained enabled=true, process ran with
     * the code default false). One glance at this line settles where the config came from.
     */
    @jakarta.annotation.PostConstruct
    void logAggressorConfig() {
        log.info("UA_AGGRESSOR_CONFIG enabled={} maxCandidatesPerCycle={} maxRequestEquivalentsPerCycle={} "
                        + "sessionStartEt={} sweepWindowMs={} blockMinContracts={} spreadMarkers={}",
                aggressorEnabled, aggressorMaxCandidates, aggressorMaxRequests,
                aggressorSessionStart, aggressorSweepWindowMs, aggressorBlockMinContracts,
                aggressorSpreadMarkers);
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

    /**
     * Runs one scan and writes the results into the Book (timestamped) so snapshot assembly
     * can read them synchronously and label their age honestly. Serialized per ticker.
     *
     * On-demand scans get their own fresh stage-2 budget; the scheduled watchlist sweep
     * passes ONE shared budget across all its tickers so a full sweep cannot exceed
     * ua.aggressor.max-requests-per-cycle request-equivalents against IBKR's historical
     * pacing window (~60 per 10 minutes, BID_ASK counting double).
     */
    public OptionActivityResult computeActivity(String ticker) {
        return computeActivity(ticker, new HistoricalRequestBudget(aggressorMaxRequests));
    }

    public OptionActivityResult computeActivity(String ticker, HistoricalRequestBudget aggressorBudget) {
        String upperTicker = ticker.toUpperCase();
        synchronized (scanLocks.computeIfAbsent(upperTicker, k -> new Object())) {
            OptionActivityResult result = doComputeActivity(upperTicker, aggressorBudget);
            // Only a scan that actually resolved strikes overwrites the Book: a failed scan
            // (IBKR down, no chain, no underlying price) yields an empty profile, and writing
            // that would erase the last honest result with a wrongly-fresh nothing. The Book
            // keeps last knowns; their age says how old they are.
            if (!result.oiProfile().isEmpty()) {
                TickerBook tb = book.book(upperTicker);
                Instant now = Instant.now();
                tb.unusualActivity().update(List.copyOf(result.unusualActivity()), now);
                tb.oiProfile().update(List.copyOf(result.oiProfile()), now);
            }
            return result;
        }
    }

    /**
     * Background scan of the whole watchlist (the anchor carries no UA scan — it exists for
     * liveness, not analysis). Decouples scan cost from the request path: snapshots read the
     * Book, this loop refreshes it.
     */
    @Scheduled(initialDelayString = "${book.scan-initial-delay-ms:30000}",
               fixedDelayString = "${book.scan-interval-ms:300000}")
    public void scheduledWatchlistScan() {
        // One stage-2 budget for the whole sweep — see computeActivity javadoc.
        HistoricalRequestBudget aggressorBudget = new HistoricalRequestBudget(aggressorMaxRequests);
        for (String symbol : subscriptionManager.bookSymbols()) {
            if (symbol.equals(subscriptionManager.anchorSymbol())) continue;
            try {
                OptionActivityResult result = computeActivity(symbol, aggressorBudget);
                log.info("Scheduled UA/OI scan for {}: {} flagged, {} strikes",
                        symbol, result.unusualActivity().size(), result.oiProfile().size());
            } catch (Exception e) {
                log.warn("Scheduled UA/OI scan for {} failed: {}", symbol, e.getMessage());
            }
        }
    }

    private OptionActivityResult doComputeActivity(String ticker, HistoricalRequestBudget aggressorBudget) {
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

        topUnusual = escalateAggressor(upper, topUnusual, aggressorBudget);

        List<OptionsData.OiLevel> sortedOiProfile = oiProfile.stream()
                .sorted(Comparator.comparingDouble(OptionsData.OiLevel::strike))
                .collect(Collectors.toList());

        return new OptionActivityResult(topUnusual, sortedOiProfile);
    }

    // -------------------------------------------------------------------------
    // UA stage 2 — per-contract aggressor classification (escalation)
    // -------------------------------------------------------------------------

    /**
     * Escalation stage: the expensive trade-by-trade analysis runs ONLY on the candidates
     * the cheap stage-1 filter already flagged. Candidates are the top
     * {@code ua.aggressor.max-candidates-per-cycle} entries by PREMIUM notional (actual
     * money spent — strike notional overstates cheap OTM contracts; entries without a
     * premium rank last). The shared per-cycle budget is checked per candidate: once it is
     * exhausted, remaining candidates carry an explicit SKIPPED_BUDGET profile instead of
     * silently missing one. Disabled (default) = the flagged list passes through untouched,
     * bit for bit — hard constraint of the stage.
     */
    List<OptionsData.UnusualActivity> escalateAggressor(String ticker,
            List<OptionsData.UnusualActivity> flagged, HistoricalRequestBudget budget) {
        if (!aggressorEnabled || flagged.isEmpty() || budget == null) {
            return flagged;
        }

        List<OptionsData.UnusualActivity> candidates = flagged.stream()
                .sorted(Comparator.comparing(OptionsData.UnusualActivity::premiumNotionalUsd,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(aggressorMaxCandidates, 0))
                .collect(Collectors.toList());

        AggressorClassifier.Config cfg = new AggressorClassifier.Config(
                aggressorSweepWindowMs, aggressorBlockMinContracts, aggressorSpreadMarkers);
        ZonedDateTime sessionStart = ZonedDateTime.of(
                LocalDate.now(US_MARKET_TZ),
                java.time.LocalTime.parse(aggressorSessionStart),
                US_MARKET_TZ);

        // Minimum viable slice: 1 TRADES page (1) + 1 BID_ASK page (2). Below that a
        // candidate cannot produce a single classifiable window and the equivalents are
        // better left to the pool.
        final int minViableSlice = 3;
        String todayExpiry = LocalDate.now(US_MARKET_TZ)
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);

        Map<OptionsData.UnusualActivity, AggressorProfile> profiles = new HashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            OptionsData.UnusualActivity ua = candidates.get(i);
            String right = "PUT".equals(ua.type()) ? "P" : "C";
            String contractLabel = ticker + " " + ua.expiry() + " " + ua.strike() + " " + right;

            // Fair share: each candidate may use at most an equal split of what is left
            // (remaining / candidates still to serve). The slice CAPS, it does not reserve —
            // whatever a candidate leaves unused flows back to the pool and grows the share
            // of everyone after it. Prevents the observed starvation where the first (=
            // largest) candidate ate the whole cycle budget and the rest got SKIPPED_BUDGET.
            int share = budget.remaining() / (candidates.size() - i);
            if (share < minViableSlice) {
                profiles.put(ua, AggressorProfile.skippedBudget());
                log.info("UA_AGGRESSOR ticker={} contract={} SKIPPED_BUDGET (share={} < {} viable)",
                        ticker, contractLabel, share, minViableSlice);
                continue;
            }

            IbkrDayTicks ticks = ibkrService.fetchDayTicks(ticker, ua.expiry(), ua.strike(), right,
                    sessionStart, budget.slice(share));
            if (ticks == null) {
                log.info("UA_AGGRESSOR ticker={} contract={} no tick data (IBKR not connected)",
                        ticker, contractLabel);
                continue; // no profile — honest absence, not a fabricated empty one
            }

            logDistinctSpecialConditions(contractLabel, ticks);

            AggressorProfile profile = AggressorClassifier.classify(
                    ticks.trades(), ticks.quotes(), cfg, ua.volume(),
                    ticks.tradesPartial(), ticks.quoteCoverage());

            if (todayExpiry.equals(ua.expiry())) {
                // 0DTE: position and expiry coincide — next-session OI never exists, the
                // inference is STRUCTURALLY impossible, which is different from a memory
                // gap. Say so instead of UNKNOWN.
                profile = profile.withOiJoin(null, AggressorProfile.INFERENCE_EXPIRES_TODAY);
            } else {
                Long todayOi = ua.openInterest();
                Long previousOi = previousSessionContractOi(ticker, ua.expiry(), ua.strike(), right);
                Long oiDelta = (todayOi != null && previousOi != null) ? todayOi - previousOi : null;
                profile = profile.withOiJoin(oiDelta, AggressorClassifier.positionInference(
                        profile.buyVolume(), profile.sellVolume(), oiDelta));
            }

            profiles.put(ua, profile);
            log.info("UA_AGGRESSOR ticker={} contract={} requests={} coverage={} classified={} buy={} sell={} unknown={} "
                            + "sweeps={} blocks={} oiDelta={} inference={} status={}",
                    ticker, contractLabel, ticks.requestEquivalentsUsed(),
                    // Locale.ROOT: this line is the verification instrument for the live
                    // SPY check — it must grep the same on a de-DE host as on en-US.
                    profile.tickCoverage() == null ? "n/a"
                            : String.format(java.util.Locale.ROOT, "%.2f", profile.tickCoverage()),
                    profile.classifiedShare() == null ? "n/a"
                            : String.format(java.util.Locale.ROOT, "%.2f", profile.classifiedShare()),
                    profile.buyVolume(), profile.sellVolume(), profile.unknownVolume(),
                    profile.sweepCount(), profile.blockCount(),
                    profile.oiDelta(), profile.positionInference(), profile.status());
        }

        // Original ratio-descending order preserved; only the profile field changes.
        return flagged.stream()
                .map(ua -> profiles.containsKey(ua) ? ua.withAggressorProfile(profiles.get(ua)) : ua)
                .collect(Collectors.toList());
    }

    /** First sessions instrument (see spec): the spread-marker code set is exchange-dependent
     *  and poorly documented — every distinct specialConditions string observed on the wire
     *  is logged so the definitive ua.aggressor.spread-condition-markers list can be built
     *  from reality instead of documentation. */
    private void logDistinctSpecialConditions(String contractLabel, IbkrDayTicks ticks) {
        if (!log.isDebugEnabled()) return;
        Set<String> distinct = new LinkedHashSet<>();
        for (AggressorClassifier.Trade t : ticks.trades()) {
            if (t.specialConditions() != null && !t.specialConditions().isBlank()) {
                distinct.add(t.specialConditions());
            }
        }
        if (!distinct.isEmpty()) {
            log.debug("UA_AGGRESSOR_CONDITIONS contract={} distinct={}", contractLabel, distinct);
        }
    }

    // -------------------------------------------------------------------------
    // Per-contract OI day memory (stage-2 OI-delta join)
    // -------------------------------------------------------------------------

    /**
     * Written on every scan regardless of ua.aggressor.enabled — a pure cache write with
     * zero effect on stage-1 results, so the memory is already warm on the day stage 2 is
     * first switched on. Key embeds the NY trading date; the value is the OI as published
     * that morning (i.e. positions as of the PREVIOUS close — see the epoch caveat on
     * AggressorProfile).
     */
    private void recordContractOi(String ticker, String expiry, double strike, String right, Long openInterest) {
        if (openInterest == null) return;
        Cache cache = cacheManager.getCache("oiContractDayMemory");
        if (cache == null) return;
        cache.put(contractOiKey(ticker, expiry, strike, right, LocalDate.now(US_MARKET_TZ)), openInterest);
    }

    /** The most recent previous session's published OI, looking back up to 5 calendar days
     *  (weekend + holiday). Null when nothing is remembered — oiDelta then stays null and
     *  positionInference honestly says UNKNOWN. */
    private Long previousSessionContractOi(String ticker, String expiry, double strike, String right) {
        Cache cache = cacheManager.getCache("oiContractDayMemory");
        if (cache == null) return null;
        LocalDate today = LocalDate.now(US_MARKET_TZ);
        for (int daysBack = 1; daysBack <= 5; daysBack++) {
            Long oi = cache.get(contractOiKey(ticker, expiry, strike, right, today.minusDays(daysBack)), Long.class);
            if (oi != null) return oi;
        }
        return null;
    }

    private static String contractOiKey(String ticker, String expiry, double strike, String right, LocalDate day) {
        return ticker + ":" + expiry + ":" + strike + ":" + right + ":" + EXPIRY_FMT.format(day);
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
                        AggressorFeatures agg = classifyAggressor(result);
                        log.info("Options activity for {} {} {} {}: flagged, aggressor={} lastLocation={} lastAge={}s",
                                ticker, expiry, strike, right, agg.aggressor(), agg.lastLocation(), agg.lastAgeSeconds());
                        unusual.add(new OptionsData.UnusualActivity(
                                expiry, strike, type, result.volume, result.openInterest,
                                result.ratio, result.bid, result.ask, result.last,
                                premiumNotional, null,
                                agg.lastLocation(), agg.aggressor(), agg.lastAgeSeconds()));
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
                                  Double bid, Double ask, Double last,
                                  Double bidAtLast, Double askAtLast, Long lastTimestampEpoch) {}

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

    /** Raw aggressor features for one contract — see UnusualActivity javadoc for semantics. */
    private record AggressorFeatures(Double lastLocation, String aggressor, Long lastAgeSeconds) {}

    /**
     * Quote-rule trade classification (Lee/Ready): where inside the bid-ask spread did the
     * last trade print? Near the ask = buyer lifted the offer (aggressive buying), near the
     * bid = seller hit the bid (aggressive selling). Two integrity rules:
     *
     * 1. The comparison quote is the pair FROZEN at the moment the last tick arrived
     *    (IbkrOptionContractActivity.Builder#last). Fallback to the end-of-window pair only
     *    when no freeze happened — never one frozen side mixed with one end-of-window side,
     *    that compares prices from two different moments.
     * 2. A last print older than {@code aggressorStaleSeconds} is classified STALE, not
     *    located: the quote has moved on since that trade, and a location computed against
     *    today's quote would be fiction. lastLocation is still emitted for STALE (the
     *    analysis layer may weigh it), but the label refuses to pretend it's current.
     *
     * UNKNOWN when there is no last, no two-sided quote, or a crossed quote (ask <= bid) —
     * same quality-gate philosophy as persistence: refuse to classify rather than guess.
     */
    private AggressorFeatures classifyAggressor(ContractResult r) {
        Long age = null;
        if (r.lastTimestampEpoch != null && r.lastTimestampEpoch > 0) {
            age = Math.max(0L, java.time.Instant.now().getEpochSecond() - r.lastTimestampEpoch);
        }

        Double bid, ask;
        if (r.bidAtLast != null && r.askAtLast != null) {
            bid = r.bidAtLast;
            ask = r.askAtLast;
        } else {
            bid = r.bid;
            ask = r.ask;
        }

        if (r.last == null || bid == null || ask == null || ask <= bid) {
            return new AggressorFeatures(null, "UNKNOWN", age);
        }

        double location = (r.last - bid) / (ask - bid);
        location = Math.max(0.0, Math.min(1.0, location));
        double rounded = Math.round(location * 10000.0) / 10000.0;

        String aggressor;
        if (age != null && age > aggressorStaleSeconds) {
            aggressor = "STALE";
        } else if (location >= 0.75) {
            aggressor = "AT_ASK";
        } else if (location <= 0.25) {
            aggressor = "AT_BID";
        } else {
            aggressor = "MID";
        }
        return new AggressorFeatures(rounded, aggressor, age);
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
        Double bidAtLast = null, askAtLast = null;
        Long lastTs = null;

        try {
            if (cachedOI != null && marketClosed) {
                // Closed market: cached OI is the complete answer, a volume fetch can only time out.
                return new ContractResult(null, cachedOI, null, null, null, null, null, null, null);
            } else if (cachedOI != null) {
                // OI doesn't move intraday — reuse it, only ask IBKR for a fresh volume read.
                // Price ticks (bid/ask/last) ride on the same request as default ticks.
                openInterest = cachedOI;
                IbkrOptionContractActivity act = fetchWithRetry(ticker, expiry, strike, right, false);
                volume = act != null ? act.volume() : null;
                if (act != null) {
                    bid = act.bid(); ask = act.ask(); last = act.last();
                    bidAtLast = act.bidAtLast(); askAtLast = act.askAtLast(); lastTs = act.lastTimestampEpoch();
                }
            } else {
                IbkrOptionContractActivity act = fetchWithRetry(ticker, expiry, strike, right, true);
                if (act == null) return null;
                volume = act.volume();
                openInterest = act.openInterest();
                bid = act.bid(); ask = act.ask(); last = act.last();
                bidAtLast = act.bidAtLast(); askAtLast = act.askAtLast(); lastTs = act.lastTimestampEpoch();
                if (openInterest != null && oiCache != null) {
                    oiCache.put(contractKey, openInterest);
                }
            }
        } catch (com.trading.marketdata.ibkr.IbkrContractNotFoundException e) {
            if (invalidCache != null) invalidCache.put(contractKey, true);
            return null;
        }

        // Stage-2 OI day memory — today's published OI per contract, so tomorrow's scan can
        // compute the delta. Deliberately unconditional (see recordContractOi javadoc).
        recordContractOi(ticker, expiry, strike, right, openInterest);

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
                // The frozen pair and trade timestamp belong to a specific last print, so
                // they travel WITH the last they were frozen against — backfilling a lone
                // bidAtLast next to the first attempt's last would pair a quote with a
                // trade it never met.
                if (refetch != null) {
                    if (bid == null) bid = refetch.bid();
                    if (ask == null) ask = refetch.ask();
                    if (last == null && refetch.last() != null) {
                        last = refetch.last();
                        bidAtLast = refetch.bidAtLast();
                        askAtLast = refetch.askAtLast();
                        lastTs = refetch.lastTimestampEpoch();
                    }
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
            return new ContractResult(volume, openInterest, null, bid, ask, last, bidAtLast, askAtLast, lastTs);
        }

        double ratio = (double) volume / openInterest;
        log.info("Options activity check {} {} {} {}: volume={}, openInterest={}, ratio={}",
                ticker, expiry, strike, right, volume, openInterest, String.format("%.2f", ratio));
        return new ContractResult(volume, openInterest, ratio, bid, ask, last, bidAtLast, askAtLast, lastTs);
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
