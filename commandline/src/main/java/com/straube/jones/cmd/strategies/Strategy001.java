package com.straube.jones.cmd.strategies;


import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import com.straube.jones.cmd.db.DBConnection;

public class Strategy001
{
    private static final int REQUIRED_DAYS = 282;
    private static final String OUTPUT_FILE = "strategy001_results.csv";

    private static final String SQL_ISINS = "SELECT DISTINCT cIsin FROM tPriceData ORDER BY cIsin";
    private static final String SQL_COUNT = "SELECT COUNT(*) AS cnt FROM tPriceData WHERE cIsin = ?";
    private static final String SQL_COMPANY_NAME = "SELECT cLongName FROM tCompany WHERE cIsin = ?";
    private static final String SQL_RECENT = "SELECT cDate, cOpen, cClose, cHigh, cLow "
                                           + "FROM tPriceData WHERE cIsin = ? ORDER BY cDate DESC LIMIT ?";

    public void execute()
    {
        int processedCount = 0;
        int ignoredCount = 0;
        Path outputPath = Paths.get(OUTPUT_FILE);

        try (Connection connection = DBConnection.getStocksConnection();
                BufferedWriter writer = Files.newBufferedWriter(outputPath);
                PreparedStatement allIsinsStmt = connection.prepareStatement(SQL_ISINS);
                PreparedStatement countStmt = connection.prepareStatement(SQL_COUNT);
                PreparedStatement companyStmt = connection.prepareStatement(SQL_COMPANY_NAME);
                PreparedStatement recentStmt = connection.prepareStatement(SQL_RECENT))
        {
            writer.write("cIsin,cName,cResult1,cResult2,cResult3");
            writer.newLine();

            try (ResultSet isinsRs = allIsinsStmt.executeQuery())
            {
                while (isinsRs.next())
                {
                    String isin = isinsRs.getString("cIsin");
                    if (isin == null || isin.isBlank())
                    {
                        continue;
                    }

                    int totalDays = getTotalDays(countStmt, isin);
                    if (totalDays < REQUIRED_DAYS)
                    {
                        ignoredCount++;
                        System.out.println("SKIP: cIsin "
                                        + isin
                                        + " hat nur "
                                        + totalDays
                                        + " Handelstage (< "
                                        + REQUIRED_DAYS
                                        + ") – wird ignoriert.");
                        continue;
                    }

                    List<PriceDay> recentDays = getRecentDays(recentStmt, isin);
                    if (recentDays.size() < REQUIRED_DAYS)
                    {
                        ignoredCount++;
                        System.out.println("SKIP: cIsin "
                                        + isin
                                        + " hat nur "
                                        + recentDays.size()
                                        + " Handelstage (< "
                                        + REQUIRED_DAYS
                                        + ") – wird ignoriert.");
                        continue;
                    }

                    BigDecimal basePrice = recentDays.get(recentDays.size() - 1).open;
                    if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) == 0)
                    {
                        ignoredCount++;
                        System.out.println("SKIP: cIsin " + isin + " hat keinen gültigen basePrice – wird ignoriert.");
                        continue;
                    }

                    List<PriceDay> usableDays = filterUsableDays(recentDays);
                    if (usableDays.size() < REQUIRED_DAYS)
                    {
                        ignoredCount++;
                        System.out.println("SKIP: cIsin "
                                        + isin
                                        + " hat nach NULL-Filterung nur "
                                        + usableDays.size()
                                        + " verwertbare Handelstage (< "
                                        + REQUIRED_DAYS
                                        + ") – wird ignoriert.");
                        continue;
                    }

                    BigDecimal result1Abs = calculateResult1Absolute(usableDays);
                    BigDecimal result2Abs = calculateResult2Absolute(usableDays);
                    BigDecimal result3Abs = calculateResult3Absolute(usableDays);

                    BigDecimal result1 = toPercent(result1Abs, basePrice);
                    BigDecimal result2 = toPercent(result2Abs, basePrice);
                    BigDecimal result3 = toPercent(result3Abs, basePrice);
                    String companyName = getCompanyName(companyStmt, isin);

                    writer.write(isin
                                    + ","
                                    + companyName
                                    + ","
                                    + format2(result1)
                                    + ","
                                    + format2(result2)
                                    + ","
                                    + format2(result3));
                    writer.newLine();
                    processedCount++;
                }
            }

