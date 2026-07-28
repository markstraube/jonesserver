package com.trading.marketdata.service;

import com.trading.marketdata.book.MarketDataBook;
import com.trading.marketdata.book.SubscriptionManager;
import com.trading.marketdata.book.TickerBook;
import com.trading.marketdata.book.TimestampedField;
import com.trading.marketdata.model.OptionsData;
import com.trading.marketdata.scraper.BarchartScraper;
import com.trading.marketdata.scraper.ScraperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class OptionsService {

    private static final Logger log = LoggerFactory.getLogger(OptionsService.class);

    private final MarketDataBook book;
    private final SubscriptionManager subscriptionManager;
    private final OptionActivityService optionActivityService;
    private final BarchartScraper barchartScraper;
    private final CollectorStatusRegistry collectorStatus;

    /** Non-watchlist tickers only: minimum age of the Book's scan result before an
     *  on-demand rescan. Watchlist tickers never rescan inline — the scheduled scanner
     *  owns their freshness and the snapshot labels the age via dataQuality. */
    @Value("${book.scan-refresh-seconds:120}")
    private long scanRefreshSeconds;

    public OptionsService(MarketDataBook book,
                          SubscriptionManager subscriptionManager,
                          OptionActivityService optionActivityService,
                          BarchartScraper barchartScraper,
                          CollectorStatusRegistry collectorStatus) {
        this.book = book;
        this.subscriptionManager = subscriptionManager;
        this.optionActivityService = optionActivityService;
        this.barchartScraper = barchartScraper;
        this.collectorStatus = collectorStatus;
    }

    public OptionsData getOptions(String ticker) {
        return getOptions(ticker, false);
    }

    /**
     * @param forceRefresh when true, bypasses the watchlist Book-only path and performs a
     * fresh option scan with its own stage-2 historical request budget. This is intended
     * for explicit diagnostic/deep-flow requests, not high-frequency polling.
     */
    public OptionsData getOptions(String ticker, boolean forceRefresh) {
        String upper = ticker.toUpperCase();
        CollectorStatusRegistry.Run snapshotRun = collectorStatus.start(upper, "marketSnapshot", java.util.List.of("ibkrConnection", "openInterest"));

        Double putCallRatio  = null;
        Double ivRank        = null;
        Double ivPercentile  = null;
        Double iv            = null;
        Double hv            = null;
        Double maxPain       = null;
        List<OptionsData.UnusualActivity> unusualActivity = List.of();
        List<OptionsData.OiLevel> oiProfile = List.of();
        String source        = "ibkr";

        // --- Metrics from the Book (IV/HV/PCR stream in on the permanent subscription; PCR
        // is computed from option-volume ticks 29/30 — IBKR has no direct PCR tick). Non-Book
        // tickers have no stream, their metrics come from the Barchart fallback below. ---
        TickerBook tb = book.find(upper);
        if (tb != null) {
            putCallRatio = tb.putCallRatio();
            iv = tb.impliedVolatility().value();
            hv = tb.historicalVolatility().value();
            log.debug("Book options metrics for {}: putCallRatio={}, iv={}, hv={}",
                    upper, putCallRatio, iv, hv);
        }

        // --- UA/OI scan results from the Book. Watchlist tickers are kept fresh by the
        // scheduled scanner and NEVER scanned inline (snapshot latency stays flat; age is
        // labeled honestly in dataQuality). Other tickers scan on demand, deduped via the
        // Book's own scan timestamp — this replaced the former 120s options cache. ---
        try {
            boolean watchlistTicker = subscriptionManager.isBookSymbol(upper);
            Long scanAge = tb != null ? tb.oiProfile().get().ageSeconds(Instant.now()) : null;
            if (forceRefresh || (!watchlistTicker && (scanAge == null || scanAge > scanRefreshSeconds))) {
                // computeActivity(ticker) creates a fresh per-request stage-2 budget instead
                // of inheriting the shared scheduled-watchlist budget that may already be
                // exhausted by earlier symbols in the sweep.
                optionActivityService.computeActivity(upper);
                tb = book.find(upper); // scan writes create/update the entry
            }
            if (tb != null) {
                TimestampedField.Stamped<List<OptionsData.UnusualActivity>> ua = tb.unusualActivity().get();
                TimestampedField.Stamped<List<OptionsData.OiLevel>> oi = tb.oiProfile().get();
                if (ua.value() != null) unusualActivity = ua.value();
                if (oi.value() != null) oiProfile = oi.value();
                log.info("Book options activity for {}: {} contracts flagged, {} strikes in OI profile, scanAgeSeconds={}",
                        upper, unusualActivity.size(), oiProfile.size(), oi.ageSeconds(Instant.now()));
            }
        } catch (Exception e) {
            log.warn("Options activity read/scan failed for {}: {}", upper, e.getMessage());
        }

        // --- Fallback: Barchart, only for whatever IBKR couldn't provide ---
        // maxPain has no IBKR equivalent at all (would need OI across the *entire* chain, not just
        // near-the-money strikes) so this branch still runs whenever maxPain is missing. unusualActivity
        // only falls back to Barchart if the IBKR-based computation above came back empty (e.g. not
        // connected) — Barchart is last-resort now, not first choice.
        if (maxPain == null || putCallRatio == null || unusualActivity.isEmpty()) {
            try {
                OptionsData barchart = barchartScraper.fetchOptionsData(upper);
                log.info("Barchart for {}: putCallRatio={}, ivRank={}, maxPain={}, unusualActivity={}",
                        upper, barchart.putCallRatio(), barchart.ivRank(), barchart.maxPain(),
                        barchart.unusualActivity() != null ? barchart.unusualActivity().size() : 0);
                if (putCallRatio == null) putCallRatio = barchart.putCallRatio();
                if (ivRank == null)       ivRank       = barchart.ivRank();
                if (ivPercentile == null) ivPercentile = barchart.ivPercentile();
                if (maxPain == null)      maxPain      = barchart.maxPain();
                if (unusualActivity.isEmpty() && barchart.unusualActivity() != null
                        && !barchart.unusualActivity().isEmpty()) {
                    unusualActivity = barchart.unusualActivity();
                }
                if (putCallRatio != null || maxPain != null) {
                    source = "ibkr+barchart";
                }
            } catch (Exception e) {
                log.warn("Barchart fallback failed for {}: {}", upper, e.getMessage());
            }
        }

        log.info("Final options for {}: putCallRatio={}, ivRank={}, ivPercentile={}, maxPain={}, unusualActivity={}",
                upper, putCallRatio, ivRank, ivPercentile, maxPain, unusualActivity.size());

        // Intraday pinning reference from our own scanned OI window (nearest expiry) —
        // deliberately separate from the full-chain maxPain a fallback source may provide;
        // see MaxPain's scope note.
        Double todayMaxPain = com.trading.marketdata.analysis.MaxPain.nearestExpiry(oiProfile);

        java.util.Map<String, Object> snapshotMetrics = java.util.Map.of(
                "oiLevels", oiProfile.size(),
                "unusualContracts", unusualActivity.size(),
                "source", source,
                "forceRefresh", forceRefresh,
                "itemsRequested", 1,
                "itemsProcessed", 1,
                "itemsSucceeded", oiProfile.isEmpty() ? 0 : 1,
                "itemsFailed", oiProfile.isEmpty() ? 1 : 0
        );
        if (oiProfile.isEmpty()) snapshotRun.partial(snapshotMetrics, "no OI profile available");
        else snapshotRun.ok(snapshotMetrics);

        CollectorStatusRegistry.Run oiRun = collectorStatus.start(upper, "openInterest", java.util.List.of("ibkrConnection", "contractCatalog"));
        long oiContracts = oiProfile.size() * 2L;
        java.util.Map<String, Object> oiMetrics = java.util.Map.of(
                "levels", oiProfile.size(),
                "contractsRepresented", oiContracts,
                "source", source,
                "itemsRequested", oiContracts,
                "itemsProcessed", oiContracts,
                "itemsSucceeded", oiContracts,
                "itemsFailed", 0
        );
        if (oiProfile.isEmpty()) oiRun.partial(oiMetrics, "no open-interest levels available");
        else oiRun.ok(oiMetrics);

        return new OptionsData(
                upper,
                putCallRatio,
                ivRank,
                ivPercentile,
                iv,
                hv,
                unusualActivity,
                oiProfile,
                maxPain,
                todayMaxPain,
                source,
                null,
                true,
                Instant.now()
        );
    }
}
