package com.straube.jones.dataprovider.yahoo;


import java.sql.Date;
import java.time.LocalDate;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.straube.jones.dto.CompanyResponse;

public class CompanyResolver
{

    private static final Logger LOGGER = Logger.getLogger(CompanyResolver.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private CompanyResolver()
    {}


    public static CompanyResponse resolve(String isin)
    {
        try
        {
            // 1. Resolve ISIN to Symbol
            String symbol = SymbolResolver.resolveCode(isin);
            if (symbol == null)
            {
                LOGGER.warning("Could not resolve symbol for ISIN: " + isin);
                return null;
            }

            // 2. Download JSON from Yahoo
            String jsonResponse = YahooPriceDownloader.downloadRawJson(symbol,
                                                                       LocalDate.now().minusDays(2),
                                                                       LocalDate.now());
            if (jsonResponse == null || jsonResponse.isEmpty())
            { return null; }

            // 3. Parse JSON and populate CompanyResponse
            return parseCompanyData(jsonResponse, symbol, isin);

        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, "Error resolving company for ISIN: " + isin, e);
            return null;
        }
    }


    private static CompanyResponse parseCompanyData(String json, String symbol, String isin)
    {
        try
        {
            JsonNode rootNode = objectMapper.readTree(json);
            JsonNode chartNode = rootNode.path("chart");
            JsonNode resultNode = chartNode.path("result");

            JsonNode meta = null;
            if (!resultNode.isMissingNode() && resultNode.isArray() && resultNode.size() > 0)
            {
                JsonNode dataNode = resultNode.get(0);
                meta = dataNode.path("meta");
            }

            if (meta == null || meta.isMissingNode())
            {
                LOGGER.warning("Meta data missing in response for symbol: " + symbol);
                return null;
            }

            CompanyResponse company = new CompanyResponse();
            company.setIsin(isin);
            company.setSymbol(meta.path("symbol").asText(symbol));
            company.setShortName(meta.path("shortName").asText(""));
            company.setLongName(meta.path("longName").asText(""));
            company.setCurrency(meta.path("currency").asText(""));
            company.setInstrumentType(meta.path("instrumentType").asText(""));

            long firstTradeDateSec = meta.path("firstTradeDate").asLong(0);
            if (firstTradeDateSec > 0)
            {
                company.setFirstTradeDate(new Date(firstTradeDateSec * 1000));
            }

            company.setExchangeName(meta.path("exchangeName").asText(""));
            company.setFullExchangeName(meta.path("fullExchangeName").asText(""));
            company.setExchangeTimezoneName(meta.path("exchangeTimezoneName").asText(""));
            company.setTimezone(meta.path("timezone").asText(""));
            company.setHasPrePostMarketData(meta.path("hasPrePostMarketData").asBoolean(false));
            company.setPriceHint(meta.path("priceHint").asInt(0));
            company.setDataGranularity(meta.path("dataGranularity").asText(""));

            // Set timestamps? cCreated/cUpdated are usually DB managed. 
            // We'll leave them null or set them to now if needed, but the requirement was "Company Daten"

            return company;

        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, "Error parsing JSON for ISIN: " + isin, e);
            return null;
        }
    }
}
