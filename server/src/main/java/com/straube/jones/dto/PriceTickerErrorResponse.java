package com.straube.jones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Error response DTO for the Price Ticker API.
 */
public class PriceTickerErrorResponse
{
    @JsonProperty("error")
    private ErrorDetail error;
    
    public static class ErrorDetail
    {
        @JsonProperty("code")
        private String code;
        
        @JsonProperty("message")
        private String message;
        
        @JsonProperty("details")
        private String details;
        
        @JsonProperty("timestamp")
        private String timestamp;
        
        public ErrorDetail()
        {
        }
        
        public ErrorDetail(String code, String message, String details, String timestamp)
        {
            this.code = code;
            this.message = message;
            this.details = details;
            this.timestamp = timestamp;
        }
        
        // Getters and Setters
        public String getCode()
        {
            return code;
        }
        
        public void setCode(String code)
        {
            this.code = code;
        }
        
        public String getMessage()
        {
            return message;
        }
        
        public void setMessage(String message)
        {
            this.message = message;
        }
        
        public String getDetails()
        {
            return details;
        }
        
        public void setDetails(String details)
        {
            this.details = details;
        }
        
        public String getTimestamp()
        {
            return timestamp;
        }
        
        public void setTimestamp(String timestamp)
        {
            this.timestamp = timestamp;
        }
    }
    
    public PriceTickerErrorResponse()
    {
    }
    
    public PriceTickerErrorResponse(String code, String message, String details, String timestamp)
    {
        this.error = new ErrorDetail(code, message, details, timestamp);
    }
    
    public ErrorDetail getError()
    {
        return error;
    }
    
    public void setError(ErrorDetail error)
    {
        this.error = error;
    }
    
    public static PriceTickerErrorResponse create(String code, String message, String details)
    {
        String timestamp = java.time.Instant.now().toString();
        return new PriceTickerErrorResponse(code, message, details, timestamp);
    }
}
