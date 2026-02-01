package com.straube.jones.service;


import com.straube.jones.dto.ai.AIResponseChunk;
import com.straube.jones.dto.ai.LLMRequest;
import com.straube.jones.dto.ai.LLMResponseChunk;
import com.straube.jones.service.llm.LLMProvider;
import com.straube.jones.service.llm.LLMProviderFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class LLMService
{
    private final LLMProviderFactory factory;

    public LLMService(LLMProviderFactory factory)
    {
        this.factory = factory;
    }


    public Flux<AIResponseChunk> streamResponse(LLMRequest request)
    {
        LLMProvider provider = factory.getProvider();
        return provider.streamCompletion(request).map(chunk -> {
            if (chunk.isFinished())
            {
                return AIResponseChunk.builder()
                                      .type("complete")
                                      .metadata(java.util.Map.of("tokensUsed", chunk.getTokensUsed()))
                                      .build();
            }
            else
            {
                return AIResponseChunk.chunk(chunk.getContent());
            }
        });
    }
}
