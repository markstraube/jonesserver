package com.straube.jones.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIResponseChunk {
    private String type; // chunk, complete, error
    private String content;
    private String contentType; // markdown, link
    private Map<String, Object> metadata;

    public static AIResponseChunk chunk(String content) {
        return AIResponseChunk.builder()
                .type(AIEventType.CHUNK.getValue())
                .content(content)
                .contentType(AIContentType.MARKDOWN.getValue())
                .build();
    }
    
    public static AIResponseChunk error(String message, String errorCode, boolean retryable) {
        return AIResponseChunk.builder()
                .type(AIEventType.ERROR.getValue())
                .content(message)
                .metadata(Map.of("errorCode", errorCode, "retryable", retryable))
                .build();
    }

    public static AIResponseChunk complete(Map<String, Object> metadata) {
        return AIResponseChunk.builder()
                .type(AIEventType.COMPLETE.getValue())
                .metadata(metadata)
                .build();
    }
}
