package com.straube.jones.dto.ai;


public enum AIContentType
{
    MARKDOWN("markdown"), LINK("link"), TEXT("text");

    private final String value;

    AIContentType(String value)
    {
        this.value = value;
    }


    public String getValue()
    {
        return value;
    }
}
