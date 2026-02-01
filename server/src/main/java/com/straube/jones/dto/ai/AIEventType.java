package com.straube.jones.dto.ai;


public enum AIEventType
{
    CHUNK("chunk"), COMPLETE("complete"), ERROR("error");

    private final String value;

    AIEventType(String value)
    {
        this.value = value;
    }


    public String getValue()
    {
        return value;
    }
}
