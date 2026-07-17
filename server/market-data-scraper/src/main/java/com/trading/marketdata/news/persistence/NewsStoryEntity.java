package com.trading.marketdata.news.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="news_story", indexes={@Index(name="idx_story_updated", columnList="last_updated_at")})
public class NewsStoryEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="story_key",nullable=false,unique=true,length=64) private String storyKey;
 @Column(name="representative_headline",nullable=false,length=1024) private String representativeHeadline;
 @Column(name="first_seen_at",nullable=false) private Instant firstSeenAt;
 @Column(name="last_updated_at",nullable=false) private Instant lastUpdatedAt;
 @Column(name="article_count",nullable=false) private int articleCount;
 @Column(name="affected_tickers",length=512) private String affectedTickers;
 @Column(name="event_type",length=64) private String eventType;
 @Column(name="materiality",length=16) private String materiality;
 @Column(name="direction",length=16) private String direction;
 @Column(name="confidence") private Double confidence;
 @Column(name="classifier_model",length=128) private String classifierModel;
 @Column(name="classifier_prompt_version",length=128) private String classifierPromptVersion;
 @Column(name="requires_recondensation",nullable=false) private boolean requiresRecondensation;
 public Long getId(){return id;} public String getStoryKey(){return storyKey;} public void setStoryKey(String v){storyKey=v;}
 public String getRepresentativeHeadline(){return representativeHeadline;} public void setRepresentativeHeadline(String v){representativeHeadline=v;}
 public Instant getFirstSeenAt(){return firstSeenAt;} public void setFirstSeenAt(Instant v){firstSeenAt=v;} public Instant getLastUpdatedAt(){return lastUpdatedAt;} public void setLastUpdatedAt(Instant v){lastUpdatedAt=v;}
 public int getArticleCount(){return articleCount;} public void setArticleCount(int v){articleCount=v;} public String getAffectedTickers(){return affectedTickers;} public void setAffectedTickers(String v){affectedTickers=v;}
 public String getEventType(){return eventType;} public void setEventType(String v){eventType=v;} public String getMateriality(){return materiality;} public void setMateriality(String v){materiality=v;}
 public String getDirection(){return direction;} public void setDirection(String v){direction=v;} public Double getConfidence(){return confidence;} public void setConfidence(Double v){confidence=v;}
 public String getClassifierModel(){return classifierModel;} public void setClassifierModel(String v){classifierModel=v;} public String getClassifierPromptVersion(){return classifierPromptVersion;} public void setClassifierPromptVersion(String v){classifierPromptVersion=v;}
 public boolean isRequiresRecondensation(){return requiresRecondensation;} public void setRequiresRecondensation(boolean v){requiresRecondensation=v;}
}