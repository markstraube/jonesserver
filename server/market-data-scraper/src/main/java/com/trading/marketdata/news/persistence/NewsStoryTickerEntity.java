package com.trading.marketdata.news.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "news_story_ticker",
        uniqueConstraints = @UniqueConstraint(name = "uk_story_ticker", columnNames = {"story_id", "ticker"}),
        indexes = @Index(name = "idx_story_ticker_symbol", columnList = "ticker,story_id"))
public class NewsStoryTickerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "story_id", nullable = false)
    private Long storyId;
    @Column(nullable = false, length = 12)
    private String ticker;

    public Long getId() { return id; }
    public Long getStoryId() { return storyId; }
    public void setStoryId(Long storyId) { this.storyId = storyId; }
    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }
}
