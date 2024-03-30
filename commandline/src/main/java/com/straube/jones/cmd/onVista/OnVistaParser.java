package com.straube.jones.cmd.onVista;


import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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

import java.io.File;

import org.json.JSONObject;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.straube.jones.cmd.currencies.EuroRates;

public class OnVistaParser
{

    public static Map<String, Object> mStocksCounter;
    public static final long OneWeekMillis = 7 * 24 * 3600 * 1000L;

    public static Map<String, Double> rates = new HashMap<>();

    public static void init(File rootFolder)
    {
        try
        {
            (new EuroRates(rootFolder)).load(rates);
            byte[] buf = Files.readAllBytes(Paths.get(rootFolder.getAbsolutePath(), "fundamentals", "StocksCounter.json"));
            JSONObject jo = new JSONObject(new String(buf, StandardCharsets.UTF_8));
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
        String isin = Parser.parseIsin(entries.get(0));
        lRow.add(isin);
        lRow.add(Parser.parseShortUrl(entries.get(0)));
        lRow.add(Parser.parseName(entries.get(0)));
        try
        {
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
            System.out.println(String.format("ERR: NOT adding name: %s, ISIN: %s", lRow.get(2), lRow.get(0)));
        }
        return null;
    }


    private static Double calcCaptitalization(String isin, Double quote, String currency, Double fallBack)
    {
        double result = fallBack;
        if (mStocksCounter != null)
        {
            try
            {
                Object o = mStocksCounter.get(isin);
                if ("GBP".equalsIgnoreCase(currency))
                {
                    result = makeDouble(o) * quote / 100 / rates.get(currency.toUpperCase());
                    if (result == 0)
                    {
                        result = fallBack / 100 / rates.get(currency.toUpperCase());
                    }
                }
                else
                {
                    result = makeDouble(o) * quote / rates.get(currency.toUpperCase());
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
        Element e = element.select("div > a").first();
        return e.text();
    }


    public static String parseCurrency(Element element)
    {
        Element e = element.select("td > data > span").first();
        return e.text(); // currency
    }


    public static String parseIsin(Element element)
    {
        Element e = element.select("div > a").first();
        String title = e.attr("title");
        return title.split("·")[2].trim();
    }


    public static String parseShortUrl(Element element)
    {
        Element e = element.select("div > a").first();
        String[] ref = e.attr("href").split("/");
        return ref[ref.length - 1];
    }
}
