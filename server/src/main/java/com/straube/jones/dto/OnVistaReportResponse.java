package com.straube.jones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON-freundliche Response-Klasse für OnVista-Berichte
 */
public class OnVistaReportResponse 
{
    @JsonProperty("short_url")
    private String shortUrl;
    
    @JsonProperty("content")
    private String content;
    
    @JsonProperty("found")
    private Boolean found;
    
    @JsonProperty("timestamp")
    private Long timestamp;
    
    public OnVistaReportResponse() 
    {
        this.timestamp = System.currentTimeMillis();
    }
    
    public OnVistaReportResponse(String shortUrl, String content, Boolean found) 
    {
        this.shortUrl = shortUrl;
        this.content = content;
        this.found = found;
        this.timestamp = System.currentTimeMillis();
    }
    
    public static OnVistaReportResponse found(String shortUrl, String content) 
    {
        return new OnVistaReportResponse(shortUrl, content, true);
    }
    
    public static OnVistaReportResponse notFound(String shortUrl) 
    {
        return new OnVistaReportResponse(shortUrl, "No Data", false);
    }
    
    // Getters und Setters
    public String getShortUrl() { return shortUrl; }
    public void setShortUrl(String shortUrl) { this.shortUrl = shortUrl; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public Boolean getFound() { return found; }
    public void setFound(Boolean found) { this.found = found; }
    
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}