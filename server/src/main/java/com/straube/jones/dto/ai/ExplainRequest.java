package com.straube.jones.dto.ai;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExplainRequest
{
    @NotBlank(message = "Question must not be empty")
    private String question;

    private String sessionId;
}
