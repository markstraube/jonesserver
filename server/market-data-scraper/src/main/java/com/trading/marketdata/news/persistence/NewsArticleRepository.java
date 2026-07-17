package com.trading.marketdata.news.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface NewsArticleRepository extends JpaRepository<NewsArticleEntity, Long> {
    Optional<NewsArticleEntity> findByCanonicalKey(String canonicalKey);
    long countByStoryId(Long storyId);
}
