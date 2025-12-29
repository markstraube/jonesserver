package com.straube.jones.dto;


import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TablePriceDataResponse
{
    @JsonProperty("headers")
    private List<String> headers;

    @JsonProperty("rows")
    private List<PriceTableRow> rows;

    public TablePriceDataResponse()
    {
        this.headers = List.of("isin",
                               "symbol",
                               "date-long",
                               "open",
                               "high",
                               "low",
                               "close",
                               "adjClose",
                               "currency",
                               "volume",
                               "dayCounter");
        this.rows = new ArrayList<>();
    }


    public List<String> getHeaders()
    {
        return headers;
    }


    public void setHeaders(List<String> headers)
    {
        this.headers = headers;
    }


    public List<PriceTableRow> getRows()
    {
        return rows;
    }


    public void setRows(List<PriceTableRow> rows)
    {
        this.rows = rows;
    }


    public void addRow(String isin, String symbol, Long dateLong, Double open, Double high, Double low, Double close, Double adjClose, String currency, Long volume, Integer dayCounter)
    {
        rows.add(new PriceTableRow(isin, symbol, dateLong, open, high, low, close, adjClose, currency, volume, dayCounter));
    }


    public void addLines(List<String> lines, long fromDate, long toDate, int type)
    {
        // Implementation für die Verarbeitung von CSV-ähnlichen Datenzeilen
        for (String line : lines)
        {
            if (line != null && !line.trim().isEmpty())
            {
                String[] parts = line.split(",");
                if (parts.length >= 12)
                {
                    try
                    {
                        String isin = parts[0].trim();
                        String symbol = parts[1].trim();
                        Long dateLong = Long.parseLong(parts[2].trim());
                        Double open = Double.parseDouble(parts[3].trim());
                        Double high = Double.parseDouble(parts[4].trim());
                        Double low = Double.parseDouble(parts[5].trim());
                        Double close = Double.parseDouble(parts[6].trim());
                        Double adjClose = Double.parseDouble(parts[7].trim());
                        String currency = parts[8].trim();
                        Long volume = Long.parseLong(parts[9].trim());
                        Integer dayCounter = Integer.parseInt(parts[10].trim());

                        // Datum-Filter anwenden
                        if (dateLong >= fromDate && dateLong <= toDate)
                        {
                            addRow(isin,
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
                    }
                    catch (NumberFormatException e)
                    {
                        // Ignoriere fehlerhafte Zeilen
                    }
                }
            }
        }
    }


    @JsonIgnore // Diese Annotation verhindert, dass 'data' im JSON erscheint
    public List<List<TableCell>> getData()
    {
        List<List<TableCell>> data = new ArrayList<>();

        // Header als Objekte hinzufügen
        List<TableCell> headerRow = new ArrayList<>();
        for (String header : headers)
        {
            headerRow.add(new TableCell(header));
        }
        data.add(headerRow);

        // Datenzeilen als Objekte hinzufügen
        for (PriceTableRow row : rows)
        {
            List<TableCell> dataRow = new ArrayList<>();
            dataRow.add(new TableCell(row.getIsin()));
            dataRow.add(new TableCell(row.getSymbol()));
            dataRow.add(new TableCell(row.getDateLong()));
            dataRow.add(new TableCell(row.getOpen()));
            dataRow.add(new TableCell(row.getHigh()));
            dataRow.add(new TableCell(row.getLow()));
            dataRow.add(new TableCell(row.getClose()));
            dataRow.add(new TableCell(row.getAdjClose()));
            dataRow.add(new TableCell(row.getCurrency()));
            dataRow.add(new TableCell(row.getVolume()));
            dataRow.add(new TableCell(row.getDayCounter()));
            data.add(dataRow);
        }

        return data;
    }

    // Hilfsklasse um primitive Werte als Objekte zu verpacken
    public static class TableCell
    {
        @JsonProperty("value")
        private Object value;

        public TableCell(Object value)
        {
            this.value = value;
        }


        public Object getValue()
        {
            return value;
        }


        public void setValue(Object value)
        {
            this.value = value;
        }
    }

    public static class PriceTableRow
    {
        @JsonProperty("isin")
        private String isin;

        @JsonProperty("symbol")
        private String symbol;

        @JsonProperty("date_long")
        private Long dateLong;

        @JsonProperty("open")
        private Double open;

        @JsonProperty("high")
        private Double high;

        @JsonProperty("low")
        private Double low;

        @JsonProperty("close")
        private Double close;

        @JsonProperty("adjClose")
        private Double adjClose;

        @JsonProperty("currency")
        private String currency;

        @JsonProperty("volume")
        private Long volume;

        @JsonProperty("dayCounter")
        private Integer dayCounter;

        public PriceTableRow()
        {}


        public PriceTableRow(String isin,
                             String symbol,
                             Long dateLong,
                             Double open,
                             Double high,
                             Double low,
                             Double close,
                             Double adjClose,
                             String currency,
                             Long volume,
                             Integer dayCounter)
        {
            this.isin = isin;
            this.symbol = symbol;
            this.dateLong = dateLong;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.adjClose = adjClose;
            this.currency = currency;
            this.volume = volume;
            this.dayCounter = dayCounter;
        }


        // Getters und Setters
        public String getIsin()
        {
            return isin;
        }


        public void setIsin(String isin)
        {
            this.isin = isin;
        }


        public String getSymbol()
        {
            return symbol;
        }


        public void setSymbol(String symbol)
        {
            this.symbol = symbol;
        }


        public Long getDateLong()
        {
            return dateLong;
        }


        public void setDateLong(Long dateLong)
        {
            this.dateLong = dateLong;
        }


        public Double getOpen()
        {
            return open;
        }


        public void setOpen(Double open)
        {
            this.open = open;
        }


        public Double getHigh()
        {
            return high;
        }


        public void setHigh(Double high)
        {
            this.high = high;
        }


        public Double getLow()
        {
            return low;
        }


        public void setLow(Double low)
        {
            this.low = low;
        }


        public Double getClose()
        {
            return close;
        }


        public void setClose(Double close)
        {
            this.close = close;
        }


        public Double getAdjClose()
        {
            return adjClose;
        }


        public void setAdjClose(Double adjClose)
        {
            this.adjClose = adjClose;
        }


        public String getCurrency()
        {
            return currency;
        }


        public void setCurrency(String currency)
        {
            this.currency = currency;
        }


        public Long getVolume()
        {
            return volume;
        }


        public void setVolume(Long volume)
        {
            this.volume = volume;
        }


        public Integer getDayCounter()
        {
            return dayCounter;
        }


        public void setDayCounter(Integer dayCounter)
        {
            this.dayCounter = dayCounter;
        }
    }
}
