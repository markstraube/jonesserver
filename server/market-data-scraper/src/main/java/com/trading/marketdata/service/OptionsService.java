package com.trading.marketdata.service;

import com.trading.marketdata.book.MarketDataBook;
import com.trading.marketdata.book.TickerBook;
import com.trading.marketdata.model.OptionsData;
import com.trading.marketdata.scraper.BarchartScraper;
import com.trading.marketdata.scraper.ScraperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class OptionsService {

    private static final Logger log = LoggerFactory.getLogger(OptionsService.class);

    private final MarketDataBook book;
    private final OptionActivityService optionActivityService;
    private final BarchartScraper barchartScraper;

    public OptionsService(MarketDataBook book,
                          OptionActivityService optionActivityService,
                          BarchartScraper barchartScraper) {
        this.book = book;
        this.optionActivityService = optionActivityService;
        this.barchartScraper = barchartScraper;
    }

    // NOTE on this cache: it exists for the UA/OI scan below (an expensive multi-request
    // IBKR poll), NOT for the metrics — those are free synchronous Book reads. It is
    // removed in the Book rebuild's final phase when the scanner writes into the Book.
    @Cacheable(value = "options", key = "#ticker",
               unless = "#result.ivRank == null && #result.putCallRatio == null && #result.maxPain == null")
    public OptionsData getOptions(String ticker) {
        String upper = ticker.toUpperCase();

        Double putCallRatio  = null;
        Double ivRank        = null;
        Double ivPercentile  = null;
        Double iv            = null;
        Double hv            = null;
        Double maxPain       = null;
        List<OptionsData.UnusualActivity> unusualActivity = List.of();
        List<OptionsData.OiLevel> oiProfile = List.of();
        String source        = "ibkr";

        // --- Primary: the Book (IV/HV/PCR stream in on the permanent subscription; PCR is
        // computed from option-volume ticks 29/30 — IBKR has no direct PCR tick). Non-Book
        // tickers have no entry here and fall through to the Barchart fallback below. ---
        TickerBook tb = book.find(upper);
        if (tb != null) {
            putCallRatio = tb.putCallRatio();
            iv = tb.impliedVolatility().value();
            hv = tb.historicalVolatility().value();
            log.info("Book options metrics for {}: putCallRatio={}, iv={}, hv={}",
                    upper, putCallRatio, iv, hv);
        }

        // --- Primary: IBKR-computed unusual activity + OI profile (chain discovery + per-contract vol/OI) ---
        // Replaces the old Barchart scrape entirely as the first choice — see OptionActivityService.
        try {
            OptionActivityService.OptionActivityResult activity = optionActivityService.computeActivity(upper);
            unusualActivity = activity.unusualActivity();
            oiProfile = activity.oiProfile();
            log.info("IBKR options activity for {}: {} contracts flagged, {} strikes in OI profile",
                    upper, unusualActivity.size(), oiProfile.size());
        } catch (Exception e) {
            log.warn("IBKR options activity computation failed for {}: {}", upper, e.getMessage());
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
                source,
                null,
                true,
                Instant.now()
        );
    }
}
