package com.trading.marketdata.news.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NewsAnnotationRepository extends JpaRepository<NewsAnnotationEntity, Long> {
    Optional<NewsAnnotationEntity> findByArticleIdAndPromptVersion(Long articleId, String promptVersion);
    List<NewsAnnotationEntity> findByArticleIdInAndPromptVersion(Collection<Long> articleIds, String promptVersion);
}
