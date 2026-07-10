package com.trading.marketdata.service;

import com.trading.marketdata.book.MarketDataBook;
import com.trading.marketdata.book.TickerBook;
import com.trading.marketdata.book.TimestampedField;
import com.trading.marketdata.model.DataQuality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Derives the snapshot's {@link DataQuality} block from the Book's timestamp pairs.
 *
 * Staleness rules per section (market-state-aware — silence outside REGULAR is stillness,
 * not failure):
 *   quote / optionsMetrics / uaScan: stale when the connection is lost, when no member
 *       field has ever been seen, or when the market is REGULAR and the freshest member
 *       field's lastSeenAt exceeds the section's threshold.
 *   auction: NOII is silent by design outside the two dissemination windows, so age-based
 *       staleness is only judged INSIDE a window; outside, only a lost connection makes it
 *       stale. (dataAvailable in the auction payload already encodes window semantics.)
 */
@Service
public class SnapshotQualityService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotQualityService.class);

    private final MarketDataBook book;
    private final MarketStateService marketStateService;
    private final AuctionService auctionService;

    @Value("${book.ticker-stale-seconds:120}")
    private long quoteStaleSeconds;

    /** IV/HV/option-volume ticks are far sparser than trades — their own, wider threshold. */
    @Value("${book.metrics-stale-seconds:600}")
    private long metricsStaleSeconds;

    @Value("${auction.max-age-seconds:180}")
    private long auctionMaxAgeSeconds;

    /** A UA/OI scan result older than this is flagged stale (it is still served, honestly aged). */
    @Value("${book.scan-stale-seconds:1800}")
    private long scanStaleSeconds;

    public SnapshotQualityService(MarketDataBook book,
                                  MarketStateService marketStateService,
                                  AuctionService auctionService) {
        this.book = book;
        this.marketStateService = marketStateService;
        this.auctionService = auctionService;
    }

    /** DataQuality for a ticker, or null when it has no Book entry (scraper-served tickers). */
    public DataQuality forTicker(String ticker) {
        TickerBook tb = book.find(ticker.toUpperCase());
        if (tb == null) return null;

        boolean lost = book.isConnectionLost();
        boolean regular = marketStateService.getMarketState() == MarketStateService.MarketState.REGULAR;
        boolean insideAuctionWindow = auctionService.isInsideAuctionWindow();
        Instant now = Instant.now();

        DataQuality.Section quote = section(now, lost, regular, quoteStaleSeconds,
                tb.last(), tb.bid(), tb.ask(), tb.open(), tb.high(), tb.low(), tb.close(), tb.volume());
        DataQuality.Section metrics = section(now, lost, regular, metricsStaleSeconds,
                tb.impliedVolatility(), tb.historicalVolatility(), tb.callOptionVolume(), tb.putOptionVolume());
        DataQuality.Section auction = section(now, lost, insideAuctionWindow, auctionMaxAgeSeconds,
                tb.auctionPrice(), tb.auctionVolume(), tb.imbalance(), tb.regulatoryImbalance());
        DataQuality.Section uaScan = section(now, lost, regular, scanStaleSeconds,
                tb.unusualActivity(), tb.oiProfile());

        return new DataQuality(lost, tb.marketDataType(), quote, metrics, auction, uaScan);
    }

    /**
     * @param ageGate when true, an age beyond {@code staleSeconds} marks the section stale
     *                (REGULAR market for stream sections, inside-window for auction)
     */
    private DataQuality.Section section(Instant now, boolean connectionLost, boolean ageGate,
                                        long staleSeconds, TimestampedField<?>... fields) {
        Long age = null;
        Long changedAge = null;
        boolean anySeen = false;
        boolean allSeenInvalidated = true;
        boolean anyNotSubscribed = false;
        for (TimestampedField<?> field : fields) {
            TimestampedField.Stamped<?> s = field.get();
            Long a = s.ageSeconds(now);
            if (a != null) {
                anySeen = true;
                if (age == null || a < age) age = a;
                if (!s.invalidated()) allSeenInvalidated = false;
            }
            Long c = s.changedAgeSeconds(now);
            if (c != null && (changedAge == null || c < changedAge)) changedAge = c;
            if (s.notSubscribed()) anyNotSubscribed = true;
        }
        boolean invalidated = anySeen && allSeenInvalidated;
        boolean stale = connectionLost
                || !anySeen
                || invalidated
                || (ageGate && age != null && age > staleSeconds);
        return new DataQuality.Section(age, changedAge, stale,
                anySeen ? invalidated : null,
                anyNotSubscribed ? true : null);
    }
}
