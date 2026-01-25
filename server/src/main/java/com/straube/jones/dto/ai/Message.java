package com.straube.jones.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private String timestamp;
    private String role; // user | assistant | system
    private String content;
    private String contentType;

    public static Message user(String content) {
        return Message.builder()
                .timestamp(Instant.now().toString())
                .role("user")
                .content(content)
                .contentType("text")
                .build();
    }

    public static Message assistant(String content) {
        return Message.builder()
                .timestamp(Instant.now().toString())
                .role("assistant")
                .content(content)
                .contentType("markdown")
                .build();
    }
    
    public static Message system(String content) {
        return Message.builder()
                .timestamp(Instant.now().toString())
                .role("system")
                .content(content)
                .contentType("text")
                .build();
    }
}
