package com.straube.jones.trader.dto;


public class AnalysisRecommendation
{
    private RecommendationAction action;
    private String timeHorizon;
    private String rationale;
    private String nextCheck;

    public RecommendationAction getAction()
    {
        return action;
    }


    public void setAction(RecommendationAction action)
    {
        this.action = action;
    }


    public String getTimeHorizon()
    {
        return timeHorizon;
    }


    public void setTimeHorizon(String timeHorizon)
    {
        this.timeHorizon = timeHorizon;
    }


    public String getRationale()
    {
        return rationale;
    }


    public void setRationale(String rationale)
    {
        this.rationale = rationale;
    }


    public String getNextCheck()
    {
        return nextCheck;
    }


    public void setNextCheck(String nextCheck)
    {
        this.nextCheck = nextCheck;
    }
}
