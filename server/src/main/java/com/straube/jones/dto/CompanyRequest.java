package com.straube.jones.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request object for creating or updating company master data.")
public class CompanyRequest {
    
    @Schema(description = "The International Securities Identification Number (ISIN) of the company.", example = "US0378331005", requiredMode = Schema.RequiredMode.REQUIRED)
    private String isin;

    public String getIsin() {
        return isin;
    }

    public void setIsin(String isin) {
        this.isin = isin;
    }
}
