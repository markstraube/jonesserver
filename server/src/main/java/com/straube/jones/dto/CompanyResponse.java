package com.straube.jones.dto;


import java.sql.Timestamp;
import java.sql.Date;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response object containing detailed company master data.")
public class CompanyResponse
{

    @Schema(description = "Unique identifier for the company record.", example = "550e8400-e29b-41d4-a716-446655440000")
    private String id;

    @Schema(description = "The stock symbol (ticker) used by the exchange.", example = "AAPL")
    private String symbol;

    @Schema(description = "The International Securities Identification Number (ISIN).", example = "US0378331005")
    private String isin;

    @Schema(description = "Short name of the company.", example = "Apple Inc.")
    private String shortName;

    @Schema(description = "Full legal name of the company.", example = "Apple Inc.")
    private String longName;

    @Schema(description = "Currency code for the stock prices.", example = "USD")
    private String currency;

    @Schema(description = "Type of financial instrument.", example = "EQUITY")
    private String instrumentType;

    @Schema(description = "Date of the first trade recorded.", example = "1980-12-12")
    private Date firstTradeDate;

    @Schema(description = "Name of the exchange where the stock is traded.", example = "NasdaqGS")
    private String exchangeName;

    @Schema(description = "Full name of the exchange.", example = "Nasdaq Global Select")
    private String fullExchangeName;

    @Schema(description = "Name of the timezone for the exchange.", example = "America/New_York")
    private String exchangeTimezoneName;

    @Schema(description = "Timezone abbreviation.", example = "EST")
    private String timezone;

    @Schema(description = "Indicates if pre-market and post-market data is available.", example = "true")
    private boolean hasPrePostMarketData;

    @Schema(description = "Price hint for formatting.", example = "2")
    private Integer priceHint;

    @Schema(description = "Granularity of the data available.", example = "1d")
    private String dataGranularity;

    @Schema(description = "Timestamp when the record was created.", example = "2023-01-01T12:00:00Z")
    private Timestamp created;

    @Schema(description = "Timestamp when the record was last updated.", example = "2023-01-02T12:00:00Z")
    private Timestamp updated;

    public String getId()
    {
        return id;
    }


    public void setId(String id)
    {
        this.id = id;
    }


    public String getSymbol()
    {
        return symbol;
    }


    public void setSymbol(String symbol)
    {
        this.symbol = symbol;
    }


    public String getIsin()
    {
        return isin;
    }


    public void setIsin(String isin)
    {
        this.isin = isin;
    }


    public String getShortName()
    {
        return shortName;
    }


    public void setShortName(String shortName)
    {
        this.shortName = shortName;
    }


    public String getLongName()
    {
        return longName;
    }


    public void setLongName(String longName)
    {
        this.longName = longName;
    }


    public String getCurrency()
    {
        return currency;
    }


    public void setCurrency(String currency)
    {
        this.currency = currency;
    }


    public String getInstrumentType()
    {
        return instrumentType;
    }


    public void setInstrumentType(String instrumentType)
    {
        this.instrumentType = instrumentType;
    }


    public Date getFirstTradeDate()
    {
        return firstTradeDate;
    }


    public void setFirstTradeDate(Date firstTradeDate)
    {
        this.firstTradeDate = firstTradeDate;
    }


    public String getExchangeName()
    {
        return exchangeName;
    }


    public void setExchangeName(String exchangeName)
    {
        this.exchangeName = exchangeName;
    }


    public String getFullExchangeName()
    {
        return fullExchangeName;
    }


    public void setFullExchangeName(String fullExchangeName)
    {
        this.fullExchangeName = fullExchangeName;
    }


    public String getExchangeTimezoneName()
    {
        return exchangeTimezoneName;
    }


    public void setExchangeTimezoneName(String exchangeTimezoneName)
    {
        this.exchangeTimezoneName = exchangeTimezoneName;
    }


    public String getTimezone()
    {
        return timezone;
    }


    public void setTimezone(String timezone)
    {
        this.timezone = timezone;
    }


    public boolean isHasPrePostMarketData()
    {
        return hasPrePostMarketData;
    }


    public void setHasPrePostMarketData(boolean hasPrePostMarketData)
    {
        this.hasPrePostMarketData = hasPrePostMarketData;
    }


    public Integer getPriceHint()
    {
        return priceHint;
    }


    public void setPriceHint(Integer priceHint)
    {
        this.priceHint = priceHint;
    }


    public String getDataGranularity()
    {
        return dataGranularity;
    }


    public void setDataGranularity(String dataGranularity)
    {
        this.dataGranularity = dataGranularity;
    }


    public Timestamp getCreated()
    {
        return created;
    }


    public void setCreated(Timestamp created)
    {
        this.created = created;
    }


    public Timestamp getUpdated()
    {
        return updated;
    }


    public void setUpdated(Timestamp updated)
    {
        this.updated = updated;
    }
}
