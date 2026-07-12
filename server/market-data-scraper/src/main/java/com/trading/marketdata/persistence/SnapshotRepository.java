package com.trading.marketdata.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SnapshotRepository extends JpaRepository<SnapshotEntity, Long> {

    /** Most recent persisted snapshot for a ticker — the reference for delta computation. */
    Optional<SnapshotEntity> findTopByTickerOrderBySnapshotTsDesc(String ticker);

    /** (snapshotTs, volume) series for the intraday volume curve — one indexed range scan
     *  over (ticker, snapshotTs); rows with null volume are filtered at the source. */
    @Query("select s.snapshotTs, s.volume from SnapshotEntity s "
            + "where s.ticker = ?1 and s.snapshotTs >= ?2 and s.volume is not null "
            + "order by s.snapshotTs")
    List<Object[]> findVolumeSeries(String ticker, Instant from);
}
