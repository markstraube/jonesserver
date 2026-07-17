package com.trading.marketdata.news.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface NewsArticleTickerRepository extends JpaRepository<NewsArticleTickerEntity, Long> {
    Optional<NewsArticleTickerEntity> findByArticleIdAndTicker(Long articleId, String ticker);
}
