package com.straube.jones.cmd.onVista;


import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.text.ParseException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.straube.jones.cmd.currencies.EuroRates;
import com.straube.jones.cmd.onVista.Column.UNITS;

public class OnVistaModel
{
    public final static List<Column> columns = new ArrayList<>();
    public static Map<String, Object> mStocksCounter;
    public static final long OneWeekMillis = 7 * 24 * 3600 * 1000L;

    public static Map<String, Double> rates = new HashMap<>();

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

        col = new Column("exchange", "Börse", UNITS.TEXT, "cExchange");
        columns.add(col);

        col = new Column("dateLong", "Date long", UNITS.LONG, "cDateLong");
        columns.add(col);

        col = new Column("currency", "Currency", UNITS.CURRENCY, "cCurrency");
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

        col = new Column("stocksDetails.theScreenerRisk", "Risiko-Rating", UNITS.LONG, "cRiskRating");
        columns.add(col);

        col = new Column("doubleValues.employeesM1", "Beschäftigte 2021", UNITS.LONG, "cEmployees");
        columns.add(col);

        col = new Column("doubleValues.turnoverM1", "Umsatz 2021", UNITS.EURO, "cTurnover");
        columns.add(col);

        col = new Column("updated", "updated", UNITS.AUTO, "cUpdated");
        columns.add(col);
    }

    public static void init(String rootFolder)
    {
        try
        {
            (new EuroRates(rootFolder)).load(rates);
            byte[] buf = Files.readAllBytes(Paths.get(rootFolder, "fundamentals", "StocksCounter.json"));
            JSONObject jo = new JSONObject(new String(buf, "UTF-8"));
            mStocksCounter = jo.toMap();
        }
        catch (Exception e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


    public static List<Object> parseRow(Element row)
    {
        List<Object> lRow = new ArrayList<>();
        Elements entries = row.select("td");
        String isin = Column.parseIsin(entries.get(0));
        lRow.add(isin);
        lRow.add(Column.parseShortUrl(entries.get(0)));
        lRow.add(Column.parseName(entries.get(0)));
        try
        {
            lRow.add(Column.parseWkn(entries.get(1)));
            lRow.add(Column.parseBranch(entries.get(2)));
            lRow.add(Column.parseSector(entries.get(3)));
            lRow.add(Column.parseCountry(entries.get(4)));
            Double quote = Column.parseQuote(entries.get(5));
            lRow.add(quote);
            lRow.add(Column.parseExchange(entries.get(5)));
            Long quoteDate = Column.parseDateLong(entries.get(5));
            if (quoteDate < System.currentTimeMillis() - OneWeekMillis)
            { return null; }
            lRow.add(quoteDate);
            lRow.add(Column.parseCurrency(entries.get(5)));
            lRow.add(Column.parsePerformance(entries.get(6)));
            lRow.add(Column.parsePef52(entries.get(7)));
            lRow.add(Column.parsePefM6(entries.get(8)));
            lRow.add(Column.parsePefW4(entries.get(9)));
            lRow.add(Column.parseDivYield(entries.get(10)));
            lRow.add(Column.parseDividend(entries.get(11)));
            lRow.add(Column.parseCapitalization(entries.get(12)));
            lRow.add(Column.parseRisk(entries.get(13)));
            lRow.add(Column.parseEmployees(entries.get(14)));
            lRow.add(Column.parseTurnover(entries.get(15)));

            return lRow;
        }
        catch (Exception ignore)
        {
            System.out.println(String.format("ERR: NOT adding name: %s, ISIN: %s", lRow.get(2), lRow.get(0)));
        }
        return null;
    }


    private static Double calcCaptitalization(String isin, Double quote, String currency, Double fallBack)
    {
        if (mStocksCounter != null)
        {
            try
            {
                Object o = mStocksCounter.get(isin);
                return makeDouble(o) * quote / rates.get(currency.toUpperCase());
            }
            catch (Exception ignore)
            {
                ignore.printStackTrace();
            }
        }
        return fallBack;
    }


    public static PreparedStatement setParams(PreparedStatement stmnt, List<Object> params)
    {
        try
        {
            String isin = String.valueOf(params.get(0));
            Double quote = makeDouble(params.get(7));
            String currency = String.valueOf(params.get(10));
            Double capitalization = makeDouble((params.get(17)));
            capitalization = calcCaptitalization(isin, quote, currency, capitalization);

            stmnt.setString(1, isin);
            stmnt.setString(2, String.valueOf(params.get(1)));
            stmnt.setString(3, String.valueOf(params.get(2)));
            stmnt.setString(4, String.valueOf(params.get(3)));
            stmnt.setString(5, String.valueOf(params.get(4)));
            stmnt.setString(6, String.valueOf(params.get(5)));
            stmnt.setString(7, String.valueOf(params.get(6)));
            stmnt.setDouble(8, quote);
            stmnt.setString(9, String.valueOf(params.get(8)));
            stmnt.setLong(10, makeLong(params.get(9)));
            stmnt.setString(11, currency);
            stmnt.setDouble(12, makeDouble((params.get(11))));
            stmnt.setDouble(13, makeDouble((params.get(12))));
            stmnt.setDouble(14, makeDouble((params.get(13))));
            stmnt.setDouble(15, makeDouble((params.get(14))));
            stmnt.setDouble(16, makeDouble((params.get(15))));
            stmnt.setDouble(17, makeDouble((params.get(16))));
            stmnt.setDouble(18, capitalization);
            stmnt.setLong(19, makeLong(params.get(18)));
            stmnt.setLong(20, makeLong(params.get(19)));
            stmnt.setDouble(21, makeDouble((params.get(20))));
            stmnt.setTimestamp(22, new Timestamp(System.currentTimeMillis()));
        }
        catch (Exception e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return stmnt;
    }


    public static Double makeDouble(Object object)
    {
        if (object instanceof Double)
        {
            return (Double)object;
        }
        else if (object instanceof Long)
        {
            return ((Long)object).doubleValue();
        }
        else if (object instanceof Integer)
        { return ((Integer)object).doubleValue(); }
        return 0d;
    }


    public static Long makeLong(Object object)
    {
        if (object instanceof Long)
        {
            return (Long)object;
        }
        else if (object instanceof Integer)
        { return ((Integer)object).longValue(); }
        return 0L;
    }
}


class Column
{
    public enum UNITS
    {
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


    public static Double parseTurnover(Element element)
    {
        Element e = element.select("data").first();
        String val = e.attributes().get("value");
        return Double.parseDouble(val);
    }


    public static Long parseEmployees(Element element)
    {
        Element e = element.select("data").first();
        String val = e.attributes().get("value");
        return Long.parseLong(val);
    }


    public static Long parseRisk(Element element)
    {
        return Long.valueOf(1L);
    }


    public static Double parseCapitalization(Element element)
    {
        Element e = element.select("data").first();
        String val = e.attributes().get("value");
        return Double.parseDouble(val);
    }


    public static Double parseDividend(Element element)
    {
        Element e = element.select("data").first();
        String val = e.attributes().get("value");
        return Double.parseDouble(val);
    }


    public static Double parseDivYield(Element element)
    {
        Element e = element.select("data").first();
        String val = e.attributes().get("value");
        return Double.parseDouble(val);
    }


    public static Double parsePefM6(Element element)
    {
        Element e = element.select("data").first();
        String val = e.attributes().get("value");
        return Double.parseDouble(val);
    }


    public static Double parsePefW4(Element element)
    {
        Element e = element.select("data").first();
        String val = e.attributes().get("value");
        return Double.parseDouble(val);
    }


    public static Double parsePef52(Element element)
    {
        Element e = element.select("data").first();
        String val = e.attributes().get("value");
        return Double.parseDouble(val);
    }


    public static Double parsePerformance(Element element)
    {
        Element e = element.select("data").first();
        String val = e.attributes().get("value");
        return Double.parseDouble(val);
    }


    public static String parseExchange(Element element)
        throws ParseException
    {
        Element e = element.select("span > span").first();
        String title = e.attributes().get("title");
        return title;
    }


    public static Long parseDateLong(Element element)
    {
        Element e = element.select("span > time").first();
        String time = e.attributes().get("datetime");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        ZonedDateTime zonedDateTime = ZonedDateTime.parse(time, formatter);
        return zonedDateTime.toInstant().toEpochMilli();
    }


    public static Double parseQuote(Element element)
    {
        Element e = element.select("data").first();
        String val = e.attributes().get("value");
        return Double.parseDouble(val);
    }


    public static String parseCountry(Element element)
    {
        return element.text();
    }


    public static String parseBranch(Element element)
    {
        return element.text();
    }


    public static String parseSector(Element element)
    {
        return element.text();
    }


    public static String parseWkn(Element element)
    {
        return element.text();
    }


    public static String parseName(Element element)
    {
        Element e = element.select("div > div > div > a").first();
        return e.text();
    }


    public static String parseCurrency(Element element)
    {
        Element e = element.select("td > data > span").first();
        return e.text(); // currency
    }


    public static String parseIsin(Element element)
    {
        Element e = element.select("div > div > div > a").first();
        String title = e.attr("title");
        return title.split(":")[2].trim();
    }


    public static String parseShortUrl(Element element)
    {
        Element e = element.select("div > div > div > a").first();
        String[] ref = e.attr("href").split("/");
        return ref[ref.length - 1];
    }
}
