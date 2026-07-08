package com.trading.marketdata.service;

import com.trading.marketdata.ibkr.IbkrAuctionResult;
import com.trading.marketdata.ibkr.IbkrMarketDataService;
import com.trading.marketdata.model.AuctionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Nasdaq auction/NOII data (opening + closing cross imbalance) via IBKR Generic Tick 225.
 *
 * Window gating: NOII is only disseminated in two short windows per session (opening cross
 * from ~09:28 ET, closing cross from ~15:50 ET). Outside those windows the subscription is
 * guaranteed silent, so the default snapshot path skips the request entirely instead of
 * paying the full collection window per ticker for guaranteed-null data. The gate is
 * deliberately wider than the dissemination windows (config below) so clock skew or an
 * earlier-than-documented feed start can't clip real data.
 *
 * The gate checks WALL-CLOCK TIME ONLY, not trading-day validity: on a holiday the windows
 * "open" but the feed is silent and the result is empty — harmless, self-limiting, and it
 * avoids duplicating holiday logic that MarketStateService already owns for persistence.
 *
 * The force flag (standalone /auction endpoint) bypasses the gate for live verification
 * of the tick mapping at any time of day.
 *
 * No caching: inside the windows the imbalance updates continuously and the whole point is
 * polling it at high frequency; outside the windows the gate makes the call free anyway.
 */
@Service
public class AuctionService {

    private static final Logger log = LoggerFactory.getLogger(AuctionService.class);
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final IbkrMarketDataService ibkrService;

    @Value("${auction.enabled:true}")
    private boolean enabled;

    /** How long each request streams before harvesting the collected ticks. */
    @Value("${auction.collect-window-ms:2500}")
    private long collectWindowMs;

    /** Opening-cross gate, ET, format HH:mm-HH:mm. */
    @Value("${auction.opening-window:09:20-09:36}")
    private String openingWindow;

    /** Closing-cross gate, ET, format HH:mm-HH:mm. */
    @Value("${auction.closing-window:15:45-16:02}")
    private String closingWindow;

    public AuctionService(IbkrMarketDataService ibkrService) {
        this.ibkrService = ibkrService;
    }

    /**
     * Auction data for the given ticker, or null when disabled, gated out (outside both
     * windows and force=false), not connected, or hard-errored. With @JsonInclude NON_NULL
     * on MarketSnapshot a null here keeps the snapshot JSON clean outside auction windows.
     */
    public AuctionData getAuctionData(String ticker, boolean force) {
        if (!enabled) return null;
        if (!force && !isInsideAuctionWindow()) {
            return null;
        }

        IbkrAuctionResult result = ibkrService.fetchAuctionData(ticker, collectWindowMs);
        if (result == null) return null;

        return new AuctionData(
                ticker,
                result.auctionPrice(),
                result.auctionVolume(),
                result.imbalance(),
                result.regulatoryImbalance(),
                "ibkr",
                !result.isEmpty(),
                Instant.now()
        );
    }

    boolean isInsideAuctionWindow() {
        LocalTime nowEt = LocalTime.now(NEW_YORK);
        return isInside(nowEt, openingWindow) || isInside(nowEt, closingWindow);
    }

    private boolean isInside(LocalTime now, String window) {
        try {
            int dash = window.indexOf('-');
            LocalTime from = LocalTime.parse(window.substring(0, dash).trim(), HHMM);
            LocalTime to   = LocalTime.parse(window.substring(dash + 1).trim(), HHMM);
            return !now.isBefore(from) && !now.isAfter(to);
        } catch (Exception e) {
            // Misconfigured window must not silently kill the feature: fail open (window
            // considered active), pay the collection window, and log loudly once per call.
            log.warn("auction window config '{}' unparseable ({}) — treating as active", window, e.getMessage());
            return true;
        }
    }
}
