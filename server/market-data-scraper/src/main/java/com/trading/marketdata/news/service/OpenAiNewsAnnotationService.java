package com.trading.marketdata.news.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.trading.marketdata.news.model.NewsAnnotation;
import com.trading.marketdata.news.persistence.NewsArticleEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class OpenAiNewsAnnotationService {

    public static final class AnnotationOutput {
        @JsonPropertyDescription("POSITIVE, NEGATIVE, MIXED or NEUTRAL for the specified ticker.")
        public String direction;
        @JsonPropertyDescription("Confidence from 0.0 to 1.0.")
        public double confidence;
        @JsonPropertyDescription("Compact event type such as EARNINGS, GUIDANCE, ANALYST, SUPPLY, DEMAND, REGULATION, MARKET_MOVE or OTHER.")
        public String eventType;
        @JsonPropertyDescription("LOW, MEDIUM or HIGH materiality for the specified ticker.")
        public String materiality;
        @JsonPropertyDescription("INTRADAY, SHORT_TERM, MEDIUM_TERM or LONG_TERM.")
        public String timeHorizon;
        @JsonPropertyDescription("Two to six compact topic labels.")
        public List<String> topics;
        @JsonPropertyDescription("One concise sentence explaining the causal effect on the specified ticker. Do not give trading advice.")
        public String rationale;
    }

    private final ObjectProvider<OpenAIClient> clientProvider;

    @Value("${news.annotation.model:gpt-5.4-mini}")
    private String model;
    @Value("${news.annotation.prompt-version:market-news-v1}")
    private String promptVersion;

    public OpenAiNewsAnnotationService(ObjectProvider<OpenAIClient> clientProvider) {
        this.clientProvider = clientProvider;
    }

    public NewsAnnotation annotate(NewsArticleEntity article) {
        OpenAIClient client = clientProvider.getIfAvailable();
        if (client == null) return null;

        String body = article.getFullText();
        if (body == null || body.isBlank()) body = "(No article body available.)";
        if (body.length() > 12_000) body = body.substring(0, 12_000);

        String input = """
                Classify the following financial news article.
                Separate language tone from economic effect. For example, supply constraints can be
                positive for a producer even when the wording sounds negative. Base the result only on
                the supplied text. MIXED is preferred when near-term and longer-term effects conflict.
                This is metadata classification, not investment advice.

                Headline: %s
                Source: %s
                Published at: %s
                Article:
                %s
                """.formatted(article.getHeadline(), article.getSource(),
                article.getPublishedAt(), body);

        StructuredResponseCreateParams<AnnotationOutput> params = ResponseCreateParams.builder()
                .input(input)
                .text(AnnotationOutput.class)
                .model(model)
                .build();

        AnnotationOutput output = client.responses().create(params).output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("OpenAI response contained no structured output"));

        return new NewsAnnotation(
                output.direction,
                clamp(output.confidence),
                output.eventType,
                output.materiality,
                output.timeHorizon,
                output.topics == null ? List.of() : List.copyOf(output.topics),
                output.rationale,
                model,
                promptVersion,
                Instant.now());
    }

    public String promptVersion() { return promptVersion; }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
