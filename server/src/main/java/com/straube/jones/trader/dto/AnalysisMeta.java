package com.straube.jones.trader.dto;


public class AnalysisMeta
{
    private String symbol;
    private String model;
    private String modelVersion;
    private String analysisTimestamp;
    private String inputReportVersion;

    public String getSymbol()
    {
        return symbol;
    }


    public void setSymbol(String symbol)
    {
        this.symbol = symbol;
    }


    public String getModel()
    {
        return model;
    }


    public void setModel(String model)
    {
        this.model = model;
    }


    public String getModelVersion()
    {
        return modelVersion;
    }


    public void setModelVersion(String modelVersion)
    {
        this.modelVersion = modelVersion;
    }


    public String getAnalysisTimestamp()
    {
        return analysisTimestamp;
    }


    public void setAnalysisTimestamp(String analysisTimestamp)
    {
        this.analysisTimestamp = analysisTimestamp;
    }


    public String getInputReportVersion()
    {
        return inputReportVersion;
    }


    public void setInputReportVersion(String inputReportVersion)
    {
        this.inputReportVersion = inputReportVersion;
    }
}
