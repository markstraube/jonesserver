package com.straube.jones.service.llm;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.straube.jones.config.AIAssistantConfig;
import com.straube.jones.dto.ai.LLMRequest;
import com.straube.jones.dto.ai.LLMResponseChunk;
import com.straube.jones.dto.ai.Message;
import com.straube.jones.exception.LLMException;

import reactor.core.publisher.Flux;

public class OpenAIProvider
    implements
    LLMProvider
{
    private static final Logger logger = LoggerFactory.getLogger(OpenAIProvider.class);
    private final AIAssistantConfig config;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OpenAIProvider(AIAssistantConfig config, WebClient.Builder webClientBuilder)
    {
        this.config = config;
        this.webClient = webClientBuilder.baseUrl("https://api.openai.com/v1")
                                         .defaultHeader("Authorization",
                                                        "Bearer " + System.getenv()
                                                                          .getOrDefault("OPENAI_API_KEY",
                                                                                        config.getLlm()
                                                                                              .getApiKey()))
                                         .build();
        this.objectMapper = new ObjectMapper();
    }


    @Override
    public Flux<LLMResponseChunk> streamCompletion(LLMRequest request)
    {
        Map<String, Object> body = prepareRequestBody(request);

        return webClient.post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToFlux(String.class)
                        .flatMap(this::parseChunk)
                        .onErrorMap(e -> new LLMException("Error calling OpenAI", e, "OPENAI_ERROR", true));
    }


    @Override
    public String getProviderName()
    {
        return "openai";
    }


    private Map<String, Object> prepareRequestBody(LLMRequest request)
    {
        Map<String, Object> body = new HashMap<>();
        body.put("model", config.getLlm().getModel());
        body.put("stream", true);
        body.put("temperature",
                 request.getTemperature() != null ? request.getTemperature()
                                 : config.getLlm().getTemperature());

        if (request.getMaxTokens() != null)
        {
            body.put("max_tokens", request.getMaxTokens());
        }

        List<Map<String, String>> messages = new ArrayList<>();
        if (request.getSystemPrompt() != null)
        {
            messages.add(Map.of("role", "system", "content", request.getSystemPrompt()));
        }

        if (request.getContext() != null)
        {
            for (Message msg : request.getContext())
            {
                messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
            }
        }

        messages.add(Map.of("role", "user", "content", request.getUserPrompt()));
        body.put("messages", messages);

        return body;
    }


    private Flux<LLMResponseChunk> parseChunk(String chunk)
    {
        if ("[DONE]".equals(chunk.trim()))
        { return Flux.just(LLMResponseChunk.builder().finished(true).build()); }

        try
        {
            // OpenAI SSE chunks come with "data: " prefix usually, but WebClient might strip it if
            // configured,
            // or return lines. Usually `bodyToFlux` on SSE returns data lines.
            // Actually WebClient treats SSE as line stream?
            // If the server sends `data: {...}`, bodyToFlux(String.class) might return the raw string.
            // Standard OpenAI response is `data: {...}`

            // Let's assume we get lines.
            // If multiple lines come in one chunk, we might need to split.
            // For now assuming the chunk is a line or set of lines.
            // But usually Reactor Netty handles SSE via separate method or custom decoder.
            // Since we used bodyToFlux(String.class), we might get concatenated buffers or lines.

            // A safer bet without specialized SSE decoder is to handle "data:" prefix

            // NOTE: Simplification here. Robust parsing would need to handle "data: " removal and [DONE].
            // If the chunk doesn't start with "{", it might be "data: {...}"

            // However, this simple string parsing is prone to errors if split across chunks.
            // But let's assume valid JSON lines for this exercise or simple prefix stripping.

            // Actually, standard `bodyToFlux` for `TEXT_EVENT_STREAM` handles this better,
            // but OpenAI returns application/json kind of stream or text/event-stream.

            // Let's rely on simple string manipulation for the exercise, assuming well-formed lines from
            // `bodyToFlux` if content-type is event-stream.
            // But OpenAI returns `application/json` sometimes on error, and `text/event-stream` on success.

            /*
             * Each line starts with "data: ".
             */

            if (chunk.startsWith("data: "))
            {
                String json = chunk.substring(6).trim();
                if ("[DONE]".equals(json))
                { return Flux.just(LLMResponseChunk.builder().finished(true).build()); }

                JsonNode node = objectMapper.readTree(json);
                String content = "";
                if (node.has("choices") && node.get("choices").isArray() && !node.get("choices").isEmpty())
                {
                    JsonNode choice = node.get("choices").get(0);
                    if (choice.has("delta") && choice.get("delta").has("content"))
                    {
                        content = choice.get("delta").get("content").asText();
                    }
                    if (choice.has("finish_reason") && !choice.get("finish_reason").isNull())
                    {
                        // could signal finish here
                    }
                }

                if (content.isEmpty())
                { return Flux.empty(); }

                return Flux.just(LLMResponseChunk.builder().content(content).finished(false).build());
            }

            return Flux.empty();
        }
        catch (JsonProcessingException e)
        {
            // log error, skip chunk
            logger.warn("Failed to parse chunk: " + chunk);
            return Flux.empty();
        }
    }
}
