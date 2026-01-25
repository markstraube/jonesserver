package com.straube.jones.service.llm;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.straube.jones.config.AIAssistantConfig;
import com.straube.jones.exception.LLMException;

public class LLMProviderFactory {
    private final Map<String, LLMProvider> providerMap;
    private final AIAssistantConfig config;

    public LLMProviderFactory(List<LLMProvider> providers, AIAssistantConfig config) {
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(LLMProvider::getProviderName, Function.identity()));
        this.config = config;
    }

    public LLMProvider getProvider() {
        String providerName = config.getLlm().getProvider();
        LLMProvider provider = providerMap.get(providerName);
        if (provider == null) {
            throw new LLMException("Provider not found: " + providerName, "PROVIDER_NOT_FOUND", false);
        }
        return provider;
    }
}
