package com.straube.jones.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON-freundliche Response-Klasse für Service-Index
 */
public class ServiceInfoResponse 
{
    @JsonProperty("service_name")
    private String serviceName;
    
    @JsonProperty("version")
    private String version;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("timestamp")
    private Long timestamp;
    
    public ServiceInfoResponse() 
    {
        this.serviceName = "StocksServer";
        this.version = "1.0.0";
        this.description = "API für Aktieninformationen und Datenanalyse";
        this.status = "running";
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getters und Setters
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}