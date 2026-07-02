package com.trading.marketdata.service;

import com.trading.marketdata.ibkr.IbkrMarketDataService;
import com.trading.marketdata.ibkr.IbkrOptionsResult;
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

    private final IbkrMarketDataService ibkrService;
    private final OptionActivityService optionActivityService;
    private final BarchartScraper barchartScraper;

    public OptionsService(IbkrMarketDataService ibkrService,
                          OptionActivityService optionActivityService,
                          BarchartScraper barchartScraper) {
        this.ibkrService = ibkrService;
        this.optionActivityService = optionActivityService;
        this.barchartScraper = barchartScraper;
    }

    @Cacheable(value = "options", key = "#ticker",
               unless = "#result.ivRank == null && #result.putCallRatio == null && #result.maxPain == null")
    public OptionsData getOptions(String ticker) {
        String upper = ticker.toUpperCase();

        Double putCallRatio  = null;
        Double ivRank        = null;
        Double ivPercentile  = null;
        Double maxPain       = null;
        List<OptionsData.UnusualActivity> unusualActivity = List.of();
        List<OptionsData.OiLevel> oiProfile = List.of();
        String source        = "ibkr";

        // --- Primary: IBKR Generic Ticks (put/call ratio, IV, HV on the underlying) ---
        try {
            IbkrOptionsResult ibkr = ibkrService.fetchOptionsMetrics(upper);
            if (ibkr != null) {
                putCallRatio = ibkr.putCallRatio();
                // IV Rank is not directly available from IBKR; log IV for reference
                if (ibkr.impliedVolatility() != null) {
                    log.debug("IBKR IV for {}: {}%", upper,
                            String.format("%.1f", ibkr.impliedVolatility() * 100));
                }
            }
            log.info("IBKR options for {}: putCallRatio={}, iv={}, hv={}",
                    upper,
                    ibkr != null ? ibkr.putCallRatio() : null,
                    ibkr != null ? ibkr.impliedVolatility() : null,
                    ibkr != null ? ibkr.historicalVolatility() : null);
        } catch (Exception e) {
            log.warn("IBKR options metrics failed for {}: {}", upper, e.getMessage());
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
