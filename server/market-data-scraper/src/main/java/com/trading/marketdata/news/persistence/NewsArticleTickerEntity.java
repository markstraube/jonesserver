package com.trading.marketdata.news.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "news_article_ticker",
        uniqueConstraints = @UniqueConstraint(name = "uk_article_ticker", columnNames = {"article_id", "ticker"}),
        indexes = @Index(name = "idx_article_ticker_symbol", columnList = "ticker,article_id"))
public class NewsArticleTickerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "article_id", nullable = false)
    private Long articleId;
    @Column(nullable = false, length = 12)
    private String ticker;
    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    public Long getId() { return id; }
    public Long getArticleId() { return articleId; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; }
}
