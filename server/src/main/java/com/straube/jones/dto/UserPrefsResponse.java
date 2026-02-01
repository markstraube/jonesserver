package com.straube.jones.dto;


import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * JSON-freundliche Response-Klasse für User Preferences
 */
@Schema(description = "Response object carrying user preferences and operation status")
public class UserPrefsResponse
{
    @Schema(description = "The key or category identifier for the preferences (e.g., 'filter', 'watchlist').", example = "filter")
    @JsonProperty("topic")
    private String topic;

    @Schema(description = "The preferences content stored as a JSON string.", example = "{\"minPrice\": 10, \"maxPrice\": 500}")
    @JsonProperty("preferences")
    private String preferences;

    @Schema(description = "Flag indicating if the operation was successful.")
    @JsonProperty("success")
    private Boolean success;

    @Schema(description = "Human-readable message describing the outcome of the operation.")
    @JsonProperty("message")
    private String message;

    @Schema(description = "Timestamp when the response was generated (epoch milliseconds).")
    @JsonProperty("timestamp")
    private Long timestamp;

    public UserPrefsResponse()
    {
        this.timestamp = System.currentTimeMillis();
    }


    public UserPrefsResponse(String topic, String preferences, Boolean success, String message)
    {
        this.topic = topic;
        this.preferences = preferences;
        this.success = success;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }


    public static UserPrefsResponse success(String topic, String preferences)
    {
        return new UserPrefsResponse(topic, preferences, true, "Preferences processed successfully");
    }


    public static UserPrefsResponse error(String topic, String message)
    {
        return new UserPrefsResponse(topic, "[]", false, message);
    }


    // Getters und Setters
    public String getTopic()
    {
        return topic;
    }


    public void setTopic(String topic)
    {
        this.topic = topic;
    }


    public String getPreferences()
    {
        return preferences;
    }


    public void setPreferences(String preferences)
    {
        this.preferences = preferences;
    }


    public Boolean getSuccess()
    {
        return success;
    }


    public void setSuccess(Boolean success)
    {
        this.success = success;
    }


    public String getMessage()
    {
        return message;
    }


    public void setMessage(String message)
    {
        this.message = message;
    }


    public Long getTimestamp()
    {
        return timestamp;
    }


    public void setTimestamp(Long timestamp)
    {
        this.timestamp = timestamp;
    }
}
