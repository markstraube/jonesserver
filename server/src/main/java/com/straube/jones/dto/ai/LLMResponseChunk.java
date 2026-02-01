package com.straube.jones.dto.ai;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMResponseChunk
{
    private String content;
    private boolean finished;
    private int tokensUsed;
}
