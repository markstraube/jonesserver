package com.straube.jones.service.llm;

import com.straube.jones.config.AIAssistantConfig;
import com.straube.jones.dto.ai.LLMRequest;
import com.straube.jones.dto.ai.LLMResponseChunk;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

public class AnthropicProvider implements LLMProvider {
    private final AIAssistantConfig config;
    private final WebClient.Builder webClientBuilder;

    public AnthropicProvider(AIAssistantConfig config, WebClient.Builder webClientBuilder) {
        this.config = config;
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public Flux<LLMResponseChunk> streamCompletion(LLMRequest request) {
        // Implementation for Anthropic API would go here
        return Flux.error(new UnsupportedOperationException("Anthropic provider not yet implemented"));
    }

    @Override
    public String getProviderName() {
        return "anthropic";
    }
}