            System.out.println("Analyse abgeschlossen: "
                            + processedCount
                            + " ISINs verarbeitet, "
                            + ignoredCount
                            + " ISINs ignoriert.");
        }
        catch (SQLException e)
        {
            System.err.println("Fehler bei der Strategy001-Datenbankanalyse: " + e.getMessage());
            e.printStackTrace();
        }
        catch (IOException e)
        {
            System.err.println("Fehler beim Schreiben der CSV-Datei '" + OUTPUT_FILE + "': " + e.getMessage());
            e.printStackTrace();
        }
    }


    private int getTotalDays(final PreparedStatement countStmt,
                             final String isin)
        throws SQLException
    {
        countStmt.setString(1, isin);
        try (ResultSet rs = countStmt.executeQuery())
        {
            if (rs.next())
            {
                return rs.getInt("cnt");
            }
        }
        return 0;
    }


    private String getCompanyName(final PreparedStatement companyStmt,
                                  final String isin)
        throws SQLException
    {
        companyStmt.setString(1, isin);
        try (ResultSet rs = companyStmt.executeQuery())
        {
            if (rs.next())
            {
                String longName = rs.getString("cLongName");
                return normalizeToTwoWords(longName);
            }
        }
        return "";
    }


    private String normalizeToTwoWords(final String text)
    {
        if (text == null)
        {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty())
        {
            return "";
        }

        String sanitized = trimmed.replace(',', ' ');
        String[] words = sanitized.trim().split("\\s+");
        if (words.length == 1)
        {
            return words[0];
        }
        return words[0] + " " + words[1];
    }


    private List<PriceDay> getRecentDays(final PreparedStatement recentStmt,
                                         final String isin)
        throws SQLException
    {
        List<PriceDay> days = new ArrayList<>();
        recentStmt.setString(1, isin);
        recentStmt.setInt(2, REQUIRED_DAYS);
        try (ResultSet rs = recentStmt.executeQuery())
        {
            while (rs.next())
            {
                Timestamp date = rs.getTimestamp("cDate");
                BigDecimal open = rs.getBigDecimal("cOpen");
                BigDecimal close = rs.getBigDecimal("cClose");
                BigDecimal high = rs.getBigDecimal("cHigh");
                BigDecimal low = rs.getBigDecimal("cLow");
                days.add(new PriceDay(date, open, close, high, low));
            }
        }
        return days;
    }


    private List<PriceDay> filterUsableDays(final List<PriceDay> days)
    {
        List<PriceDay> usable = new ArrayList<>(days.size());
        for (PriceDay day : days)
        {
            if (day.open != null && day.close != null && day.high != null && day.low != null)
            {
                usable.add(day);
            }
        }
        return usable;
    }


    private BigDecimal calculateResult1Absolute(final List<PriceDay> days)
    {
        BigDecimal sum = BigDecimal.ZERO;
        for (PriceDay day : days)
        {
            sum = sum.add(day.close.subtract(day.open));
        }
        return sum;
    }


    private BigDecimal calculateResult2Absolute(final List<PriceDay> days)
    {
        PriceDay newest = days.get(0);
        PriceDay oldest = days.get(days.size() - 1);
        return newest.close.subtract(oldest.close);
    }


    private BigDecimal calculateResult3Absolute(final List<PriceDay> days)
    {
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal two = BigDecimal.valueOf(2);
        for (PriceDay day : days)
        {
            BigDecimal buyPrice = day.open.add(day.low).divide(two, 10, RoundingMode.HALF_UP);
            BigDecimal sellPrice = day.close.add(day.high).divide(two, 10, RoundingMode.HALF_UP);
            sum = sum.add(sellPrice.subtract(buyPrice));
        }
        return sum;
    }


    private BigDecimal toPercent(final BigDecimal absoluteValue,
                                 final BigDecimal basePrice)
    {
        return absoluteValue.multiply(BigDecimal.valueOf(100))
                            .divide(basePrice, 10, RoundingMode.HALF_UP);
    }


    private String format2(final BigDecimal value)
    {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }


    private static class PriceDay
    {
        private final Timestamp date;
        private final BigDecimal open;
        private final BigDecimal close;
        private final BigDecimal high;
        private final BigDecimal low;


        PriceDay(final Timestamp date,
                 final BigDecimal open,
                 final BigDecimal close,
                 final BigDecimal high,
                 final BigDecimal low)
        {
            this.date = date;
            this.open = open;
            this.close = close;
            this.high = high;
            this.low = low;
        }
    }
}