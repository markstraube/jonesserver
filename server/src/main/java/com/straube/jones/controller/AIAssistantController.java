package com.straube.jones.controller;

import com.straube.jones.dto.ai.AIResponseChunk;
import com.straube.jones.dto.ai.AnalyzeRequest;
import com.straube.jones.dto.ai.ExplainRequest;
import com.straube.jones.service.AIAssistantService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.security.Principal;

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
}
