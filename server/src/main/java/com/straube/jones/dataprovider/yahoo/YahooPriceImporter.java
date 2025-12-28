package com.straube.jones.dataprovider.yahoo;


import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.straube.jones.db.DBConnection;
import com.straube.jones.db.DayCounter;

public class YahooPriceImporter
{
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void uploadPriceData(String folder)
    {
        File yahooDir = new File(folder);
        if (!yahooDir.exists() || !yahooDir.isDirectory())
        {
            System.err.println("Yahoo directory not found: " + yahooDir.getAbsolutePath());
            return;
        }
        File[] jsonFiles = yahooDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json")
                        && !name.equals("YahooCodes.json"));
        if (jsonFiles == null)
        {
            System.err.println("No JSON files found in " + yahooDir.getAbsolutePath());
            return;
        }

        for (File jsonFile : jsonFiles)
        {
            processFile(jsonFile);
        }
    }


    private void processFile(File jsonFile)
    {
        System.out.println("Processing file: " + jsonFile.getName());
        String filename = jsonFile.getName();
        // Extract ISIN from filename (assuming format ISIN_SYMBOL.json)
        String isin = filename.split("_")[0];

        try (Connection conn = DBConnection.getStocksConnection())
        {
            conn.setAutoCommit(false);

            try
            {
                JsonNode rootNode = objectMapper.readTree(jsonFile);

                JsonNode meta;
                JsonNode dataNode;

                JsonNode chartNode = rootNode.path("chart");
                JsonNode resultNode = chartNode.path("result");

                if (!resultNode.isMissingNode() && resultNode.isArray() && resultNode.size() > 0)
                {
                    // New structure
                    dataNode = resultNode.get(0);
                    meta = dataNode.path("meta");
                }
                else
                {
                    // Old structure
                    meta = rootNode.path("meta");
                    dataNode = rootNode.path("data");
                }

                if (meta.isMissingNode())
                {
                    System.err.println("Meta data missing in file: " + filename);
                    return;
                }

                // 2. Company-Daten (tCompany)
                upsertCompany(conn, meta, isin);

                // 3. Price-Daten (tPriceData)
                int importedRecords = importPriceData(conn, dataNode, meta, isin);

                conn.commit();
                System.out.println("Successfully processed " + filename
                                + ". Imported "
                                + importedRecords
                                + " price records.");

            }
            catch (Exception e)
            {
                conn.rollback();
                System.err.println("Error processing file " + filename + ": " + e.getMessage());
                e.printStackTrace();
            }

        }
        catch (SQLException e)
        {
            System.err.println("Database connection error: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void upsertCompany(Connection conn, JsonNode meta, String isin)
        throws SQLException
    {
        String symbol = meta.path("symbol").asText();

        // Check if company exists
        String selectSql = "SELECT cId FROM tCompany WHERE cSymbol = ?";
        String existingId = null;
        try (PreparedStatement ps = conn.prepareStatement(selectSql))
        {
            ps.setString(1, symbol);
            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    existingId = rs.getString("cId");
                }
            }
        }

        if (existingId != null)
        {
            // UPDATE
            String updateSql = "UPDATE tCompany SET cIsin=?, cShortName=?, cLongName=?, cCurrency=?, cInstrumentType=?, "
                            + "cFirstTradeDate=?, cExchangeName=?, cFullExchangeName=?, cExchangeTimezoneName=?, cTimezone=?, "
                            + "cHasPrePostMarketData=?, cPriceHint=?, cDataGranularity=?, cUpdated=CURRENT_TIMESTAMP "
                            + "WHERE cId=?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql))
            {
                fillCompanyStatement(ps, meta, isin);
                ps.setString(14, existingId);
                ps.executeUpdate();
            }
        }
        else
        {
            // INSERT
            String insertSql = "INSERT INTO tCompany (cIsin, cShortName, cLongName, cCurrency, cInstrumentType, "
                            + "cFirstTradeDate, cExchangeName, cFullExchangeName, cExchangeTimezoneName, cTimezone, "
                            + "cHasPrePostMarketData, cPriceHint, cDataGranularity, cId, cSymbol) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql))
            {
                fillCompanyStatement(ps, meta, isin);
                ps.setString(14, UUID.randomUUID().toString());
                ps.setString(15, symbol);
                ps.executeUpdate();
            }
        }
    }


    private void fillCompanyStatement(PreparedStatement ps, JsonNode meta, String isin)
        throws SQLException
    {
        ps.setString(1, isin);
        ps.setString(2, meta.path("shortName").asText(""));
        ps.setString(3, meta.path("longName").asText(""));
        ps.setString(4, meta.path("currency").asText(""));
        ps.setString(5, meta.path("instrumentType").asText(""));

        long firstTradeDateSec = meta.path("firstTradeDate").asLong(0);
        if (firstTradeDateSec > 0)
        {
            ps.setDate(6, new java.sql.Date(firstTradeDateSec * 1000));
        }
        else
        {
            ps.setDate(6, null);
        }

        ps.setString(7, meta.path("exchangeName").asText(""));
        ps.setString(8, meta.path("fullExchangeName").asText(""));
        ps.setString(9, meta.path("exchangeTimezoneName").asText(""));
        ps.setString(10, meta.path("timezone").asText(""));
        ps.setBoolean(11, meta.path("hasPrePostMarketData").asBoolean(false));
        ps.setInt(12, meta.path("priceHint").asInt(0));
        ps.setString(13, meta.path("dataGranularity").asText(""));
    }


    private int importPriceData(Connection conn, JsonNode data, JsonNode meta, String isin)
        throws SQLException
    {
        String symbol = meta.path("symbol").asText();
        String currency = meta.path("currency").asText();
        int count = 0;

        String deleteSql = "DELETE FROM tPriceData WHERE cSymbol = ? AND cDayCounter = ?";
        String insertSql = "INSERT INTO tPriceData (cId, cIsin, cSymbol, cDate, cOpen, cClose, cAdjClose, cHigh, cLow, cVolume, cCurrency, cDayCounter) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement psDelete = conn.prepareStatement(deleteSql);
                        PreparedStatement psInsert = conn.prepareStatement(insertSql))
        {
            if (data.isArray())
            {
                for (JsonNode record : data)
                {
                    String dateStr = record.path("date").asText();
                    if (dateStr == null || dateStr.isEmpty())
                        continue;

                    LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
                    long dayCounter = DayCounter.get(date);

                    // Idempotency: Delete existing
                    psDelete.setString(1, symbol);
                    psDelete.setLong(2, dayCounter);
                    psDelete.executeUpdate();

                    // Insert new
                    psInsert.setString(1, UUID.randomUUID().toString());
                    psInsert.setString(2, isin);
                    psInsert.setString(3, symbol);
                    psInsert.setTimestamp(4, Timestamp.valueOf(date.atStartOfDay()));
                    psInsert.setDouble(5, record.path("open").asDouble());
                    psInsert.setDouble(6, record.path("close").asDouble());
                    psInsert.setDouble(7, record.path("adjClose").asDouble());
                    psInsert.setDouble(8, record.path("high").asDouble());
                    psInsert.setDouble(9, record.path("low").asDouble());
                    psInsert.setLong(10, record.path("volume").asLong());
                    psInsert.setString(11, currency);
                    psInsert.setLong(12, dayCounter);

                    psInsert.executeUpdate();
                    count++ ;
                }
            }
            else if (data.has("timestamp") && data.has("indicators"))
            {
                JsonNode timestampNode = data.path("timestamp");
                JsonNode indicatorsNode = data.path("indicators");
                JsonNode quoteNode = indicatorsNode.path("quote").get(0);
                JsonNode adjCloseNode = indicatorsNode.path("adjclose").get(0);

                if (timestampNode.isArray() && quoteNode != null)
                {
                    JsonNode openArr = quoteNode.path("open");
                    JsonNode closeArr = quoteNode.path("close");
                    JsonNode highArr = quoteNode.path("high");
                    JsonNode lowArr = quoteNode.path("low");
                    JsonNode volumeArr = quoteNode.path("volume");
                    JsonNode adjCloseArr = (adjCloseNode != null) ? adjCloseNode.path("adjclose") : null;

                    String timezone = meta.path("timezone").asText("UTC");
                    ZoneId zoneId;
                    try
                    {
                        zoneId = ZoneId.of(timezone);
                    }
                    catch (Exception e)
                    {
                        zoneId = ZoneId.of("UTC");
                    }

                    for (int i = 0; i < timestampNode.size(); i++ )
                    {
                        long ts = timestampNode.get(i).asLong();
                        LocalDate date = Instant.ofEpochSecond(ts).atZone(zoneId).toLocalDate();
                        long dayCounter = DayCounter.get(date);

                        // Idempotency: Delete existing
                        psDelete.setString(1, symbol);
                        psDelete.setLong(2, dayCounter);
                        psDelete.executeUpdate();

                        // Insert new
                        psInsert.setString(1, UUID.randomUUID().toString());
                        psInsert.setString(2, isin);
                        psInsert.setString(3, symbol);
                        psInsert.setTimestamp(4, Timestamp.valueOf(date.atStartOfDay()));

                        psInsert.setDouble(5, getValue(openArr, i));
                        psInsert.setDouble(6, getValue(closeArr, i));

                        double adjClose = (adjCloseArr != null) ? getValue(adjCloseArr, i)
                                        : getValue(closeArr, i);
                        psInsert.setDouble(7, adjClose);

                        psInsert.setDouble(8, getValue(highArr, i));
                        psInsert.setDouble(9, getValue(lowArr, i));
                        psInsert.setLong(10, getLongValue(volumeArr, i));
                        psInsert.setString(11, currency);
                        psInsert.setLong(12, dayCounter);

                        psInsert.executeUpdate();
                        count++ ;
                    }
                }
            }
        }
        return count;
    }


    private double getValue(JsonNode arr, int index)
    {
        if (arr == null || !arr.has(index) || arr.get(index).isNull())
            return 0.0;
        return arr.get(index).asDouble();
    }


    private long getLongValue(JsonNode arr, int index)
    {
        if (arr == null || !arr.has(index) || arr.get(index).isNull())
            return 0L;
        return arr.get(index).asLong();
    }


    public static void main(String[] args)
    {
        String rootFolder = "./data/yahoo/daily";
        if (args.length > 0)
        {
            rootFolder = args[0];
        }

        System.out.println("Starting Yahoo Price Import from: " + rootFolder);
        YahooPriceImporter importer = new YahooPriceImporter();
        importer.uploadPriceData(rootFolder);
        System.out.println("Import finished.");
    }
}
