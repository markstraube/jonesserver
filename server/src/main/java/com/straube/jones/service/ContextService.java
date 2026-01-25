package com.straube.jones.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.straube.jones.config.AIAssistantConfig;
import com.straube.jones.dataprovider.userprefs.UserPrefsRepo;
import com.straube.jones.dto.ai.AIContext;
import com.straube.jones.model.User;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class ContextService {
    private static final Logger logger = LoggerFactory.getLogger(ContextService.class);
    private final ObjectMapper objectMapper;
    private final AIAssistantConfig config;

    public ContextService(ObjectMapper objectMapper,
            AIAssistantConfig config) {
        this.objectMapper = objectMapper;
        this.config = config;
    }

    public Mono<AIContext> loadContext(String sessionId, User user, String type) {
        return Mono.fromCallable(() -> {
            String prefsJson = UserPrefsRepo.getPrefs(user, "chat#" + type + "#" + sessionId);
            if (prefsJson == null || prefsJson.isEmpty()) {
                return AIContext.builder().sessionId(sessionId).createdAt(Instant.now().toString()).build();
            }

            try {
                Map<String, Object> prefsMap = objectMapper.readValue(prefsJson,
                        new TypeReference<Map<String, Object>>() {
                        });
                String key = type.equals("explain") ? "ai_explain_" + sessionId : "ai_analyze_" + sessionId;

                if (prefsMap.containsKey(key)) {
                    // Assuming stored as complex object or string
                    Object ctxObj = prefsMap.get(key);
                    return objectMapper.convertValue(ctxObj, AIContext.class);
                }
            } catch (Exception e) {
                logger.error("Error parsing user preferences", e);
            }

            return AIContext.builder().sessionId(sessionId).createdAt(Instant.now().toString()).build();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> saveContext(AIContext context, User user, String type) {
        return Mono.fromRunnable(() -> {
            try {
                String prefsJson = UserPrefsRepo.getPrefs(user, "chat#" + type + "#" + context.getSessionId());

                Map<String, Object> prefsMap = new HashMap<>();
                if (prefsJson != null && !prefsJson.isEmpty()) {
                    prefsMap = objectMapper.readValue(prefsJson, new TypeReference<Map<String, Object>>() {
                    });
                }

                String key = type.equals("explain") ? "ai_explain_" + context.getSessionId()
                        : "ai_analyze_" + context.getSessionId();
                context.setLastAccessedAt(Instant.now().toString());
                prefsMap.put(key, context);

                // Cleanup old contexts if needed (omitted for brevity)

                UserPrefsRepo.savePrefs(user, "chat#" + type + "#" + context.getSessionId(), objectMapper.writeValueAsString(prefsMap));

            } catch (Exception e) {
                logger.error("Error saving user preferences", e);
                // Don't fail the flow if save fails
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
