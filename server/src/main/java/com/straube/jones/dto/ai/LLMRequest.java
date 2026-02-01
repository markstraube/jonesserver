package com.straube.jones.dto.ai;


import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LLMRequest
{
    private String systemPrompt;
    private String userPrompt;
    private List<Message> context;
    private Double temperature;
    private Integer maxTokens;
}
