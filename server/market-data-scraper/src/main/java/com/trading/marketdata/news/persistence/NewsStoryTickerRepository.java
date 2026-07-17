package com.trading.marketdata.news.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface NewsStoryTickerRepository extends JpaRepository<NewsStoryTickerEntity, Long> {
    Optional<NewsStoryTickerEntity> findByStoryIdAndTicker(Long storyId, String ticker);
    List<NewsStoryTickerEntity> findByStoryId(Long storyId);

    @Query("select st.storyId from NewsStoryTickerEntity st where st.ticker = :ticker")
    List<Long> findStoryIdsByTicker(@Param("ticker") String ticker);
}
