package com.straube.jones.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.straube.jones.dto.ai.AIResponseChunk;
import com.straube.jones.dto.ai.AnalyzeRequest;
import com.straube.jones.dto.ai.ChatSessionSummary;
import com.straube.jones.dto.ai.ExplainRequest;
import com.straube.jones.service.AIAssistantService;

import jakarta.validation.Valid;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/assistant")
public class AIAssistantController {

    private final AIAssistantService aiAssistantService;

    public AIAssistantController(AIAssistantService aiAssistantService) {
        this.aiAssistantService = aiAssistantService;
    }

    @PostMapping(value = "/explain", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AIResponseChunk> explain(@Valid @RequestBody ExplainRequest request, Principal principal) {
        return aiAssistantService.explain(request, principal.getName());
    }

    @PostMapping(value = "/analyze", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AIResponseChunk> analyze(@Valid @RequestBody AnalyzeRequest request, Principal principal) {
        return aiAssistantService.analyze(request, principal.getName());
    }

    @GetMapping("/session")
    public Mono<AIContext> getSession(@RequestParam String chat, @RequestParam String sessionId, Principal principal) {
        return aiAssistantService.getSession(chat, sessionId, principal.getName());
    }

    @GetMapping("/history")
    public List<ChatSessionSummary> getHistory(@RequestParam String chat, Principal principal) {
        return aiAssistantService.getHistory(chat, principal.getName());
    }
}
