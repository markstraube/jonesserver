package com.trading.marketdata.news.config;
import org.springframework.context.annotation.Condition; import org.springframework.context.annotation.ConditionContext; import org.springframework.core.type.AnnotatedTypeMetadata;
public class OpenAiNewsEnabledCondition implements Condition { public boolean matches(ConditionContext c, AnnotatedTypeMetadata m){return Boolean.parseBoolean(c.getEnvironment().getProperty("news.classifier.enabled","false"))||Boolean.parseBoolean(c.getEnvironment().getProperty("news.condensation.enabled","false"));}}
