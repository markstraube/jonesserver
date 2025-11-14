package com.straube.jones.cmd.db;


import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class StocksModel
{
    public final static List<Column> columns = new ArrayList<>();
    public final static String TABLENAME = "tStocks";

    /**
     */
    static
    {
        Column col = new Column("id", "ID", Column.UNITS.PRIMARY, "cID");
        columns.add(col);

        col = new Column("isin", "ISIN", Column.UNITS.TEXT, "cIsin");
        columns.add(col);

        col = new Column("quote.last", "Kurs", Column.UNITS.NUMBER, "cLast");
        columns.add(col);

        col = new Column("currency", "Currency", Column.UNITS.CURRENCY, "cCurrency");
        columns.add(col);

        col = new Column("timestamp", "Timestamp", Column.UNITS.LONG, "cDateLong");
        columns.add(col);

        col = new Column("date", "Date", Column.UNITS.TIMESTAMP, "cDate");
        columns.add(col);

        col = new Column("sequence", "Sequence", Column.UNITS.UNSIGNED, "cSequence");
        columns.add(col);
    }

    public static List<Column> getModel()
    {
        return columns;
    }


    public static void create()
        throws SQLException
    {
        OnVistaDB.create(TABLENAME, columns);
    }
}
