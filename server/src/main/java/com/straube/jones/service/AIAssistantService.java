package com.straube.jones.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.straube.jones.agent.StocksAgent;
import com.straube.jones.dto.ai.AIContext;
import com.straube.jones.dto.ai.AIResponseChunk;
import com.straube.jones.dto.ai.AnalyzeRequest;
import com.straube.jones.dto.ai.ChatSessionSummary;
import com.straube.jones.dto.ai.ExplainRequest;
import com.straube.jones.dto.ai.LLMRequest;
import com.straube.jones.dto.ai.Message;
import com.straube.jones.model.User;
import com.straube.jones.repository.UserRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class AIAssistantService {

    private final ContextService contextService;
    private final LLMService llmService;
    private final UserRepository userRepository;

    public AIAssistantService(ContextService contextService, LLMService llmService, UserRepository userRepository) {
        this.contextService = contextService;
        this.llmService = llmService;
        this.userRepository = userRepository;
    }

    public Flux<AIResponseChunk> explain(ExplainRequest request, String username) {
        String sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString();

        final User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return contextService.loadContext(sessionId, user, "explain")
                .flatMapMany(context -> {
                    // Update context with user message
                    Message userMessage = Message.user(request.getQuestion());
                    context.addMessage(userMessage);

                    return StocksAgent.explain(contextService, context, user, request.getQuestion());
                });
    }

    public Flux<AIResponseChunk> analyze(AnalyzeRequest request, String username) {
        String sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString();

        return Mono.fromCallable(() -> userRepository.findByUsername(username))
                .flatMap(userOpt -> {
                    if (userOpt.isEmpty())
                        return Mono.error(new RuntimeException("User not found"));
                    return contextService.loadContext(sessionId, userOpt.get(), "analyze")
                            .zipWith(Mono.just(userOpt.get()));
                })
                .flatMapMany(tuple -> {
                    AIContext context = tuple.getT1();
                    User user = tuple.getT2();

                    Message userMessage = Message.user(request.getQuestion());
                    context.addMessage(userMessage);

                    // Fetch data (Placeholder)
                    String dataContext = fetchDataForRequest(request);

                    LLMRequest llmRequest = LLMRequest.builder()
                            .systemPrompt("Du bist ein Experte für Finanzmarkt-Analysen.\n" +
                                    "Analysiere die Daten und gib eine fundierte Einschätzung ab.\n" +
                                    "Daten:\n" + dataContext)
                            .userPrompt(request.getQuestion())
                            .context(context.getMessages().subList(0, context.getMessages().size() - 1))
                            .build();

                    StringBuffer fullResponse = new StringBuffer();

                    return llmService.streamResponse(llmRequest)
                            .doOnNext(chunk -> {
                                if ("chunk".equals(chunk.getType())) {
                                    fullResponse.append(chunk.getContent());
                                }
                            })
                            .doOnComplete(() -> {
                                Message assistantMessage = Message.assistant(fullResponse.toString());
                                context.addMessage(assistantMessage);
                                contextService.saveContext(context, user, "analyze").subscribe();
                            });
                });
    }

    public Mono<AIContext> getSession(String type, String sessionId, String username) {
        return Mono.fromCallable(() -> userRepository.findByUsername(username))
                .flatMap(userOpt -> {
                    if (userOpt.isEmpty())
                        return Mono.error(new RuntimeException("User not found"));
                    return contextService.loadContext(sessionId, userOpt.get(), type);
                });
    }

    public java.util.List<ChatSessionSummary> getHistory(String type, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return contextService.getHistory(type, user);
    }

    private String fetchDataForRequest(AnalyzeRequest request) {
        // Mock data fetching logic
        // In real impl, would call MarketDataService, IndicatorService, etc.
        // based on extracted symbols from request.symbol or request.question
        StringBuilder sb = new StringBuilder();
        if (request.getSymbol() != null) {
            sb.append("Details for ").append(request.getSymbol()).append(":\n");
            sb.append("- Current Price: 150.00 EUR\n");
            sb.append("- RSI (14): 65.5\n");
            sb.append("- MACD: Positive crossover\n");
        }
        return sb.toString();
    }
}
