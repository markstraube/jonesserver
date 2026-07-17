package com.trading.marketdata.news.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.ResponseCreateParams;
import com.trading.marketdata.news.persistence.NewsStoryEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OpenAiNewsCondenserService {
    private static final String PROMPT_VERSION = "market-news-condenser-v2";

    public static final class Output {
        @JsonPropertyDescription("Compact factual overview of the supplied stories") public String overview;
        @JsonPropertyDescription("Dominant current market narrative in one concise sentence") public String dominantTheme;
        @JsonPropertyDescription("Overall news sentiment: BULLISH, BEARISH, MIXED, or NEUTRAL") public String marketSentiment;
        @JsonPropertyDescription("Confidence in the overall market assessment from 0.0 to 1.0") public Double confidence;
        public List<String> activeNarratives;
        public List<String> contradictions;
        public List<String> newlyMaterialFacts;
        @JsonPropertyDescription("Evidence-based factors supportive of the covered market or sector") public List<String> bullishFactors;
        @JsonPropertyDescription("Evidence-based factors negative for the covered market or sector") public List<String> bearishFactors;
        @JsonPropertyDescription("Events or facts likely to matter materially to prices or fundamentals") public List<String> materialEvents;
        @JsonPropertyDescription("Unresolved developments, scheduled catalysts, or conditions to monitor") public List<String> watchItems;
        @JsonPropertyDescription("Canonical exchange tickers materially involved in the condensed state") public List<String> materialTickers;
    }

    private final ObjectProvider<OpenAIClient> clients;
    private final PromptResourceLoader prompts;
    private final ObjectMapper mapper;

    @Value("${news.condensation.model:gpt-5.4-mini}") String model;

    public OpenAiNewsCondenserService(ObjectProvider<OpenAIClient> clients,
                                      PromptResourceLoader prompts,
                                      ObjectMapper mapper,
                                      NewsTickerNormalizer ignored) {
        this.clients = clients;
        this.prompts = prompts;
        this.mapper = mapper;
    }

    public String condense(List<NewsStoryEntity> stories) {
        OpenAIClient client = clients.getIfAvailable();
        if (client == null) throw new IllegalStateException("OpenAI disabled");
        String data = stories.stream().map(this::storyLine).reduce("", (left, right) -> left + "\n" + right);
        ResponseCreateParams params = ResponseCreateParams.builder()
                .model(model)
                .input(prompts.load(PROMPT_VERSION) + "\nStories:" + data)
                .text(Output.class)
                .build();
        Output output = client.responses().create(params).output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .findFirst().orElseThrow();
        normalize(output);
        try {
            return mapper.writeValueAsString(output);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize condensed news state", e);
        }
    }

    private String storyLine(NewsStoryEntity story) {
        return "ID=" + story.getId()
                + " | headline=" + story.getRepresentativeHeadline()
                + " | tickers=" + NewsTickerNormalizer.normalizeCsv(story.getAffectedTickers())
                + " | direction=" + story.getDirection()
                + " | eventType=" + story.getEventType()
                + " | materiality=" + story.getMateriality()
                + " | confidence=" + story.getConfidence()
                + " | articles=" + story.getArticleCount();
    }

    static void normalize(Output output) {
        if (output.marketSentiment != null) {
            String normalized = output.marketSentiment.trim().toUpperCase();
            output.marketSentiment = switch (normalized) {
                case "BULLISH", "BEARISH", "MIXED", "NEUTRAL" -> normalized;
                default -> "MIXED";
            };
        }
        if (output.confidence != null) output.confidence = Math.max(0.0, Math.min(1.0, output.confidence));
        output.activeNarratives = safe(output.activeNarratives);
        output.contradictions = safe(output.contradictions);
        output.newlyMaterialFacts = safe(output.newlyMaterialFacts);
        output.bullishFactors = safe(output.bullishFactors);
        output.bearishFactors = safe(output.bearishFactors);
        output.materialEvents = safe(output.materialEvents);
        output.watchItems = safe(output.watchItems);
        output.materialTickers = NewsTickerNormalizer.normalizeAll(safe(output.materialTickers));
    }

    private static List<String> safe(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).distinct().toList();
    }

    public String model(){ return model; }
    public String promptVersion(){ return PROMPT_VERSION; }
}
