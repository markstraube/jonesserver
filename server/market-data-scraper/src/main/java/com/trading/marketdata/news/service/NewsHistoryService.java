package com.trading.marketdata.news.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.marketdata.model.NewsItem;
import com.trading.marketdata.news.model.NewsContext;
import com.trading.marketdata.news.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class NewsHistoryService {
    private static final Logger log = LoggerFactory.getLogger(NewsHistoryService.class);

    private final NewsArticleRepository articles;
    private final NewsArticleTickerRepository articleTickers;
    private final NewsStoryRepository stories;
    private final NewsStoryTickerRepository storyTickers;
    private final StoryClusteringService clustering;
    private final NewsCondensationService condensation;
    private final ObjectMapper mapper;

    @Value("${news.history.enabled:true}")
    boolean enabled;
    @Value("${news.history.window-hours:72}")
    int hours;
    @Value("${news.history.report-limit:20}")
    int limit;

    public NewsHistoryService(NewsArticleRepository articles,
                              NewsArticleTickerRepository articleTickers,
                              NewsStoryRepository stories,
                              NewsStoryTickerRepository storyTickers,
                              StoryClusteringService clustering,
                              NewsCondensationService condensation,
                              ObjectMapper mapper) {
        this.articles = articles;
        this.articleTickers = articleTickers;
        this.stories = stories;
        this.storyTickers = storyTickers;
        this.clustering = clustering;
        this.condensation = condensation;
        this.mapper = mapper;
    }

    public NewsContext ingestAndBuild(String ticker, List<NewsItem> incoming) {
        if (!enabled) return null;
        String normalizedTicker = ticker.toUpperCase(Locale.ROOT);
        try {
            ingest(normalizedTicker, incoming == null ? List.of() : incoming);
            return buildContext(normalizedTicker);
        } catch (Exception e) {
            log.warn("News context unavailable for {}: {}", normalizedTicker, e.getMessage(), e);
            return null;
        }
    }

    @Transactional
    protected synchronized void ingest(String ticker, List<NewsItem> incoming) {
        Instant now = Instant.now();
        for (NewsItem item : incoming) {
            if (item == null || item.headline() == null || item.headline().isBlank()) continue;

            String key = canonicalKey(item);
            NewsArticleEntity article = articles.findByCanonicalKey(key).orElse(null);
            boolean fresh = article == null;

            if (fresh) {
                article = new NewsArticleEntity();
                article.setCanonicalKey(key);
                article.setFirstSeenAt(now);
                article.setSeenCount(0);
            }

            article.setArticleId(item.articleId());
            article.setHeadline(item.headline());
            article.setSource(item.source());
            article.setUrl(item.url());
            if (item.fullText() != null && !item.fullText().isBlank()) article.setFullText(item.fullText());
            article.setPublishedAt(item.publishedAt() != null ? item.publishedAt() : now);
            article.setLastSeenAt(now);
            article.setSeenCount(article.getSeenCount() + 1);

            try {
                article = articles.saveAndFlush(article);
            } catch (DataIntegrityViolationException race) {
                article = articles.findByCanonicalKey(key).orElseThrow(() -> race);
                fresh = false;
            }

            attachArticleTicker(article.getId(), ticker, now);
            log.info("NEWS_INGEST articleId={} canonicalKey={} ticker={} result={}",
                    article.getId(), key, ticker, fresh ? "NEW" : "EXACT_DUPLICATE");

            if (article.getStoryId() == null) {
                NewsStoryEntity story = clustering.assignOnce(article, ticker);
                article.setStoryId(story.getId());
                articles.save(article);
            } else {
                log.info("NEWS_CLASSIFIER_SKIPPED articleId={} reason=ALREADY_CLASSIFIED storyId={}",
                        article.getId(), article.getStoryId());
            }
        }
    }

    private void attachArticleTicker(Long articleId, String ticker, Instant now) {
        if (articleTickers.findByArticleIdAndTicker(articleId, ticker).isPresent()) return;
        NewsArticleTickerEntity link = new NewsArticleTickerEntity();
        link.setArticleId(articleId);
        link.setTicker(ticker);
        link.setFirstSeenAt(now);
        try {
            articleTickers.saveAndFlush(link);
        } catch (DataIntegrityViolationException ignored) {
            // Idempotent under parallel ticker snapshots.
        }
    }

    private NewsContext buildContext(String ticker) {
        Instant now = Instant.now();
        CondensedNewsStateEntity state = condensation.latest().orElse(null);
        Instant since = now.minus(Duration.ofHours(hours));
        Instant deltaSince = state == null ? since : state.getGeneratedAt();
        Set<Long> permittedStoryIds = new HashSet<>(storyTickers.findStoryIdsByTicker(ticker));

        List<NewsStoryEntity> delta = stories
                .findByLastUpdatedAtGreaterThanEqualOrderByLastUpdatedAtDesc(since)
                .stream()
                .filter(s -> permittedStoryIds.contains(s.getId()))
                .filter(s -> s.getLastUpdatedAt() != null && s.getLastUpdatedAt().isAfter(deltaSince))
                .limit(Math.max(1, limit))
                .toList();

        NewsContext.CondensedState condensed = null;
        if (state != null) {
            Object summary;
            try {
                summary = mapper.readTree(state.getSummaryJson());
            } catch (Exception ignored) {
                summary = state.getSummaryJson();
            }
            condensed = new NewsContext.CondensedState(
                    state.getGeneratedAt(), state.getWindowStart(), state.getWindowEnd(),
                    state.getModel(), state.getPromptVersion(), state.getTriggerType(), summary);
        }

        Instant comparison = state == null ? Instant.EPOCH : state.getGeneratedAt();
        List<NewsContext.StoryDelta> storyDeltas = delta.stream()
                .map(s -> new NewsContext.StoryDelta(
                        s.getId(),
                        s.getFirstSeenAt().isAfter(comparison) ? "NEW" : "UPDATED",
                        s.getRepresentativeHeadline(), s.getFirstSeenAt(), s.getLastUpdatedAt(),
                        s.getArticleCount(), s.getAffectedTickers(), s.getDirection(), s.getEventType(),
                        s.getMateriality(), s.getConfidence(), s.getClassifierModel() != null))
                .toList();

        return new NewsContext(hours, now, condensed, storyDeltas);
    }

    private static String canonicalKey(NewsItem item) {
        if (item.articleId() != null && !item.articleId().isBlank()) {
            // Provider article IDs are global across ticker subscriptions.
            return sha("article-id:" + item.articleId().trim());
        }
        if (item.storyKey() != null && !item.storyKey().isBlank()) return sha("story:" + item.storyKey());
        if (item.url() != null && !item.url().isBlank()) return sha("url:" + normalizeUrl(item.url()));
        String normalized = item.headline().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return sha("headline:" + normalized);
    }

    private static String normalizeUrl(String url) {
        int query = url.indexOf('?');
        return query >= 0 ? url.substring(0, query) : url;
    }

    private static String sha(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
