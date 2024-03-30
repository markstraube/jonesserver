package com.straube.jones.cmd.db;


import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class OnVistaModel
{
    public final static List<Column> columns = new ArrayList<>();
    public final static String TABLENAME = "tOnVista";
    /**
     */
    static
    {
        Column col = new Column("isin", "ISIN", Column.UNITS.PRIMARY, "cIsin");
        columns.add(col);

        col = new Column("ref", "REF", Column.UNITS.TEXT, "cRef");
        columns.add(col);

        col = new Column("instrument", "Name", Column.UNITS.TEXT, "cName");
        columns.add(col);

        col = new Column("instrument.wkn", "WKN", Column.UNITS.TEXT, "cNsin");
        columns.add(col);

        col = new Column("company.branch.name", "Branche", Column.UNITS.TEXT, "cBranch");
        columns.add(col);

        col = new Column("company.branch.sector.name", "Sektor", Column.UNITS.TEXT, "cSector");
        columns.add(col);

        col = new Column("company.nameCountry", "Land", Column.UNITS.TEXT, "cCountryCode");
        columns.add(col);

        col = new Column("quote.last", "Kurs", Column.UNITS.NUMBER, "cLast");
        columns.add(col);

        col = new Column("exchange", "Börse", Column.UNITS.TEXT, "cExchange");
        columns.add(col);

        col = new Column("dateLong", "Date long", Column.UNITS.LONG, "cDateLong");
        columns.add(col);

        col = new Column("currency", "Currency", Column.UNITS.CURRENCY, "cCurrency");
        columns.add(col);

        col = new Column("quote.performancePct", "akt. Performance", Column.UNITS.PERCENT, "cPerformance");
        columns.add(col);

        col = new Column("doubleValues.perfW52", "Performance 1J", Column.UNITS.PERCENT, "cPerf1Year");
        columns.add(col);

        col = new Column("doubleValues.perfM6", "Performance 6M", Column.UNITS.PERCENT, "cPerf6Months");
        columns.add(col);

        col = new Column("doubleValues.perfW4", "Performance 4W", Column.UNITS.PERCENT, "cPerf4Weeks");
        columns.add(col);

        col = new Column("doubleValues.cnDivYieldM0", "Dividendenrendite 2021", Column.UNITS.PERCENT, "cDividendYield");
        columns.add(col);

        col = new Column("doubleValues.cnDpsM0", "Dividende 2021", Column.UNITS.NUMBER, "cDividend");
        columns.add(col);

        col = new Column("doubleValues.cnMarketCapM1", "Marktkapitalisierung 2021", Column.UNITS.EURO, "cMarketCapitalization");
        columns.add(col);

        col = new Column("stocksDetails.theScreenerRisk", "Risiko-Rating", Column.UNITS.LONG, "cRiskRating");
        columns.add(col);

        col = new Column("doubleValues.employeesM1", "Beschäftigte 2021", Column.UNITS.LONG, "cEmployees");
        columns.add(col);

        col = new Column("doubleValues.turnoverM1", "Umsatz 2021", Column.UNITS.EURO, "cTurnover");
        columns.add(col);

        col = new Column("updated", "updated", Column.UNITS.AUTO, "cUpdated");
        columns.add(col);
    }

    public static List<Column> getModel()
    {
        return columns;
    }

    public static void create() throws SQLException
    {
        OnVistaDB.create(TABLENAME, columns);
    }
}
