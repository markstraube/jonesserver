package com.straube.jones.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionSummary {
    private String timestamp;
    private String title;
    @JsonProperty("session-id")
    private String sessionId;
    @JsonProperty("file-name")
    private String fileName;
}
