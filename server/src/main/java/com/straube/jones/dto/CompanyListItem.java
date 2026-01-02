package com.straube.jones.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Data Transfer Object representing a simplified company entry in a list.
 * Contains only the essential information needed for listing companies.
 */
@Schema(description = "Simplified company information for list display containing only the company name and symbol.")
public class CompanyListItem {
    
    @Schema(description = "The stock symbol (ticker) used by the exchange. This is the unique identifier used for trading and price lookups.", example = "AAPL")
    private String cSymbol;
    
    @Schema(description = "Full legal name of the company. This is the official registered business name.", example = "Apple Inc.")
    private String cLongName;

    /**
     * Default constructor.
     */
    public CompanyListItem() {
    }

    /**
     * Constructor with all fields.
     * 
     * @param cSymbol The stock symbol
     * @param cLongName The full company name
     */
    public CompanyListItem(String cSymbol, String cLongName) {
        this.cSymbol = cSymbol;
        this.cLongName = cLongName;
    }

    /**
     * Gets the stock symbol.
     * 
     * @return The stock symbol (ticker)
     */
    public String getcSymbol() {
        return cSymbol;
    }

    /**
     * Sets the stock symbol.
     * 
     * @param cSymbol The stock symbol to set
     */
    public void setcSymbol(String cSymbol) {
        this.cSymbol = cSymbol;
    }

    /**
     * Gets the full company name.
     * 
     * @return The full legal name of the company
     */
    public String getcLongName() {
        return cLongName;
    }

    /**
     * Sets the full company name.
     * 
     * @param cLongName The full company name to set
     */
    public void setcLongName(String cLongName) {
        this.cLongName = cLongName;
    }
}
