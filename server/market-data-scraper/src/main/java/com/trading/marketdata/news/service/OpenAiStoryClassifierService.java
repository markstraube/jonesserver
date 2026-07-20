package com.trading.marketdata.news.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.trading.marketdata.news.persistence.NewsArticleEntity;
import com.trading.marketdata.news.persistence.NewsStoryEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OpenAiStoryClassifierService {
    public enum Direction { POSITIVE, NEGATIVE, MIXED, NEUTRAL, UNCLEAR }
    public enum Materiality { CRITICAL, HIGH, MEDIUM, LOW }
    public enum EventType {
        EARNINGS, GUIDANCE, ANALYST_RATING, INSIDER_BUY, INSIDER_SELL,
        PRICE_MOVEMENT, SECTOR_MOVEMENT, MEMORY_PRICING, SUPPLY_DEMAND,
        REGULATION, CAPITAL_MARKETS, PRODUCT, GENERAL_COMMENTARY, OTHER
    }

    public static final class Output {
        @JsonPropertyDescription("Existing story id, or null when this is a new independent story")
        public Long matchingStoryId;
        public double confidence;
        public Direction direction;
        public EventType eventType;
        public Materiality materiality;
        public boolean requiresRecondensation;
        public List<String> affectedTickers;
    }

    private final ObjectProvider<OpenAIClient> clients;
    private final PromptResourceLoader prompts;

    @Value("${news.classifier.model:gpt-5.4-mini}")
    String model;
    @Value("${news.classifier.prompt-version:market-news-classifier-v1}")
    String configuredPromptVersion;

    public OpenAiStoryClassifierService(ObjectProvider<OpenAIClient> clients, PromptResourceLoader prompts) {
        this.clients = clients;
        this.prompts = prompts;
    }

    public Output classify(NewsArticleEntity article, String sourceTicker, List<NewsStoryEntity> candidates) {
        OpenAIClient client = clients.getIfAvailable();
        if (client == null) return null;

        String body = article.getFullText() == null ? "" : article.getFullText();
        if (body.length() > 10_000) body = body.substring(0, 10_000);
        String existing = candidates.stream()
                .map(this::candidateDescription)
                .reduce("", (a, b) -> a + "\n" + b);

        String input = prompts.load(effectivePromptVersion())
                + "\nSource ticker: " + sourceTicker
                + "\nHeadline: " + article.getHeadline()
                + "\nArticle: " + body
                + "\nCandidate stories:" + existing;

        StructuredResponseCreateParams<Output> params = ResponseCreateParams.builder()
                .model(model)
                .input(input)
                .text(Output.class)
                .build();

        return client.responses().create(params).output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .findFirst()
                .orElseThrow();
    }

    private String candidateDescription(NewsStoryEntity story) {
        return "ID=" + story.getId()
                + " | headline=" + nullSafe(story.getRepresentativeHeadline())
                + " | eventType=" + nullSafe(story.getEventType())
                + " | direction=" + nullSafe(story.getDirection())
                + " | materiality=" + nullSafe(story.getMateriality())
                + " | articleCount=" + story.getArticleCount()
                + " | affectedTickers=" + nullSafe(story.getAffectedTickers());
    }

    private String effectivePromptVersion() {
        // Existing deployments explicitly configured v1. Upgrade that legacy value to the
        // stricter v2 semantics while keeping custom future prompt versions configurable.
        return "market-news-classifier-v1".equals(configuredPromptVersion)
                ? "market-news-classifier-v2"
                : configuredPromptVersion;
    }

    private static String nullSafe(Object value) {
        return value == null ? "" : value.toString();
    }

    public String model() { return model; }
    public String promptVersion() { return effectivePromptVersion(); }
}
