package com.straube.jones.cmd.onVista;


import java.util.ArrayList;
import java.util.List;

import com.straube.jones.cmd.onVista.Column.UNITS;

public class OnVistaModel
{
    public final static List<Column> columns = new ArrayList<>();

    /**
     */
    static
    {
        Column col = new Column("isin", "ISIN", UNITS.PRIMARY, "cIsin");
        columns.add(col);

        col = new Column("ref", "REF", UNITS.TEXT, "cRef");
        columns.add(col);

        col = new Column("instrument", "Name", UNITS.TEXT, "cName");
        columns.add(col);

        col = new Column("instrument.wkn", "WKN", UNITS.TEXT, "cNsin");
        columns.add(col);

        col = new Column("company.branch.name", "Branche", UNITS.TEXT, "cBranch");
        columns.add(col);

        col = new Column("company.branch.sector.name", "Sektor", UNITS.TEXT, "cSector");
        columns.add(col);

        col = new Column("company.nameCountry", "Land", UNITS.TEXT, "cCountryCode");
        columns.add(col);

        col = new Column("quote.last", "Kurs", UNITS.NUMBER, "cLast");
        columns.add(col);

        col = new Column("dateLong", "Date long",UNITS.LONG, "cDateLong");
        columns.add(col);

        col = new Column("currency", "Currency",UNITS.CURRENCY, "cCurrency");
        columns.add(col);

        col = new Column("quote.performancePct", "akt. Performance", UNITS.PERCENT, "cPerformance");
        columns.add(col);

        col = new Column("doubleValues.perfW52", "Performance 1J", UNITS.PERCENT, "cPerf1Year");
        columns.add(col);

        col = new Column("doubleValues.perfM6", "Performance 6M", UNITS.PERCENT, "cPerf6Months");
        columns.add(col);

        col = new Column("doubleValues.perfW4", "Performance 4W", UNITS.PERCENT, "cPerf4Weeks");
        columns.add(col);

        col = new Column("doubleValues.cnDivYieldM1", "Dividendenrendite 2021", UNITS.PERCENT, "cDividentPerf1Y");
        columns.add(col);

        col = new Column("doubleValues.cnDpsM1", "Dividende 2021", UNITS.NUMBER, "cDividend");
        columns.add(col);

        col = new Column("doubleValues.cnMarketCapM1", "Marktkapitalisierung 2021", UNITS.EURO, "cMarketCapitalization");
        columns.add(col);

        col = new Column("stocksDetails.theScreenerRisk", "Risiko-Rating",UNITS.LONG, "cRiskRating");
        columns.add(col);

        col = new Column("doubleValues.employeesM1", "Beschäftigte 2021", UNITS.LONG, "cEmployees");
        columns.add(col);

        col = new Column("doubleValues.turnoverM1", "Umsatz 2021",UNITS.EURO, "cTurnover");
        columns.add(col);

        col = new Column("updated", "updated",UNITS.AUTO, "cUpdated");
        columns.add(col);
    }
}

class Column
{
    public enum UNITS {
        EURO, USD, PERCENT, TEXT, NUMBER, RISK, PRIMARY, AUTO, LONG, CURRENCY
    }

    String id;
    String label;
    UNITS unit;
    String colName;

    Column(String id, String label, UNITS unit, String colName)
    {
        this.id = id;
        this.label = label;
        this.unit = unit;
        this.colName = colName;
    }
}

class Columns
{
    
}
