package com.straube.jones.trader.dasboard;


public class SupportSection
{

    /** Unterstützungsniveau */
    private double supportLevel;

    /** Abstand zum Support in Prozent */
    private double distanceToSupportPercent;

    /** Art der Unterstützung */
    private String supportType;

    /** Erklärung */
    private String explanation;

    public double getSupportLevel()
    {
        return supportLevel;
    }


    public void setSupportLevel(double supportLevel)
    {
        this.supportLevel = supportLevel;
    }


    public double getDistanceToSupportPercent()
    {
        return distanceToSupportPercent;
    }


    public void setDistanceToSupportPercent(double distanceToSupportPercent)
    {
        this.distanceToSupportPercent = distanceToSupportPercent;
    }


    public String getSupportType()
    {
        return supportType;
    }


    public void setSupportType(String supportType)
    {
        this.supportType = supportType;
    }


    public String getExplanation()
    {
        return explanation;
    }


    public void setExplanation(String explanation)
    {
        this.explanation = explanation;
    }
}
