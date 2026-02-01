package com.straube.jones.dto.ai;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AnalyzeRequest
{
    @NotBlank(message = "Question must not be empty")
    private String question;

    private String symbol;

    private String timeRange;

    private String sessionId;
}
