package com.straube.jones.trader.dto;


public class AnalysisSummary
{
    private String headline;
    private Sentiment overallSentiment;
    private double confidence;

    public String getHeadline()
    {
        return headline;
    }


    public void setHeadline(String headline)
    {
        this.headline = headline;
    }


    public Sentiment getOverallSentiment()
    {
        return overallSentiment;
    }


    public void setOverallSentiment(Sentiment overallSentiment)
    {
        this.overallSentiment = overallSentiment;
    }


    public double getConfidence()
    {
        return confidence;
    }


    public void setConfidence(double confidence)
    {
        this.confidence = confidence;
    }
}
