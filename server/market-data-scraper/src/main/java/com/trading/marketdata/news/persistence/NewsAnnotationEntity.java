package com.trading.marketdata.news.persistence;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "news_article_annotation", uniqueConstraints =
        @UniqueConstraint(name = "uk_annotation_article_prompt", columnNames = {"article_id", "prompt_version"}))
public class NewsAnnotationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "article_id", nullable = false) private Long articleId;
    @Column(nullable = false, length = 24) private String direction;
    @Column(nullable = false) private double confidence;
    @Column(name = "event_type", length = 48) private String eventType;
    @Column(length = 24) private String materiality;
    @Column(name = "time_horizon", length = 24) private String timeHorizon;
    @Column(columnDefinition = "json") private String topicsJson;
    @Column(length = 1200) private String rationale;
    @Column(nullable = false, length = 80) private String model;
    @Column(name = "prompt_version", nullable = false, length = 80) private String promptVersion;
    @Column(name = "annotated_at", nullable = false) private Instant annotatedAt;

    public Long getId() { return id; }
    public Long getArticleId() { return articleId; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getMateriality() { return materiality; }
    public void setMateriality(String materiality) { this.materiality = materiality; }
    public String getTimeHorizon() { return timeHorizon; }
    public void setTimeHorizon(String timeHorizon) { this.timeHorizon = timeHorizon; }
    public String getTopicsJson() { return topicsJson; }
    public void setTopicsJson(String topicsJson) { this.topicsJson = topicsJson; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public Instant getAnnotatedAt() { return annotatedAt; }
    public void setAnnotatedAt(Instant annotatedAt) { this.annotatedAt = annotatedAt; }
}
