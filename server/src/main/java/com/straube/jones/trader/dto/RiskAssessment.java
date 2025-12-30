package com.straube.jones.trader.dto;


import java.util.List;

public class RiskAssessment
{
    private RiskLevel overallRisk;
    private List<String> keyRisks;
    private List<String> invalidatesSetupIf;

    public RiskLevel getOverallRisk()
    {
        return overallRisk;
    }


    public void setOverallRisk(RiskLevel overallRisk)
    {
        this.overallRisk = overallRisk;
    }


    public List<String> getKeyRisks()
    {
        return keyRisks;
    }


    public void setKeyRisks(List<String> keyRisks)
    {
        this.keyRisks = keyRisks;
    }


    public List<String> getInvalidatesSetupIf()
    {
        return invalidatesSetupIf;
    }


    public void setInvalidatesSetupIf(List<String> invalidatesSetupIf)
    {
        this.invalidatesSetupIf = invalidatesSetupIf;
    }
}
