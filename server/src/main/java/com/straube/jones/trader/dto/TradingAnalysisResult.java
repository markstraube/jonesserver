package com.straube.jones.trader.dto;


import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TradingAnalysisResult
{
    private AnalysisMeta meta;
    private AnalysisSummary summary;
    private AnalysisScore score;
    private AnalysisSignals signals;
    private List<AnalysisInsight> insights;
    private AnalysisRecommendation recommendation;
    private RiskAssessment riskAssessment;

    public AnalysisMeta getMeta()
    {
        return meta;
    }


    public void setMeta(AnalysisMeta meta)
    {
        this.meta = meta;
    }


    public AnalysisSummary getSummary()
    {
        return summary;
    }


    public void setSummary(AnalysisSummary summary)
    {
        this.summary = summary;
    }


    public AnalysisScore getScore()
    {
        return score;
    }


    public void setScore(AnalysisScore score)
    {
        this.score = score;
    }


    public AnalysisSignals getSignals()
    {
        return signals;
    }


    public void setSignals(AnalysisSignals signals)
    {
        this.signals = signals;
    }


    public List<AnalysisInsight> getInsights()
    {
        return insights;
    }


    public void setInsights(List<AnalysisInsight> insights)
    {
        this.insights = insights;
    }


    public AnalysisRecommendation getRecommendation()
    {
        return recommendation;
    }


    public void setRecommendation(AnalysisRecommendation recommendation)
    {
        this.recommendation = recommendation;
    }


    public RiskAssessment getRiskAssessment()
    {
        return riskAssessment;
    }


    public void setRiskAssessment(RiskAssessment riskAssessment)
    {
        this.riskAssessment = riskAssessment;
    }


    @Override
    public String toString()
    {
        try
        {
            return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(this);
        }
        catch (JsonProcessingException e)
        {
            return super.toString();
        }
    }
}
