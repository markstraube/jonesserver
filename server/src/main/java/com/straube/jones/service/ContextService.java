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
import com.straube.jones.dto.ai.ChatSessionSummary;
import com.straube.jones.dto.ai.Message;
import com.straube.jones.model.User;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import reactor.core.publisher.Flux;
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
        return Mono.fromCallable(() -> loadContextSync(sessionId, user, type))
                   .subscribeOn(Schedulers.boundedElastic());
    }

    public AIContext loadContextSync(String sessionId, User user, String type) {
        try {
            String prefsJson = UserPrefsRepo.getPrefs(user, "chat#" + type + "#" + sessionId);
            if (prefsJson == null || prefsJson.isEmpty()) {
                return AIContext.builder().sessionId(sessionId).createdAt(Instant.now().toString()).build();
            }

            Map<String, Object> prefsMap = objectMapper.readValue(prefsJson,
                    new TypeReference<Map<String, Object>>() {
                    });
            String key = type.equals("explain") ? "ai_explain_" + sessionId : "ai_analyze_" + sessionId;

            if (prefsMap.containsKey(key)) {
                Object ctxObj = prefsMap.get(key);
                return objectMapper.convertValue(ctxObj, AIContext.class);
            }
        } catch (Exception e) {
            logger.error("Error parsing user preferences for session " + sessionId, e);
        }

        return AIContext.builder().sessionId(sessionId).createdAt(Instant.now().toString()).build();
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

                UserPrefsRepo.savePrefs(user, "chat#" + type + "#" + context.getSessionId(),
                        objectMapper.writeValueAsString(prefsMap));

            } catch (Exception e) {
                logger.error("Error saving user preferences", e);
                // Don't fail the flow if save fails
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public java.util.List<ChatSessionSummary> getHistory(String type, User user) {
        java.util.List<String> sessionIds = UserPrefsRepo.listPrefs(user, "chat#" + type);
        java.util.List<ChatSessionSummary> summaries = new java.util.ArrayList<>();

        for (String sessionId : sessionIds) {
            try {
                AIContext context = loadContextSync(sessionId, user, type);
                
                String title = "New Chat";
                if (context.getMessages() != null) {
                    for (Message m : context.getMessages()) {
                        if ("user".equals(m.getRole())) {
                            title = m.getContent();
                            if (title != null && title.length() > 100) {
                                title = title.substring(0, 100) + "...";
                            }
                            break;
                        }
                    }
                } else {
                    title = "Chat " + sessionId;
                }

                String timestamp = null;
                if (context.getMessages() != null && !context.getMessages().isEmpty()) {
                    timestamp = context.getMessages().get(context.getMessages().size() - 1).getTimestamp();
                }
                if (timestamp == null) {
                    timestamp = context.getLastAccessedAt();
                }
                if (timestamp == null) {
                    timestamp = context.getCreatedAt();
                }
                if (timestamp == null) {
                    timestamp = Instant.now().toString();
                }

                String fileName = "chat/" + type + "/" + sessionId + ".json";
                
                summaries.add(ChatSessionSummary.builder()
                        .sessionId(sessionId)
                        .title(title)
                        .timestamp(timestamp)
                        .fileName(fileName)
                        .build());

            } catch (Exception e) {
                logger.error("Error loading history for session " + sessionId, e);
            }
        }

        summaries.sort((s1, s2) -> {
            String t1 = s1.getTimestamp() != null ? s1.getTimestamp() : "";
            String t2 = s2.getTimestamp() != null ? s2.getTimestamp() : "";
            return t2.compareTo(t1);
        });
        
        return summaries;
    }
}
