package com.trading.marketdata.service;

import com.trading.marketdata.persistence.SnapshotPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Persists watchlist snapshots on the service's OWN clock — data collection decoupled from
 * data consumption. Without this job, MySQL history only grew when a consumer polled the
 * REST API: a paused downstream pipeline meant no volume-curve days ever qualified (the
 * curve needs snapshot series reaching 15:30 ET), the OI warm-up source thinned out, and
 * delta references aged arbitrarily.
 *
 * Gating:
 *  - market state: runs in PRE / REGULAR / POST — PRE captures the freshly published OI
 *    (struck overnight, delivered before the open) and the pre-market tape; POST captures
 *    the final volume that pins the curve's close. CLOSED and UNKNOWN skip: a frozen
 *    weekend Book adds rows, not information. UNKNOWN skips deliberately — it means the
 *    state source is down, and "probably closed" rows would be mislabeled either way.
 *  - freshness: a ticker whose latest persisted snapshot is younger than min-age is
 *    skipped, so an actively polling consumer (which persists through the same assembly)
 *    does not get doubled rows — the job only fills the gaps the consumer leaves.
 */
@Component
public class ScheduledSnapshotJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledSnapshotJob.class);

    private final SnapshotAssemblyService assemblyService;
    private final SnapshotPersistenceService persistenceService;
    private final MarketStateService marketStateService;

    @Value("${snapshot.job.enabled:true}")
    boolean enabled = true;

    @Value("${book.watchlist:}")
    List<String> watchlist = List.of();

    /** Skip a ticker whose latest snapshot is younger than this — dedupe against an
     *  actively polling consumer. */
    @Value("${snapshot.job.min-age-ms:120000}")
    long minAgeMs = 120_000;

    public ScheduledSnapshotJob(SnapshotAssemblyService assemblyService,
                                SnapshotPersistenceService persistenceService,
                                MarketStateService marketStateService) {
        this.assemblyService = assemblyService;
        this.persistenceService = persistenceService;
        this.marketStateService = marketStateService;
    }

    @Scheduled(initialDelayString = "${snapshot.job.initial-delay-ms:60000}",
               fixedDelayString = "${snapshot.job.interval-ms:300000}")
    public void persistWatchlist() {
        if (!enabled || watchlist.isEmpty()) return;
        MarketStateService.MarketState state = marketStateService.getMarketState();
        if (state != MarketStateService.MarketState.PRE
                && state != MarketStateService.MarketState.REGULAR
                && state != MarketStateService.MarketState.POST) {
            return;
        }

        int persisted = 0, skippedFresh = 0, failed = 0;
        for (String ticker : watchlist) {
            String upper = ticker.trim().toUpperCase();
            if (upper.isEmpty()) continue;
            try {
                boolean fresh = persistenceService.findPrevious(upper)
                        .map(prev -> Duration.between(prev.getSnapshotTs(), Instant.now()).toMillis() < minAgeMs)
                        .orElse(false);
                if (fresh) {
                    skippedFresh++;
                    continue;
                }
                assemblyService.build(upper); // persists internally (@Async)
                persisted++;
            } catch (Exception e) {
                // One ticker's failure (scraper hiccup, transient DB issue) must not stop
                // the rest of the watchlist — the next cycle retries anyway.
                failed++;
                log.warn("SNAPSHOT_JOB ticker={} failed: {}", upper, e.getMessage());
            }
        }
        log.info("SNAPSHOT_JOB state={} persisted={} skippedFresh={} failed={}",
                state, persisted, skippedFresh, failed);
    }
}
