package com.straube.jones.controller;


import java.security.Principal;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.straube.jones.dto.ai.AIContext;
import com.straube.jones.dto.ai.AIResponseChunk;
import com.straube.jones.dto.ai.AnalyzeRequest;
import com.straube.jones.dto.ai.ChatSessionSummary;
import com.straube.jones.dto.ai.ExplainRequest;
import com.straube.jones.service.AIAssistantService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/assistant")
@PreAuthorize("isAuthenticated()")
@Tag(name = "AI Assistant", description = "Endpoints for AI-powered financial analysis and explanations")
public class AIAssistantController
{

    private final AIAssistantService aiAssistantService;

    public AIAssistantController(AIAssistantService aiAssistantService)
    {
        this.aiAssistantService = aiAssistantService;
    }


    @Operation(summary = "Explain a financial topic", description = "Streams an explanation for a given question using an AI agent.")
    @PostMapping(value = "/explain", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AIResponseChunk> explain(@Valid
    @RequestBody
    ExplainRequest request, Principal principal)
    {
        return aiAssistantService.explain(request, principal.getName());
    }


    @Operation(summary = "Analyze financial data", description = "Streams an analysis based on provided data and question.")
    @PostMapping(value = "/analyze", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AIResponseChunk> analyze(@Valid
    @RequestBody
    AnalyzeRequest request, Principal principal)
    {
        return aiAssistantService.analyze(request, principal.getName());
    }


    @Operation(summary = "Get Chat Session", description = "Retrieves the full chat session history and context for a specific session ID.")
    @ApiResponse(responseCode = "200", description = "The chat session context", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AIContext.class)))
    @GetMapping("/session")
    public Mono<AIContext> getSession(@Parameter(description = "The type of chat session (e.g., 'explain', 'analyze')", required = true, example = "explain")
    @RequestParam
    String chat,
                                      @Parameter(description = "The unique session ID", required = true)
                                      @RequestParam
                                      String sessionId,
                                      Principal principal)
    {
        return aiAssistantService.getSession(chat, sessionId, principal.getName());
    }


    @Operation(summary = "Get Chat History", description = "Retrieves a summary list of past chat sessions for a specific type.")
    @GetMapping("/history")
    public List<ChatSessionSummary> getHistory(@Parameter(description = "The type of chat session (e.g., 'explain')", required = true)
    @RequestParam
    String chat, Principal principal)
    {
        return aiAssistantService.getHistory(chat, principal.getName());
    }
}
