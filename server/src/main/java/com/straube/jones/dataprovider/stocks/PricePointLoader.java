package com.straube.jones.dataprovider.stocks;


import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.straube.jones.dataprovider.eurorates.CurrencyDB;
import com.straube.jones.db.DBConnection;
import com.straube.jones.db.DayCounter;
import com.straube.jones.dto.TablePriceDataResponse;
import com.straube.jones.dto.TablePriceDataResponse.PriceTableRow;

public class PricePointLoader
{
    private PricePointLoader()
    {}


    private static String getValidCodeOrNull(String code)
    {
        if (code == null || code.trim().isEmpty())
        { return null; }

        code = code.trim();
        if (code.length() > 12)
        { return null; }

        return code;
    }


    public static TablePriceDataResponse loadPrices(List<String> codes,
                                                    long start,
                                                    long end,
                                                    int type,
                                                    boolean convertToEuro)
    {
        TablePriceDataResponse data = new TablePriceDataResponse();
        long minVolume = Long.MAX_VALUE;
        long maxVolume = 0;
        long averageVolume = 0;
        String originalCurrency = null;

        if (codes == null || codes.isEmpty())
        { return data; }

        StringBuilder codesList = new StringBuilder();
        boolean first = true;
        for (String code : codes)
        {
            String validCode = getValidCodeOrNull(code);
            if (validCode != null)
            {
                if (!first)
                {
                    codesList.append(",");
                }
                codesList.append("'").append(validCode).append("'");
                first = false;
            }
        }

        if (codesList.length() == 0)
        { return data; }

        String codesString = codesList.toString();
        String query = "SELECT cIsin, cSymbol, cDate, cOpen, cHigh, cLow, cClose, cAdjClose, cCurrency, cVolume, cDayCounter FROM tPriceData WHERE (cIsin IN ("
                        + codesString
                        + ") OR cSymbol IN ("
                        + codesString
                        + ")) AND cDayCounter >= ? AND cDayCounter <= ? ORDER BY cIsin, cDayCounter "
                        + (type == 1 || type == 2 ? "ASC" : "DESC");

        Map<String, Double> normalizationValues = (type == 1) ? new HashMap<>() : null;
        Map<String, PriceTableRow> previousValues = (type == 2) ? new HashMap<>() : null;

        AtomicInteger rowCounter = new AtomicInteger(0);
        try (Connection connection = DBConnection.getStocksConnection();
                        PreparedStatement ps = connection.prepareStatement(query))
        {
            // Parameter setzen
            int paramIndex = 1;
            ps.setLong(paramIndex, DayCounter.get(start));
            paramIndex++ ;
            ps.setLong(paramIndex, DayCounter.get(end));
            // Query ausführen
            try (ResultSet rs = ps.executeQuery())
            {
                boolean firstRow = true;
                while (rs.next())
                {
                    PriceTableRow row = new PriceTableRow();
                    String isin = rs.getString("cIsin");
                    String symbol = rs.getString("cSymbol");
                    Date date = rs.getDate("cDate");
                    Long dateLong = date.getTime();
                    Double open = rs.getDouble("cOpen");
                    Double high = rs.getDouble("cHigh");
                    Double low = rs.getDouble("cLow");
                    Double close = rs.getDouble("cClose");
                    Double adjClose = rs.getDouble("cAdjClose");
                    String currency = rs.getString("cCurrency");
                    Long volume = rs.getLong("cVolume");
                    Integer dayCounter = rs.getInt("cDayCounter");

                    if (firstRow)
                    {
                        originalCurrency = currency;
                        firstRow = false;
                    }
                    if (volume < minVolume)
                    {
                        minVolume = volume;
                    }
                    if (volume > maxVolume)
                    {
                        maxVolume = volume;
                    }
                    if (type == 0)
                    {
                        open = CurrencyDB.getAsEuroOrOriginal(currency,
                                                              open,
                                                              DayCounter.yesterday(),
                                                              convertToEuro);
                        high = CurrencyDB.getAsEuroOrOriginal(currency,
                                                              high,
                                                              DayCounter.yesterday(),
                                                              convertToEuro);
                        low = CurrencyDB.getAsEuroOrOriginal(currency,
                                                             low,
                                                             DayCounter.yesterday(),
                                                             convertToEuro);
                        close = CurrencyDB.getAsEuroOrOriginal(currency,
                                                               close,
                                                               DayCounter.yesterday(),
                                                               convertToEuro);
                        adjClose = CurrencyDB.getAsEuroOrOriginal(currency,
                                                                  adjClose,
                                                                  DayCounter.yesterday(),
                                                                  convertToEuro);
                        currency = "EUR";

                        row = new PriceTableRow(isin,
                                                symbol,
                                                dateLong,
                                                open,
                                                high,
                                                low,
                                                close,
                                                adjClose,
                                                currency,
                                                volume,
                                                dayCounter);
                    }
                    if (type == 1)
                    {
                        Double base = normalizationValues.get(isin);
                        if (base == null)
                        {
                            if (adjClose == 0.0d)
                            {
                                // Skip rows until we find the first non-zero price to normalize against
                                continue;
                            }
                            normalizationValues.put(isin, adjClose);
                            base = adjClose;
                        }

                        if (base != 0.0d)
                        {
                            open = (open / base - 1.0d) * 100.0d;
                            high = (high / base - 1.0d) * 100.0d;
                            low = (low / base - 1.0d) * 100.0d;
                            close = (close / base - 1.0d) * 100.0d;
                            adjClose = (adjClose / base - 1.0d) * 100.0d;
                            currency = "%";
                        }
                        row = new PriceTableRow(isin,
                                                symbol,
                                                dateLong,
                                                open,
                                                high,
                                                low,
                                                close,
                                                adjClose,
                                                currency,
                                                volume,
                                                dayCounter);
                    }
                    else if (type == 2)
                    {
                        PriceTableRow prev = previousValues.get(isin);
                        previousValues.put(isin,
                                           new PriceTableRow(isin,
                                                             symbol,
                                                             dateLong,
                                                             open,
                                                             high,
                                                             low,
                                                             close,
                                                             adjClose,
                                                             currency,
                                                             volume,
                                                             dayCounter));
                        if (prev == null)
                        {
                            // Skip first row for each ISIN since we have no previous value to compare against
                            continue;
                        }
                        double nightGap = 0;//(open - prev.getClose()) / open * 100.0d;
                        high = (high - open) / open * 100.0d + nightGap;
                        low = (low - open) / open * 100.0d + nightGap;
                        close = (close - open) / open * 100.0d + nightGap;
                        adjClose = (adjClose - open) / open * 100.0d + nightGap;
                        open = nightGap;
                        currency = "%";

                        row = new PriceTableRow(isin,
                                                symbol,
                                                dateLong,
                                                open,
                                                high,
                                                low,
                                                close,
                                                adjClose,
                                                currency,
                                                volume,
                                                dayCounter);
                    }
                    data.addRow(row);
                    rowCounter.incrementAndGet();
                    averageVolume += volume;
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        if (minVolume > maxVolume)
        {
            minVolume = 0;
            maxVolume = 0;
            averageVolume = 0;
        }
        else if (rowCounter.get() > 0)
        {
            averageVolume = averageVolume / rowCounter.get();
        }
        String displayCurrency = "%";
        if (type == 0)
        {
            if (convertToEuro)
            {
                displayCurrency = "EUR";
            }
            else
            {
                displayCurrency = originalCurrency;
            }
        }
        data.setMeta(new TablePriceDataResponse.MetaData(minVolume,
                                                         maxVolume,
                                                         averageVolume,
                                                         originalCurrency,
                                                         displayCurrency));
        return data;
    }
}
