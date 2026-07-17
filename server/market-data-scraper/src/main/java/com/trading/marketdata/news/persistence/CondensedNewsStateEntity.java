package com.trading.marketdata.news.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "condensed_news_state",
        indexes = @Index(name = "idx_condensed_scope_time", columnList = "scope_key,generated_at"))
public class CondensedNewsStateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "scope_key", nullable = false, length = 128)
    private String scopeKey;
    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;
    @Column(name = "window_start", nullable = false)
    private Instant windowStart;
    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;
    @Column(name = "story_watermark", nullable = false)
    private long storyWatermark;
    @Column(name = "model", length = 128)
    private String model;
    @Column(name = "prompt_version", length = 128)
    private String promptVersion;
    @Column(name = "trigger_type", nullable = false, length = 48)
    private String triggerType;
    @Column(name = "summary_json", columnDefinition = "longtext", nullable = false)
    private String summaryJson;

    public Long getId() { return id; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
    public Instant getWindowStart() { return windowStart; }
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }
    public Instant getWindowEnd() { return windowEnd; }
    public void setWindowEnd(Instant windowEnd) { this.windowEnd = windowEnd; }
    public long getStoryWatermark() { return storyWatermark; }
    public void setStoryWatermark(long storyWatermark) { this.storyWatermark = storyWatermark; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getSummaryJson() { return summaryJson; }
    public void setSummaryJson(String summaryJson) { this.summaryJson = summaryJson; }
}
