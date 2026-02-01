package com.straube.jones.model;


import com.fasterxml.jackson.annotation.JsonProperty;

public class CompanyBasics
{
    @JsonProperty("company_name")
    private String companyName;

    @JsonProperty("headquarters")
    private String headquarters;

    @JsonProperty("business_model")
    private String businessModel;

    public CompanyBasics()
    {}


    public CompanyBasics(String companyName, String headquarters, String businessModel)
    {
        this.companyName = companyName;
        this.headquarters = headquarters;
        this.businessModel = businessModel;
    }


    public String getCompanyName()
    {
        return companyName;
    }


    public void setCompanyName(String companyName)
    {
        this.companyName = companyName;
    }


    public String getHeadquarters()
    {
        return headquarters;
    }


    public void setHeadquarters(String headquarters)
    {
        this.headquarters = headquarters;
    }


    public String getBusinessModel()
    {
        return businessModel;
    }


    public void setBusinessModel(String businessModel)
    {
        this.businessModel = businessModel;
    }
}
