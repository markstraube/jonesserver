package com.straube.jones.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIContext {
    private String sessionId;
    @Builder.Default
    private List<Message> messages = new ArrayList<>();
    private String createdAt;
    private String lastAccessedAt;
    private String responseId;

    public void addMessage(Message message) {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        messages.add(message);
    }
}
