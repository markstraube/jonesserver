package com.trading.marketdata.news.persistence;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.Optional;
public interface CondensedNewsStateRepository extends JpaRepository<CondensedNewsStateEntity,Long>{ Optional<CondensedNewsStateEntity> findFirstByScopeKeyOrderByGeneratedAtDesc(String scopeKey); }