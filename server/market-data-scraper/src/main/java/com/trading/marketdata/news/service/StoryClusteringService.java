package com.trading.marketdata.news.service;

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
public class StoryClusteringService {
    private static final Logger log = LoggerFactory.getLogger(StoryClusteringService.class);

    private final NewsStoryRepository stories;
    private final NewsStoryTickerRepository storyTickers;
    private final NewsArticleRepository articles;
    private final OpenAiStoryClassifierService classifier;

    @Value("${news.classifier.enabled:false}")
    boolean enabled;

    public StoryClusteringService(NewsStoryRepository stories,
                                  NewsStoryTickerRepository storyTickers,
                                  NewsArticleRepository articles,
                                  OpenAiStoryClassifierService classifier) {
        this.stories = stories;
        this.storyTickers = storyTickers;
        this.articles = articles;
        this.classifier = classifier;
    }

    @Transactional
    public synchronized NewsStoryEntity assignOnce(NewsArticleEntity article, String sourceTicker) {
        if (article.getStoryId() != null) {
            return stories.findById(article.getStoryId()).orElseThrow();
        }

        String deterministicKey = deterministicStoryKey(article.getHeadline());
        Optional<NewsStoryEntity> exact = stories.findByStoryKey(deterministicKey);
        if (exact.isPresent()) {
            NewsStoryEntity story = attach(exact.get(), article, sourceTicker, null, false);
            log.info("NEWS_CLASSIFIER_SKIPPED articleId={} reason=DETERMINISTIC_STORY storyId={}", article.getId(), story.getId());
            return story;
        }

        List<NewsStoryEntity> candidates = stories
                .findByLastUpdatedAtGreaterThanEqualOrderByLastUpdatedAtDesc(Instant.now().minus(Duration.ofHours(72)))
                .stream().limit(12).toList();

        OpenAiStoryClassifierService.Output output = null;
        if (enabled) {
            try {
                log.info("NEWS_CLASSIFIER_CALL articleId={} candidateStories={}", article.getId(), candidates.size());
                output = classifier.classify(article, sourceTicker, candidates);
            } catch (Exception e) {
                log.warn("NEWS_CLASSIFIER_FAILED articleId={} message={}", article.getId(), e.getMessage());
            }
        }

        NewsStoryEntity story = null;
        boolean newStory = false;
        if (output != null && output.matchingStoryId != null) {
            story = stories.findById(output.matchingStoryId).orElse(null);
        }
        if (story == null) {
            story = createStoryAtomically(deterministicKey, article);
            newStory = true;
        }

        story = attach(story, article, sourceTicker, output, newStory);
        log.info("NEWS_CLASSIFIER_RESULT articleId={} storyId={} newStory={} confidence={}",
                article.getId(), story.getId(), newStory, output == null ? null : output.confidence);
        return story;
    }

    private NewsStoryEntity createStoryAtomically(String storyKey, NewsArticleEntity article) {
        Instant firstSeen = article.getFirstSeenAt() == null ? Instant.now() : article.getFirstSeenAt();
        int inserted = stories.insertIgnore(storyKey, article.getHeadline(), firstSeen, firstSeen);
        NewsStoryEntity story = stories.findByStoryKey(storyKey).orElseThrow();
        if (inserted > 0) {
            log.info("NEWS_STORY_CREATED storyId={} storyKey={}", story.getId(), storyKey);
        }
        return story;
    }

    private NewsStoryEntity attach(NewsStoryEntity story,
                                   NewsArticleEntity article,
                                   String sourceTicker,
                                   OpenAiStoryClassifierService.Output output,
                                   boolean newStory) {
        article.setStoryId(story.getId());
        articles.save(article);

        addStoryTicker(story.getId(), sourceTicker);
        if (output != null && output.affectedTickers != null) {
            output.affectedTickers.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(s -> s.toUpperCase(Locale.ROOT))
                    .forEach(t -> addStoryTicker(story.getId(), t));
        }

        story.setLastUpdatedAt(Instant.now());
        story.setArticleCount(Math.toIntExact(articles.countByStoryId(story.getId())));

        // A story's canonical classification is set when created. Later articles may add tickers
        // and may escalate a critical re-condensation flag, but do not rewrite prior semantics.
        if (newStory && output != null) {
            story.setDirection(output.direction == null ? null : output.direction.name());
            story.setEventType(output.eventType == null ? null : output.eventType.name());
            story.setMateriality(output.materiality == null ? null : output.materiality.name());
            story.setConfidence(clamp(output.confidence));
            story.setClassifierModel(classifier.model());
            story.setClassifierPromptVersion(classifier.promptVersion());
            story.setRequiresRecondensation(output.requiresRecondensation);
        } else if (output != null && output.requiresRecondensation) {
            story.setRequiresRecondensation(true);
        }

        story.setAffectedTickers(joinTickers(story.getId()));
        NewsStoryEntity saved = stories.save(story);
        log.info("NEWS_STORY_ATTACHED articleId={} storyId={} articleCount={} tickers={}",
                article.getId(), saved.getId(), saved.getArticleCount(), saved.getAffectedTickers());
        return saved;
    }

    private void addStoryTicker(Long storyId, String ticker) {
        if (ticker == null || ticker.isBlank()) return;
        String normalized = ticker.trim().toUpperCase(Locale.ROOT);
        if (storyTickers.findByStoryIdAndTicker(storyId, normalized).isPresent()) return;
        NewsStoryTickerEntity link = new NewsStoryTickerEntity();
        link.setStoryId(storyId);
        link.setTicker(normalized);
        try {
            storyTickers.saveAndFlush(link);
        } catch (DataIntegrityViolationException ignored) {
            // Another parallel path already attached the same ticker.
        }
    }

    private String joinTickers(Long storyId) {
        return storyTickers.findByStoryId(storyId).stream()
                .map(NewsStoryTickerEntity::getTicker)
                .distinct().sorted().reduce((a, b) -> a + "," + b).orElse(null);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String deterministicStoryKey(String headline) {
        String normalized = headline.toLowerCase(Locale.ROOT)
                .replaceAll("\\b(update|updated|breaking|exclusive)\\b", " ")
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
