package com.trading.marketdata.service;

import com.trading.marketdata.book.MarketDataBook;
import com.trading.marketdata.book.TickerBook;
import com.trading.marketdata.book.TimestampedField;
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
 * Nasdaq auction/NOII data (opening + closing cross imbalance) read from the Book, fed by
 * the permanent streaming subscription (Generic Tick 225 → tick types 34/35/36/61).
 *
 * Window gating changed meaning with the Book rebuild: the stream is ALWAYS on (it shares
 * the symbol's single market-data line), so the windows no longer gate *requests* — they
 * gate *interpretation*. Nasdaq disseminates NOII only in two short windows per session
 * (opening cross from ~09:28 ET, closing cross from ~15:50 ET); outside them the auction
 * ticks are silent or placeholder zeros from IBKR, so Book values are only presented as
 * available when we are inside a window AND the ticks actually arrived within it (age
 * check). The gates are deliberately wider than the dissemination windows so clock skew or
 * an earlier-than-documented feed start can't clip real data.
 *
 * The gates check WALL-CLOCK TIME ONLY, not trading-day validity: on a holiday the windows
 * "open" but the feed stays silent, the ages stay old, and dataAvailable stays false —
 * harmless, self-limiting, no duplicated holiday logic.
 *
 * The force flag (standalone /auction endpoint) bypasses the interpretation gate and
 * returns whatever the Book holds, at any age — for live verification of the tick mapping
 * (in particular the still-unverified sign semantics of tick 36, see IbkrWrapper).
 */
@Service
public class AuctionService {

    private static final Logger log = LoggerFactory.getLogger(AuctionService.class);
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final MarketDataBook book;

    @Value("${auction.enabled:true}")
    private boolean enabled;

    /** Opening-cross gate, ET, format HH:mm-HH:mm. */
    @Value("${auction.opening-window:09:20-09:36}")
    private String openingWindow;

    /** Closing-cross gate, ET, format HH:mm-HH:mm. */
    @Value("${auction.closing-window:15:45-16:02}")
    private String closingWindow;

    /** Auction ticks older than this are not treated as belonging to the current window. */
    @Value("${auction.max-age-seconds:180}")
    private long maxAgeSeconds;

    public AuctionService(MarketDataBook book) {
        this.book = book;
    }

    /**
     * Auction data for the given ticker, or null when disabled, gated out (outside both
     * windows and force=false), or the ticker has no Book entry (only Book symbols carry
     * the 225 stream). With @JsonInclude NON_NULL on MarketSnapshot a null here keeps the
     * snapshot JSON clean outside auction windows.
     */
    public AuctionData getAuctionData(String ticker, boolean force) {
        if (!enabled) return null;
        String upper = ticker.toUpperCase();
        TickerBook tb = book.find(upper);
        if (tb == null) return null;

        boolean insideWindow = isInsideAuctionWindow();
        if (!force && !insideWindow) {
            return null;
        }

        // Freshness: a value only counts as current auction data when its tick arrived
        // recently — leftovers from the opening cross must not masquerade as closing-cross
        // data (and vice versa). force bypasses this too (raw last-knowns, honestly aged).
        Instant now = Instant.now();
        boolean fresh = isFresh(tb.auctionPrice().get(), now)
                || isFresh(tb.auctionVolume().get(), now)
                || isFresh(tb.imbalance().get(), now)
                || isFresh(tb.regulatoryImbalance().get(), now);

        boolean dataAvailable = insideWindow && fresh && !book.isConnectionLost();
        if (!force && !dataAvailable) {
            // Inside a window but the feed hasn't spoken (yet): report honestly-empty
            // instead of resurfacing the previous cross's numbers.
            return new AuctionData(upper, null, null, null, null, "ibkr", false, now);
        }

        AuctionData data = new AuctionData(
                upper,
                tb.auctionPrice().value(),
                tb.auctionVolume().value(),
                tb.imbalance().value(),
                tb.regulatoryImbalance().value(),
                "ibkr",
                dataAvailable,
                now
        );
        if (dataAvailable) {
            log.info("Auction data for {}: price={}, volume={}, imbalance={}, regImbalance={}",
                    upper, data.auctionPrice(), data.auctionVolume(),
                    data.imbalance(), data.regulatoryImbalance());
        }
        return data;
    }

    private boolean isFresh(TimestampedField.Stamped<?> s, Instant now) {
        Long age = s.ageSeconds(now);
        return age != null && age <= maxAgeSeconds && !s.invalidated();
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
            // considered active) and log loudly once per call.
            log.warn("auction window config '{}' unparseable ({}) — treating as active", window, e.getMessage());
            return true;
        }
    }
}
