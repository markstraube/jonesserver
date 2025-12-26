package com.straube.jones.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Company {
    private String id;
    private String symbol;
    private String isin;
    private String shortName;
    private String longName;
    private String currency;
    private String instrumentType;
    private LocalDate firstTradeDate;
    private String exchangeName;
    private String fullExchangeName;
    private String exchangeTimezoneName;
    private String timezone;
    private boolean hasPrePostMarketData;
    private Integer priceHint;
    private String dataGranularity;
    private LocalDateTime created;
    private LocalDateTime updated;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }

    public String getShortName() { return shortName; }
    public void setShortName(String shortName) { this.shortName = shortName; }

    public String getLongName() { return longName; }
    public void setLongName(String longName) { this.longName = longName; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getInstrumentType() { return instrumentType; }
    public void setInstrumentType(String instrumentType) { this.instrumentType = instrumentType; }

    public LocalDate getFirstTradeDate() { return firstTradeDate; }
    public void setFirstTradeDate(LocalDate firstTradeDate) { this.firstTradeDate = firstTradeDate; }

    public String getExchangeName() { return exchangeName; }
    public void setExchangeName(String exchangeName) { this.exchangeName = exchangeName; }

    public String getFullExchangeName() { return fullExchangeName; }
    public void setFullExchangeName(String fullExchangeName) { this.fullExchangeName = fullExchangeName; }

    public String getExchangeTimezoneName() { return exchangeTimezoneName; }
    public void setExchangeTimezoneName(String exchangeTimezoneName) { this.exchangeTimezoneName = exchangeTimezoneName; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public boolean isHasPrePostMarketData() { return hasPrePostMarketData; }
    public void setHasPrePostMarketData(boolean hasPrePostMarketData) { this.hasPrePostMarketData = hasPrePostMarketData; }

    public Integer getPriceHint() { return priceHint; }
    public void setPriceHint(Integer priceHint) { this.priceHint = priceHint; }

    public String getDataGranularity() { return dataGranularity; }
    public void setDataGranularity(String dataGranularity) { this.dataGranularity = dataGranularity; }

    public LocalDateTime getCreated() { return created; }
    public void setCreated(LocalDateTime created) { this.created = created; }

    public LocalDateTime getUpdated() { return updated; }
    public void setUpdated(LocalDateTime updated) { this.updated = updated; }
}
