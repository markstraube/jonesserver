package com.trading.marketdata.news.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "news_article_history",
        uniqueConstraints = @UniqueConstraint(name = "uk_news_canonical_key", columnNames = "canonical_key"),
        indexes = {
                @Index(name = "idx_news_published", columnList = "published_at"),
                @Index(name = "idx_news_last_seen", columnList = "last_seen_at"),
                @Index(name = "idx_news_story", columnList = "story_id")
        })
public class NewsArticleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "canonical_key", nullable = false, length = 64)
    private String canonicalKey;
    @Column(name = "article_id", length = 128)
    private String articleId;
    @Column(length = 1024, nullable = false)
    private String headline;
    @Column(length = 128)
    private String source;
    @Column(length = 2048)
    private String url;
    @Column(name = "full_text", columnDefinition = "longtext")
    private String fullText;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
    @Column(name = "seen_count", nullable = false)
    private int seenCount;
    @Column(name = "story_id")
    private Long storyId;

    public Long getId() { return id; }
    public String getCanonicalKey() { return canonicalKey; }
    public void setCanonicalKey(String canonicalKey) { this.canonicalKey = canonicalKey; }
    public String getArticleId() { return articleId; }
    public void setArticleId(String articleId) { this.articleId = articleId; }
    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getFullText() { return fullText; }
    public void setFullText(String fullText) { this.fullText = fullText; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public int getSeenCount() { return seenCount; }
    public void setSeenCount(int seenCount) { this.seenCount = seenCount; }
    public Long getStoryId() { return storyId; }
    public void setStoryId(Long storyId) { this.storyId = storyId; }
}
