package com.trading.marketdata.news.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NewsStoryRepository extends JpaRepository<NewsStoryEntity, Long> {
    Optional<NewsStoryEntity> findByStoryKey(String storyKey);
    List<NewsStoryEntity> findByLastUpdatedAtGreaterThanEqualOrderByLastUpdatedAtDesc(Instant since);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO news_story
              (story_key, representative_headline, first_seen_at, last_updated_at,
               article_count, requires_recondensation)
            VALUES (:storyKey, :headline, :firstSeenAt, :lastUpdatedAt, 0, false)
            """, nativeQuery = true)
    int insertIgnore(@Param("storyKey") String storyKey,
                     @Param("headline") String headline,
                     @Param("firstSeenAt") Instant firstSeenAt,
                     @Param("lastUpdatedAt") Instant lastUpdatedAt);
}
