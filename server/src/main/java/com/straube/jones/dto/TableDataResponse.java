package com.straube.jones.dto;


import java.util.List;
import java.util.ArrayList;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TableDataResponse
{
    @JsonProperty("headers")
    private List<String> headers;

    @JsonProperty("rows")
    private List<TableRow> rows;

    public TableDataResponse()
    {
        this.headers = List.of("isin", "name", "date", "date-long", "value");
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


    public List<TableRow> getRows()
    {
        return rows;
    }


    public void setRows(List<TableRow> rows)
    {
        this.rows = rows;
    }


    public void addRow(String isin, String name, String date, Long dateLong, Double value)
    {
        rows.add(new TableRow(isin, name, date, dateLong, value));
    }

    public void addLines(List<String> lines, long fromDate, long toDate, int type)
    {
        // Implementation für die Verarbeitung von CSV-ähnlichen Datenzeilen
        for (String line : lines) {
            if (line != null && !line.trim().isEmpty()) {
                String[] parts = line.split(",");
                if (parts.length >= 5) {
                    try {
                        String isin = parts[0].trim();
                        String name = parts[1].trim();
                        String date = parts[2].trim();
                        Long dateLong = Long.parseLong(parts[3].trim());
                        Double value = Double.parseDouble(parts[4].trim());
                        
                        // Datum-Filter anwenden
                        if (dateLong >= fromDate && dateLong <= toDate) {
                            addRow(isin, name, date, dateLong, value);
                        }
                    } catch (NumberFormatException e) {
                        // Ignoriere fehlerhafte Zeilen
                    }
                }
            }
        }
    }

    @JsonProperty("data")
    public List<List<Object>> getData()
    {
        List<List<Object>> result = new ArrayList<>();
        
        // Header als Objekte hinzufügen
        List<Object> headerRow = new ArrayList<>();
        for (String header : headers) {
            headerRow.add(new FieldValue(header));
        }
        result.add(headerRow);
        
        // Datenzeilen als Objekte hinzufügen
        for (TableRow row : rows) {
            List<Object> dataRow = new ArrayList<>();
            dataRow.add(new FieldValue(row.getIsin()));
            dataRow.add(new FieldValue(row.getName()));
            dataRow.add(new FieldValue(row.getDate()));
            dataRow.add(new FieldValue(row.getDateLong()));
            dataRow.add(new FieldValue(row.getValue()));
            result.add(dataRow);
        }
        
        return result;
    }

    // Hilfsklasse um primitive Werte als Objekte zu verpacken
    public static class FieldValue {
        @JsonProperty("value")
        private Object value;

        public FieldValue(Object value) {
            this.value = value;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }
    }

    public static class TableRow
    {
        @JsonProperty("isin")
        private String isin;

        @JsonProperty("name")
        private String name;

        @JsonProperty("date")
        private String date;

        @JsonProperty("date_long")
        private Long dateLong;

        @JsonProperty("value")
        private Double value;

        public TableRow()
        {}


        public TableRow(String isin, String name, String date, Long dateLong, Double value)
        {
            this.isin = isin;
            this.name = name;
            this.date = date;
            this.dateLong = dateLong;
            this.value = value;
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


        public String getName()
        {
            return name;
        }


        public void setName(String name)
        {
            this.name = name;
        }


        public String getDate()
        {
            return date;
        }


        public void setDate(String date)
        {
            this.date = date;
        }


        public Long getDateLong()
        {
            return dateLong;
        }


        public void setDateLong(Long dateLong)
        {
            this.dateLong = dateLong;
        }


        public Double getValue()
        {
            return value;
        }


        public void setValue(Double value)
        {
            this.value = value;
        }
    }
}
