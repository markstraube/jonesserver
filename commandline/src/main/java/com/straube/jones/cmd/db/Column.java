package com.straube.jones.cmd.db;


public class Column
{
    public enum UNITS
    {
        EURO, USD, PERCENT, TEXT, NUMBER, RISK, PRIMARY, AUTO, LONG, CURRENCY, DATE, TIMESTAMP
    }

    public String id;
    public String label;
    public UNITS unit;
    public String colName;

    public Column(String id, String label, UNITS unit, String colName)
    {
        this.id = id;
        this.label = label;
        this.unit = unit;
        this.colName = colName;
    }
}
