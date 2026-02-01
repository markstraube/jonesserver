package com.straube.jones.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai.assistant")
public class AIAssistantConfig
{
    private ContextConfig context = new ContextConfig();
    private LLMConfigProperties llm = new LLMConfigProperties();

    @Data
    public static class ContextConfig
    {
        private int timeWindowMinutes = 60;
        private int maxSizeKb = 20;
    }

    @Data
    public static class LLMConfigProperties
    {
        private String provider = "openai";
        private int timeoutSeconds = 30;
        private String apiKey;
        private String model = "gpt-4"; // Default model
        private double temperature = 0.7;
    }
}
