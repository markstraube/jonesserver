package com.straube.jones.config;


import com.straube.jones.service.llm.AnthropicProvider;
import com.straube.jones.service.llm.LLMProvider;
import com.straube.jones.service.llm.LLMProviderFactory;
import com.straube.jones.service.llm.OpenAIProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Configuration
public class LLMConfig
{

    @Bean
    public OpenAIProvider openAIProvider(AIAssistantConfig config, WebClient.Builder webClientBuilder)
    {
        return new OpenAIProvider(config, webClientBuilder);
    }


    @Bean
    public AnthropicProvider anthropicProvider(AIAssistantConfig config, WebClient.Builder webClientBuilder)
    {
        return new AnthropicProvider(config, webClientBuilder);
    }


    @Bean
    public LLMProviderFactory llmProviderFactory(List<LLMProvider> providers, AIAssistantConfig config)
    {
        return new LLMProviderFactory(providers, config);
    }
}
