package com.straube.jones.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.straube.jones.config.AIAssistantConfig;
import com.straube.jones.dto.ai.AIContext;
import com.straube.jones.dto.ai.Message;
import com.straube.jones.model.User;
import com.straube.jones.model.UserPreference;
import com.straube.jones.repository.UserPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class ContextService {
    private static final Logger logger = LoggerFactory.getLogger(ContextService.class);
    private final UserPreferenceRepository userPreferenceRepository;
    private final ObjectMapper objectMapper;
    private final AIAssistantConfig config;

    public ContextService(UserPreferenceRepository userPreferenceRepository, ObjectMapper objectMapper, AIAssistantConfig config) {
        this.userPreferenceRepository = userPreferenceRepository;
        this.objectMapper = objectMapper;
        this.config = config;
    }

    public Mono<AIContext> loadContext(String sessionId, User user, String type) {
        return Mono.fromCallable(() -> {
            Optional<UserPreference> prefsOpt = userPreferenceRepository.findByUser(user);
            if (prefsOpt.isEmpty()) {
                return AIContext.builder().sessionId(sessionId).createdAt(Instant.now().toString()).build();
            }

            String prefsJson = prefsOpt.get().getPreferences();
            if (prefsJson == null || prefsJson.isEmpty()) {
                return AIContext.builder().sessionId(sessionId).createdAt(Instant.now().toString()).build();
            }

            try {
                Map<String, Object> prefsMap = objectMapper.readValue(prefsJson, new TypeReference<Map<String, Object>>() {});
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
                UserPreference prefs = userPreferenceRepository.findByUser(user)
                        .orElseGet(() -> {
                            UserPreference newPrefs = new UserPreference();
                            newPrefs.setUser(user);
                            return newPrefs;
                        });

                Map<String, Object> prefsMap = new HashMap<>();
                if (prefs.getPreferences() != null && !prefs.getPreferences().isEmpty()) {
                    prefsMap = objectMapper.readValue(prefs.getPreferences(), new TypeReference<Map<String, Object>>() {});
                }

                String key = type.equals("explain") ? "ai_explain_" + context.getSessionId() : "ai_analyze_" + context.getSessionId();
                context.setLastAccessedAt(Instant.now().toString());
                prefsMap.put(key, context);
                
                // Cleanup old contexts if needed (omitted for brevity)

                prefs.setPreferences(objectMapper.writeValueAsString(prefsMap));
                userPreferenceRepository.save(prefs);
                
            } catch (Exception e) {
                logger.error("Error saving user preferences", e);
                // Don't fail the flow if save fails
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
