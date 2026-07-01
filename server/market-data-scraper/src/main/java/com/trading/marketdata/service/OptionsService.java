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
    private final BarchartScraper barchartScraper;

    public OptionsService(IbkrMarketDataService ibkrService,
                          BarchartScraper barchartScraper) {
        this.ibkrService = ibkrService;
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
        String source        = "ibkr";

        // --- Primary: IBKR Generic Ticks ---
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

        // --- Fallback: Barchart for maxPain and any missing fields ---
        if (maxPain == null || putCallRatio == null) {
            try {
                OptionsData barchart = barchartScraper.fetchOptionsData(upper);
                log.info("Barchart for {}: putCallRatio={}, ivRank={}, maxPain={}",
                        upper, barchart.putCallRatio(), barchart.ivRank(), barchart.maxPain());
                if (putCallRatio == null) putCallRatio = barchart.putCallRatio();
                if (ivRank == null)       ivRank       = barchart.ivRank();
                if (ivPercentile == null) ivPercentile = barchart.ivPercentile();
                if (maxPain == null)      maxPain      = barchart.maxPain();
                if (putCallRatio != null || maxPain != null) source = "ibkr+barchart";
            } catch (Exception e) {
                log.warn("Barchart fallback failed for {}: {}", upper, e.getMessage());
            }
        }

        log.info("Final options for {}: putCallRatio={}, ivRank={}, ivPercentile={}, maxPain={}",
                upper, putCallRatio, ivRank, ivPercentile, maxPain);

        return new OptionsData(
                upper,
                putCallRatio,
                ivRank,
                ivPercentile,
                List.of(),   // unusualActivity — can be added later
                maxPain,
                source,
                null,
                true,
                Instant.now()
        );
    }
}
