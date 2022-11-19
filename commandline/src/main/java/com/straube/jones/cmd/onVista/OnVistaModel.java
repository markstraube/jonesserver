package com.straube.jones.cmd.onVista;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OnVistaModel
{
    public enum UNITS {
        EURO, USD, PERCENT, TEXT, NUMBER, RISK, PRIMARY
    }

    Columns columns = new Columns();

    public class Column
    {
        String id;
        String label;
        UNITS unit;
        Map<String, String> queries;

        Column(String id, String label, UNITS unit)
        {
            this.id = id;
            this.label = label;
            this.unit = unit;
            queries = new HashMap<>();
        }


        void addQuery(String name, String query)
        {
            queries.put(name, query);
        }
    }

    public class Columns
    {
        List<Column> columns = new ArrayList<>();

        /**
         */
        Columns()
        {
            Column col = new Column("isin", "ISIN", UNITS.PRIMARY);
            columns.add(col);

            col = new Column("instrument", "Name", UNITS.TEXT);
            columns.add(col);

            col = new Column("instrument.wkn", "WKN", UNITS.TEXT);
            columns.add(col);

            col = new Column("company.branch.name", "Branche", UNITS.TEXT);
            columns.add(col);

            col = new Column("company.branch.sector.name", "Sektor", UNITS.TEXT);
            columns.add(col);

            col = new Column("company.nameCountry", "Land", UNITS.TEXT);
            columns.add(col);

            col = new Column("quote.last", "Kurs", UNITS.NUMBER);
            columns.add(col);

            col = new Column("quote.performancePct", "akt. Performance", UNITS.PERCENT);
            columns.add(col);

            col = new Column("doubleValues.perfW52", "Performance 1J", UNITS.PERCENT);
            columns.add(col);

            col = new Column("doubleValues.perfM6", "Performance 6M", UNITS.PERCENT);
            columns.add(col);

            col = new Column("doubleValues.perfW4", "Performance 4W", UNITS.PERCENT);
            columns.add(col);

            col = new Column("doubleValues.cnDivYieldM1", "Dividendenrendite 2021", UNITS.PERCENT);
            columns.add(col);

            col = new Column("doubleValues.cnDpsM1", "Dividende 2021", UNITS.NUMBER);
            columns.add(col);

            col = new Column("doubleValues.cnMarketCapM1", "Marktkapitalisierung 2021", UNITS.EURO);
            columns.add(col);

            col = new Column("stocksDetails.theScreenerRisk", "Risiko-Rating",UNITS.RISK);
            columns.add(col);

            col = new Column("doubleValues.employeesM1", "Beschäftigte 2021", UNITS.NUMBER);
            columns.add(col);

            col = new Column("doubleValues.turnoverM1", "Umsatz 2021",UNITS.EURO);
            columns.add(col);
        }
    }
}
