package com.trading.marketdata.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.marketdata.model.MarketSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Writes snapshots to the HOME-40 MySQL history and provides the previous-snapshot lookup
 * for delta computation. Two hard design rules:
 *
 * 1. The DB must NEVER break the API. Every DB access is wrapped; a dead database degrades
 *    to "no history" (deltas null, save skipped with a log line), the response still ships.
 * 2. Writes are @Async so response latency is independent of insert latency.
 *
 * Toggled via persistence.enabled — off by default until the datasource is configured.
 */
@Service
public class SnapshotPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotPersistenceService.class);

    private final SnapshotRepository repository;
    private final ObjectMapper objectMapper;

    @Value("${persistence.enabled:false}")
    private boolean enabled;

    public SnapshotPersistenceService(SnapshotRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Previous snapshot for delta computation; empty when disabled, absent, or DB unreachable. */
    public Optional<SnapshotEntity> findPrevious(String ticker) {
        if (!enabled) return Optional.empty();
        try {
            return repository.findTopByTickerOrderBySnapshotTsDesc(ticker);
        } catch (Exception e) {
            log.warn("Snapshot history lookup for {} failed ({}), continuing without deltas",
                    ticker, e.getMessage());
            return Optional.empty();
        }
    }

    @Async
    public void persist(MarketSnapshot snapshot) {
        if (!enabled || snapshot == null) return;

        // Quality gate — the response is never affected by this, only the history write.
        // Rule 1: never persist while the market is CLOSED. IBKR delivers unstable stale-tick
        // composites then (observed 2026-07-03: price 1000.00 without OHLC at 09:46, price
        // 976.63 with Wednesday's open at 12:00 — both fabrications), and a persisted phantom
        // row would poison the next session's first delta. UNKNOWN (Yahoo unreachable) is
        // deliberately NOT treated as CLOSED: fail-open, then rule 2 decides alone.
        if ("CLOSED".equals(snapshot.marketState())) {
            log.info("Skipping persistence for {}: market CLOSED (response unaffected)", snapshot.ticker());
            return;
        }
        // Rule 2 — field-granular for Book snapshots: a snapshot whose quote section has
        // ever been seen is persisted, with per-section ages/staleness recorded in
        // data_quality_json (a stale IV is persisted FLAGGED, not blocked; the delta logic
        // already refuses stale inputs). Only a quote section with no data at all is skipped
        // — there is nothing worth a row.
        if (snapshot.dataQuality() != null) {
            var quoteQuality = snapshot.dataQuality().quote();
            if (quoteQuality == null || quoteQuality.ageSeconds() == null) {
                log.info("Skipping persistence for {}: Book quote section has no data yet", snapshot.ticker());
                return;
            }
        } else if (snapshot.quote() != null
                && (snapshot.quote().volume() == null || snapshot.quote().volume() == 0)
                && snapshot.quote().open() == null) {
            // Non-Book (scraper-served) tickers keep the old all-or-nothing signature check:
            // zero/absent volume combined with a missing open does not occur during any live
            // session — that combination is the stale-tick signature regardless of what the
            // market-state lookup said.
            log.info("Skipping persistence for {}: inconsistent quote (volume={}, open=null)",
                    snapshot.ticker(), snapshot.quote().volume());
            return;
        }

        try {
            repository.save(toEntity(snapshot));
            log.debug("Persisted snapshot for {} at {}", snapshot.ticker(), snapshot.timestamp());
        } catch (Exception e) {
            log.warn("Persisting snapshot for {} failed ({}), response was served regardless",
                    snapshot.ticker(), e.getMessage());
        }
    }

    private SnapshotEntity toEntity(MarketSnapshot s) {
        SnapshotEntity e = new SnapshotEntity();
        e.setTicker(s.ticker());
        e.setSnapshotTs(s.timestamp());
        e.setMarketState(s.marketState());

        if (s.quote() != null) {
            e.setPrice(s.quote().price());
            e.setChangePct(s.quote().changePct());
            e.setOpen(s.quote().open());
            e.setHigh(s.quote().high());
            e.setLow(s.quote().low());
            e.setVolume(s.quote().volume());
        }
        if (s.derived() != null) {
            e.setPrevClose(s.derived().prevClose());
            e.setOiCallTotal(s.derived().oiCallTotal());
            e.setOiPutTotal(s.derived().oiPutTotal());
            e.setOiPutCallRatio(s.derived().oiPutCallRatio());
            e.setUaCallVolume(s.derived().uaCallVolume());
            e.setUaPutVolume(s.derived().uaPutVolume());
            e.setUaCallNotionalUsd(s.derived().uaCallNotionalUsd());
            e.setUaPutNotionalUsd(s.derived().uaPutNotionalUsd());
        }
        if (s.options() != null) {
            e.setPutCallRatioFlow(s.options().putCallRatio());
            e.setIv(s.options().iv());
            e.setHv(s.options().hv());
            e.setIvRank(s.options().ivRank());
            e.setMaxPain(s.options().maxPain());
            e.setOiProfileJson(toJson(s.options().oiProfile()));
            e.setUnusualActivityJson(toJson(s.options().unusualActivity()));
        }
        if (s.shortData() != null) {
            e.setShortFloat(s.shortData().shortFloat());
            e.setDaysToCover(s.shortData().daysToCover());
            e.setInstOwn(s.shortData().instOwn());
        }
        e.setNewsJson(toJson(s.news()));
        if (s.auction() != null && s.auction().dataAvailable()) {
            e.setAuctionPrice(s.auction().auctionPrice());
            e.setAuctionImbalance(s.auction().imbalance());
            e.setAuctionJson(toJson(s.auction()));
        }
        e.setDataQualityJson(toJson(s.dataQuality()));
        return e;
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.warn("JSON serialization for persistence failed: {}", ex.getMessage());
            return null;
        }
    }
}
