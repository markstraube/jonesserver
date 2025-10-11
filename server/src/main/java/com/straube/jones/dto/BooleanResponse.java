package com.straube.jones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON-freundliche Response-Klasse für Boolean-Operationen
 */
public class BooleanResponse 
{
    @JsonProperty("success")
    private Boolean success;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("timestamp")
    private Long timestamp;
    
    public BooleanResponse() 
    {
        this.timestamp = System.currentTimeMillis();
    }
    
    public BooleanResponse(Boolean success, String message) 
    {
        this.success = success;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
    
    public static BooleanResponse success(String message) 
    {
        return new BooleanResponse(true, message);
    }
    
    public static BooleanResponse success() 
    {
        return new BooleanResponse(true, "Operation completed successfully");
    }
    
    public static BooleanResponse failure(String message) 
    {
        return new BooleanResponse(false, message);
    }
    
    // Getters und Setters
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}