package com.straube.jones.service;


import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.jsoup.Jsoup;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.straube.jones.dataprovider.eurorates.CurrencyDB;
import com.straube.jones.dataprovider.userprefs.UserPrefsRepo;
import com.straube.jones.db.DBConnection;
import com.straube.jones.db.DayCounter;
import com.straube.jones.dto.PriceEntry;
import com.straube.jones.dto.PriceTickerResponse;
import com.straube.jones.dto.TradegateIntradayDto;

/**
 * Service for retrieving stock price information from Tradegate.
 */
@Service
public class PriceTickerService
{
    private static final String TRADEGATE_FINANCE_URL = "https://www.tradegatebsx.com/refresh.php?isin=";
    private static final int TIMEOUT_MS = 10000;

    private static Set<String> blackListedIsins = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Guava Cache: Key = ISIN_slicedSeconds, Value = TradegateIntradayDto
    private static final Cache<String, TradegateIntradayDto> priceCache = CacheBuilder.newBuilder()
                                                                                      .expireAfterWrite(3,
                                                                                                        TimeUnit.DAYS)
                                                                                      .build();

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Retrieves current price ticker information for a stock by ISIN.
     * 
     * @param isin The ISIN of the stock
     * @return PriceTickerResponse containing all available price information
     */
    public static PriceTickerResponse getPriceByIsinFromCache(String isin)
    {
        if (isin == null || isin.trim().isEmpty())
        { throw new IllegalArgumentException("ISIN cannot be null or empty"); }

        TradegateIntradayDto cached = priceCache.getIfPresent(isin);
        
        if (cached != null)
        { return convertToResponse(cached); }

        return new PriceTickerResponse();
    }


