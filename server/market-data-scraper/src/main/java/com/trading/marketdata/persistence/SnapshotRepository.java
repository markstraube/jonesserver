package com.trading.marketdata.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SnapshotRepository extends JpaRepository<SnapshotEntity, Long> {

    /** Most recent persisted snapshot for a ticker — the reference for delta computation. */
    Optional<SnapshotEntity> findTopByTickerOrderBySnapshotTsDesc(String ticker);
}
