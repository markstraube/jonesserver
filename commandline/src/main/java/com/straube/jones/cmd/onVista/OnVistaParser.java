package com.straube.jones.cmd.onVista;


import java.io.File;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.text.ParseException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.straube.jones.cmd.currencies.CurrencyDB;
import com.straube.jones.cmd.currencies.EuroRates;
import com.straube.jones.cmd.db.StockCounterDB;

public class OnVistaParser
{
    public static final long OneWeekMillis = 7 * 24 * 3600 * 1000L;

    public static void init(File rootFolder)
    {
        try
        {
            (new EuroRates(rootFolder)).load();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }


    public static List<Object> parseRow(Element row)
    {
        List<Object> lRow = new ArrayList<>();
        try
        {
            Elements entries = row.select("td");
            String isin = Parser.parseIsin(entries.get(0));
            if (isin == null || isin.isEmpty())
            {
                System.out.println("### SKIPPING row with no ISIN");
                return null;
            }
            lRow.add(isin);
            lRow.add(Parser.parseName(entries.get(0)));
            lRow.add(Parser.parseWkn(entries.get(1)));
            lRow.add(Parser.parseBranch(entries.get(2)));
            lRow.add(Parser.parseSector(entries.get(3)));
            lRow.add(Parser.parseCountry(entries.get(4)));
            Double quote = Parser.parseQuote(entries.get(5));
            lRow.add(quote);
            lRow.add(Parser.parseExchange(entries.get(5)));
            Long quoteDate = Parser.parseDateLong(entries.get(5));
            if ((quoteDate < System.currentTimeMillis() - OneWeekMillis) && !isin.equalsIgnoreCase("DK0062498333"))
            {
                System.out.println(String.format("### SKIPPING outdated ISIN:%s, Date:%d", isin, quoteDate));
                return null;
            }
            lRow.add(quoteDate);
            lRow.add(Parser.parseCurrency(entries.get(5)));
            lRow.add(Parser.parsePerformance(entries.get(6)));
            lRow.add(Parser.parsePef52(entries.get(7)));
            lRow.add(Parser.parsePefM6(entries.get(8)));
            lRow.add(Parser.parsePefW4(entries.get(9)));
            lRow.add(Parser.parseDivYield(entries.get(10)));
            lRow.add(Parser.parseDividend(entries.get(11)));
            lRow.add(Parser.parseCapitalization(entries.get(12)));
            lRow.add(Parser.parseRisk(entries.get(13)));
            lRow.add(Parser.parseEmployees(entries.get(14)));
            lRow.add(Parser.parseTurnover(entries.get(15)));

            return lRow;
        }
        catch (Exception ignore)
        {
            System.out.println(String.format("Could not parse row: %s", ignore.getMessage()));
            System.out.println(row.toString()); 
            ignore.printStackTrace();           
        }
        return null;
    }


    private static Double calcCaptitalization(String isin, Double quote, String currency, Double fallBack)
    {
        double result = fallBack;
        long stockCount = StockCounterDB.getStockCounter(isin);
        if (stockCount != 0)
        {
            try
            {
                if ("GBP".equalsIgnoreCase(currency))
                {
                    result = CurrencyDB.getAsEuro(currency, stockCount * quote / 100, System.currentTimeMillis());
                    if (result == 0)
                    {
                        result = CurrencyDB.getAsEuro(currency, fallBack / 100, System.currentTimeMillis());
                    }
                }
                else
                {
                    result = CurrencyDB.getAsEuro(currency, stockCount * quote, System.currentTimeMillis());
                    if (result == 0)
                    {
                        result = fallBack;
                    }
                }
            }
            catch (Exception ignore)
            {
                ignore.printStackTrace();
            }
        }
        return result;
    }


    public static PreparedStatement setParams(PreparedStatement stmnt, List<Object> params)
    {
        try
        {
            String isin = String.valueOf(params.get(0));
            Double quote = makeDouble(params.get(6));
            String currency = String.valueOf(params.get(9));
            Double capitalization = makeDouble((params.get(16)));
            capitalization = calcCaptitalization(isin, quote, currency, capitalization);

            stmnt.setString(1, isin);
            stmnt.setString(2, String.valueOf(params.get(1)));
            stmnt.setString(3, String.valueOf(params.get(2)));
            stmnt.setString(4, String.valueOf(params.get(3)));
            stmnt.setString(5, String.valueOf(params.get(4)));
            stmnt.setString(6, String.valueOf(params.get(5)));
            stmnt.setDouble(7, quote);
            stmnt.setString(8, String.valueOf(params.get(7)));
            stmnt.setLong(9, makeLong(params.get(8)));
            stmnt.setString(10, currency);
            stmnt.setDouble(11, makeDouble((params.get(10))));
            stmnt.setDouble(12, makeDouble((params.get(11))));
            stmnt.setDouble(13, makeDouble((params.get(12))));
            stmnt.setDouble(14, makeDouble((params.get(13))));
            stmnt.setDouble(15, makeDouble((params.get(14))));
            stmnt.setDouble(16, makeDouble((params.get(15))));
            stmnt.setDouble(17, capitalization);
            stmnt.setLong(18, makeLong(params.get(17)));
            stmnt.setLong(19, makeLong(params.get(18)));
            stmnt.setDouble(20, makeDouble((params.get(19))));
            stmnt.setTimestamp(21, new Timestamp(System.currentTimeMillis()));
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
        if (object instanceof BigDecimal)
        {
            return ((BigDecimal)object).doubleValue();
        }
        else if (object instanceof Double)
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


class Parser
{
    public static Double parseTurnover(Element element)
    {
        Element e = element.select("data").first();
        if (e == null)
        {
            return 0.0d; // No data available
        }
        String val = e.attributes().get("value");
        return Double.parseDouble(val);
    }


    public static Long parseEmployees(Element element)
    {
        Element e = element.select("data").first();
        if(e == null)
        {
            return 0L; // No data available
        }
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
        if (e == null)
        {
            return 0.0d; // No data available
        }
        String val = e.attributes().get("value");
        return Double.parseDouble(val);
    }


    public static Double parseDivYield(Element element)
    {
        Element e = element.select("data").first();
        if (e == null)
        {
            return 0.0d; // No data available
        }
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
        if( e == null)
        {
            return 0.0d; // No data available
        }
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
        //Element e = element.select("div > a").first();
        return element.text();
    }


    public static String parseCurrency(Element element)
    {
        Element e = element.select("td > data > span").first();
        return e.text(); // currency
    }


    public static String parseIsin(Element element)
    {
        Element e = element.select("div > a").first();
        if (e == null)
        {
            return ""; // No ISIN available
        }
        String title = e.attr("title");
        return title.split("·")[2].trim();
    }
}
