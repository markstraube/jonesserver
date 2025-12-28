package com.straube.jones.dto;

import java.sql.Timestamp;
import java.sql.Date;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response object containing detailed company master data.")
public class CompanyResponse {
    
    @Schema(description = "Unique identifier for the company record.", example = "550e8400-e29b-41d4-a716-446655440000")
    private String cId;
    
    @Schema(description = "The stock symbol (ticker) used by the exchange.", example = "AAPL")
    private String cSymbol;
    
    @Schema(description = "The International Securities Identification Number (ISIN).", example = "US0378331005")
    private String cIsin;
    
    @Schema(description = "Short name of the company.", example = "Apple Inc.")
    private String cShortName;
    
    @Schema(description = "Full legal name of the company.", example = "Apple Inc.")
    private String cLongName;
    
    @Schema(description = "Currency code for the stock prices.", example = "USD")
    private String cCurrency;
    
    @Schema(description = "Type of financial instrument.", example = "EQUITY")
    private String cInstrumentType;
    
    @Schema(description = "Date of the first trade recorded.", example = "1980-12-12")
    private Date cFirstTradeDate;
    
    @Schema(description = "Name of the exchange where the stock is traded.", example = "NasdaqGS")
    private String cExchangeName;
    
    @Schema(description = "Full name of the exchange.", example = "Nasdaq Global Select")
    private String cFullExchangeName;
    
    @Schema(description = "Name of the timezone for the exchange.", example = "America/New_York")
    private String cExchangeTimezoneName;
    
    @Schema(description = "Timezone abbreviation.", example = "EST")
    private String cTimezone;
    
    @Schema(description = "Indicates if pre-market and post-market data is available.", example = "true")
    private boolean cHasPrePostMarketData;
    
    @Schema(description = "Price hint for formatting.", example = "2")
    private Integer cPriceHint;
    
    @Schema(description = "Granularity of the data available.", example = "1d")
    private String cDataGranularity;
    
    @Schema(description = "Timestamp when the record was created.", example = "2023-01-01T12:00:00Z")
    private Timestamp cCreated;
    
    @Schema(description = "Timestamp when the record was last updated.", example = "2023-01-02T12:00:00Z")
    private Timestamp cUpdated;

    public String getcId() { return cId; }
    public void setcId(String cId) { this.cId = cId; }

    public String getcSymbol() { return cSymbol; }
    public void setcSymbol(String cSymbol) { this.cSymbol = cSymbol; }

    public String getcIsin() { return cIsin; }
    public void setcIsin(String cIsin) { this.cIsin = cIsin; }

    public String getcShortName() { return cShortName; }
    public void setcShortName(String cShortName) { this.cShortName = cShortName; }

    public String getcLongName() { return cLongName; }
    public void setcLongName(String cLongName) { this.cLongName = cLongName; }

    public String getcCurrency() { return cCurrency; }
    public void setcCurrency(String cCurrency) { this.cCurrency = cCurrency; }

    public String getcInstrumentType() { return cInstrumentType; }
    public void setcInstrumentType(String cInstrumentType) { this.cInstrumentType = cInstrumentType; }

    public Date getcFirstTradeDate() { return cFirstTradeDate; }
    public void setcFirstTradeDate(Date cFirstTradeDate) { this.cFirstTradeDate = cFirstTradeDate; }

    public String getcExchangeName() { return cExchangeName; }
    public void setcExchangeName(String cExchangeName) { this.cExchangeName = cExchangeName; }

    public String getcFullExchangeName() { return cFullExchangeName; }
    public void setcFullExchangeName(String cFullExchangeName) { this.cFullExchangeName = cFullExchangeName; }

    public String getcExchangeTimezoneName() { return cExchangeTimezoneName; }
    public void setcExchangeTimezoneName(String cExchangeTimezoneName) { this.cExchangeTimezoneName = cExchangeTimezoneName; }

    public String getcTimezone() { return cTimezone; }
    public void setcTimezone(String cTimezone) { this.cTimezone = cTimezone; }

    public boolean iscHasPrePostMarketData() { return cHasPrePostMarketData; }
    public void setcHasPrePostMarketData(boolean cHasPrePostMarketData) { this.cHasPrePostMarketData = cHasPrePostMarketData; }

    public Integer getcPriceHint() { return cPriceHint; }
    public void setcPriceHint(Integer cPriceHint) { this.cPriceHint = cPriceHint; }

    public String getcDataGranularity() { return cDataGranularity; }
    public void setcDataGranularity(String cDataGranularity) { this.cDataGranularity = cDataGranularity; }

    public Timestamp getcCreated() { return cCreated; }
    public void setcCreated(Timestamp cCreated) { this.cCreated = cCreated; }

    public Timestamp getcUpdated() { return cUpdated; }
    public void setcUpdated(Timestamp cUpdated) { this.cUpdated = cUpdated; }
}