    /**
     * Loads prices for all watched stocks mainly from Tradegate.
     */
    static long loopCounter = 0;
    @Scheduled(cron = "*/10 30-59 7 * * MON-FRI")
    @Scheduled(cron = "*/10 * 8-21 * * MON-FRI")
    @Scheduled(cron = "*/10 0 22 * * MON-FRI")
    public static void loadPrices()
    {
        if (loopCounter++ % 30 == 0) // try again after 5 minutes if there are blacklisted ISINs (e.g. due to temporary network issues)
        {
            blackListedIsins.clear();
            blackListedIsins.add("US6311011026"); // exclude NASDQ Index
        }
        LocalTime now = LocalTime.now(ZoneId.of("Europe/Berlin"));
        if (now.isBefore(LocalTime.of(7, 30)) || now.isAfter(LocalTime.of(22, 0)))
        { return; }

        Set<String> isins = new HashSet<>();
        File userPrefsRoot = new File(UserPrefsRepo.USER_PREFS_ROOT);
        File[] userDirs = userPrefsRoot.listFiles(File::isDirectory);
        if (userDirs != null)
        {
            for (File userDir : userDirs)
            {
                File watchlistFile = new File(userDir, "watchlist.json");
                if (watchlistFile.exists())
                {
                    try
                    {
                        JsonNode root = mapper.readTree(watchlistFile);
                        if (root.isArray())
                        {
                            for (JsonNode node : root)
                            {
                                if (node.has("isin"))
                                {
                                    isins.add(node.get("isin").asText());
                                }
                                else if (node.isTextual())
                                {
                                    isins.add(node.asText());
                                }
                            }
                        }
                        else if (root.has("isin"))
                        {
                            isins.add(root.get("isin").asText());
                        }
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }

        List<TradegateIntradayDto> dtos = new ArrayList<>();
        for (String isin : isins)
        {
            if (blackListedIsins.contains(isin))
            {
                continue;
            }
            try
            {
                TradegateIntradayDto dto = getPriceByIsinFromTradegate(isin.trim());
                if (dto != null)
                {
                    dto.setTimestamp(new java.sql.Timestamp(System.currentTimeMillis()));
                    dtos.add(dto);
                }
            }
            catch (IOException e)
            {
                System.err.println("Error fetching " + isin + ": " + e.getMessage());
                blackListedIsins.add(isin);

            }
        }

        String sql = "INSERT INTO tTradegateIntraday (cIsin, cSymbol, cBid, cAsk, cBidSize, cAskSize, cDelta, cStueck, cUmsatz, cAvg, cExecutions, cLast, cHigh, cLow, cClose, cTimestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getStocksConnection();
                        PreparedStatement pstmt = conn.prepareStatement(sql))
        {

            for (TradegateIntradayDto dto : dtos)
            {
                pstmt.setString(1, dto.getIsin());
                pstmt.setString(2, dto.getSymbol());
                pstmt.setBigDecimal(3, dto.getBid());
                pstmt.setBigDecimal(4, dto.getAsk());
                pstmt.setInt(5, dto.getBidSize());
                pstmt.setInt(6, dto.getAskSize());
                pstmt.setBigDecimal(7, dto.getDelta());
                pstmt.setLong(8, dto.getStueck());
                pstmt.setBigDecimal(9, dto.getUmsatz());
                pstmt.setBigDecimal(10, dto.getAvg());
                pstmt.setInt(11, dto.getExecutions());
                pstmt.setBigDecimal(12, dto.getLast());
                pstmt.setBigDecimal(13, dto.getHigh());
                pstmt.setBigDecimal(14, dto.getLow());
                pstmt.setBigDecimal(15, dto.getClose());
                pstmt.setTimestamp(16, dto.getTimestamp());

                pstmt.addBatch();
            }
            pstmt.executeBatch();
            conn.commit();
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }
    }


    /**
     * Reorganizes the {@code tTradegateIntraday} table by removing all records whose
     * {@code cTimestamp} falls on a calendar day that is strictly older than 14 days
     * (day-precise, evaluated in {@code Europe/Berlin} local time).
     *
     * <p>The cutoff is computed as midnight at the start of the local date 14 days ago,
     * so data from today back to and including 14 days ago is retained, while everything
     * before that boundary is deleted.
     *
     * <p>Runs automatically every working day at 06:30 local time via the
     * {@code @Scheduled} cron expression, before the regular price-fetch window opens.
     */
    @Scheduled(cron = "0 30 6 * * MON-FRI", zone = "Europe/Berlin")
    public static void cleanupIntraday()
    {
        ZoneId zone = ZoneId.of("Europe/Berlin");
        // Cutoff = start of local day 14 days ago  →  records before this timestamp are deleted
        java.time.LocalDate cutoffDate = java.time.LocalDate.now(zone).minusDays(14);
        java.sql.Timestamp cutoff = java.sql.Timestamp.from(
            cutoffDate.atStartOfDay(zone).toInstant());

        String sql = "DELETE FROM tTradegateIntraday WHERE cTimestamp < ?";
        try (Connection conn = DBConnection.getStocksConnection();
             PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setTimestamp(1, cutoff);
            int deleted = ps.executeUpdate();
            conn.commit();
            System.out.println("tTradegateIntraday cleanup: " + deleted
                               + " rows deleted (older than " + cutoffDate + ")");
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }
    }


    public static TradegateIntradayDto getPriceByIsinFromTradegate(String isin)
        throws IOException
    {
        TradegateIntradayDto dto = fetchPriceFromTradegate(isin);
        if (dto != null)
        {
            // ÄNDERUNG: Nutze nur die ISIN als Key.
            // Dadurch wird der alte Eintrag für diese ISIN sofort überschrieben (speichereffizient).
            priceCache.put(isin, dto);
        }
        else
        {
            System.err.println("No data for ISIN " + isin);
        }
        return dto;
    }


    /**
     * Fetches and parses price information from Tradegate.
     */
    private static TradegateIntradayDto fetchPriceFromTradegate(String isin)
        throws IOException
    {
        String url = TRADEGATE_FINANCE_URL + isin;

        String jsonResponse = Jsoup.connect(url)
                                   .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                   .timeout(TIMEOUT_MS)
                                   .ignoreContentType(true)
                                   .execute()
                                   .body();
        
        JsonNode node = mapper.readTree(jsonResponse);
        TradegateIntradayDto dto = new TradegateIntradayDto();
        if (node.has("bid")) dto.setBid(parseGermanDecimal(node.get("bid").asText()));
        if (node.has("ask")) dto.setAsk(parseGermanDecimal(node.get("ask").asText()));
        if (node.has("bidsize")) dto.setBidSize(node.get("bidsize").asInt());
        if (node.has("asksize")) dto.setAskSize(node.get("asksize").asInt());
        if (node.has("delta")) dto.setDelta(parseGermanDecimal(node.get("delta").asText()));
        if (node.has("stueck")) dto.setStueck(node.get("stueck").asLong());
        if (node.has("umsatz")) dto.setUmsatz(parseGermanDecimal(node.get("umsatz").asText()));
        if (node.has("avg")) dto.setAvg(parseGermanDecimal(node.get("avg").asText()));
        if (node.has("executions")) dto.setExecutions(node.get("executions").asInt());
        if (node.has("last")) dto.setLast(parseGermanDecimal(node.get("last").asText()));
        if (node.has("high")) dto.setHigh(parseGermanDecimal(node.get("high").asText()));
        if (node.has("low")) dto.setLow(parseGermanDecimal(node.get("low").asText()));
        if (node.has("close")) dto.setClose(parseGermanDecimal(node.get("close").asText()));
        
        dto.setIsin(isin);
        return dto;
    }


    private static PriceTickerResponse convertToResponse(TradegateIntradayDto dto)
    {
        PriceTickerResponse response = new PriceTickerResponse(dto.getIsin(), "EUR");
        List<PriceEntry> prices = new ArrayList<>();

        BigDecimal bid = dto.getBid()   ;
        BigDecimal ask = dto.getAsk();
        BigDecimal last = dto.getLast();
        BigDecimal high = dto.getHigh();
        BigDecimal low = dto.getLow();
        BigDecimal referencePrice = null;
        if (last != null)
        {
            try
            {
                referencePrice = BigDecimal.valueOf(Math.round(CurrencyDB.convertFromEuro("USD",
                                                                                          last.doubleValue(),
                                                                                          DayCounter.yesterday())
                                * 100.0) / 100.0);
            }
            catch (Exception e)
            {
                // ignore currency conversion errors
            }
        }

        PriceEntry price = new PriceEntry(PriceEntry.PriceType.REGULAR,
                                          bid,
                                          ask,
                                          high,
                                          low,
                                          last,
                                          referencePrice,
                                          Instant.now().toString(),
                                          "tradegate");
        prices.add(price);
        response.setPrices(prices);
        return response;
    }


    private static BigDecimal parseGermanDecimal(String text)
    {
        if (text == null || text.trim().isEmpty())
        { return null; }
        if (text.contains(".") && text.contains(","))
        {
            text = text.replace("+", "").replace(".", "").replace(",", ".").replace(" ", "");
        }
        else if (text.contains(","))
        {
            text = text.replace("+", "").replace(",", ".").replace(" ", "");
        }
        else
        {
            text = text.replace("+", "").replace(" ", "");
        }

        try
        {
            return new BigDecimal(text);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}
