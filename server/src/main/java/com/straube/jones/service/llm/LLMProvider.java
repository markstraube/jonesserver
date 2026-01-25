package com.straube.jones.service.llm;

import com.straube.jones.dto.ai.LLMRequest;
import com.straube.jones.dto.ai.LLMResponseChunk;
import reactor.core.publisher.Flux;

public interface LLMProvider {
    Flux<LLMResponseChunk> streamCompletion(LLMRequest request);
    String getProviderName();
}
