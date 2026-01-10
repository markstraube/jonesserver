package com.straube.jones.service;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.straube.jones.dataprovider.eurorates.CurrencyDB;
import com.straube.jones.dataprovider.yahoo.SymbolResolver;
import com.straube.jones.db.DayCounter;
import com.straube.jones.trader.dto.IndicatorDto;

/**
 * Service-Klasse für den Zugriff auf technische Indikatoren aus der tIndicators-Tabelle.
 * Bietet Methoden zum Abrufen historischer Indikator-Daten für verschiedene Aktien und Zeiträume.
 */
@Service
public class IndicatorService
{

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public IndicatorService(JdbcTemplate jdbcTemplate)
    {
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }


    /**
     * Ruft technische Indikatoren für eine Liste von Aktiencodes ab.
     * 
     * @param codes Liste von Aktiencodes (z.B. Yahoo Finance Symbole oder interne Codes)
     * @param startTime Startzeitpunkt als Unix-Timestamp in Millisekunden (optional, default: 1.1.2000)
     * @param endTime Endzeitpunkt als Unix-Timestamp in Millisekunden (optional, default: heute)
     * @return Liste von IndicatorDto-Objekten, sortiert nach Symbol (ASC) und Datum (DESC)
     */
    public List<IndicatorDto> getIndicatorsFromDB(List<String> codes,
                                                  Long startTime,
                                                  Long endTime,
                                                  boolean convertToEuro)
    {
        if (codes == null || codes.isEmpty())
        { return Collections.emptyList(); }

        // Konvertiere Codes in Symbole
        List<String> symbols = new ArrayList<>();
        codes.forEach(code -> symbols.add(SymbolResolver.resolveCode(code)));

        long startDay = (startTime != null) ? DayCounter.get(startTime) : 0; // 0 ist 1.1.2000
        long endDay = (endTime != null) ? DayCounter.get(endTime) : DayCounter.now();

        String sql = "SELECT cSymbol, cDateLong, cDayCounter, cCurrency," 
                        + "cBB15low, cBB15mid, cBB15high, "
                        + "cRSI, cVolume, "
                        + "cMACDvalue, cMACDsignal, "
                        + "cRSI30_5, cRSI30_10, cRSI30_20, cRSI30_30, cRSI30probability, "
                        + "cSMA5, cSMA10, cSMA20, cSMA30, "
                        + "cEMA5, cEMA10, cEMA20, cEMA30, "
                        + "cSupport, cResistance "
                        + "FROM tIndicators "
                        + "WHERE cSymbol IN (:symbols) "
                        + "AND cDayCounter >= :startDay "
                        + "AND cDayCounter <= :endDay "
                        + "ORDER BY cSymbol ASC, cDayCounter DESC";

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("symbols", symbols);
        parameters.addValue("startDay", startDay);
        parameters.addValue("endDay", endDay);

        return namedParameterJdbcTemplate.query(sql, parameters, (rs, rowNum) -> {
            IndicatorDto dto = new IndicatorDto();
            dto.setSymbol(rs.getString("cSymbol"));
            dto.setDate(rs.getLong("cDateLong"));

            String currency = rs.getString("cCurrency");

            // Bollinger Bands
            dto.setBb15low(CurrencyDB.getAsEuroOrOriginal(currency,
                                                          rs.getDouble("cBB15low"),
                                                          DayCounter.yesterday(),
                                                          convertToEuro));
            if (rs.wasNull())
                dto.setBb15low(null);

            dto.setBb15mid(CurrencyDB.getAsEuroOrOriginal(currency,
                                                          rs.getDouble("cBB15mid"),
                                                          DayCounter.yesterday(),
                                                          convertToEuro));
            if (rs.wasNull())
                dto.setBb15mid(null);

            dto.setBb15high(CurrencyDB.getAsEuroOrOriginal(currency,
                                                           rs.getDouble("cBB15high"),
                                                           DayCounter.yesterday(),
                                                           convertToEuro));
            if (rs.wasNull())
                dto.setBb15high(null);

            // RSI
            dto.setRsi(rs.getDouble("cRSI"));
            if (rs.wasNull())
                dto.setRsi(null);

            // Volume
            dto.setVolume(rs.getDouble("cVolume"));
            if (rs.wasNull())
                dto.setVolume(null);

            // MACD
            dto.setMacdValue(rs.getDouble("cMACDvalue"));
            if (rs.wasNull())
                dto.setMacdValue(null);

            dto.setMacdSignal(rs.getDouble("cMACDsignal"));
            if (rs.wasNull())
                dto.setMacdSignal(null);

            // RSI30 Probabilities
            dto.setRsi30Days5(rs.getDouble("cRSI30_5"));
            if (rs.wasNull())
                dto.setRsi30Days5(null);

            dto.setRsi30Days10(rs.getDouble("cRSI30_10"));
            if (rs.wasNull())
                dto.setRsi30Days10(null);

            dto.setRsi30Days20(rs.getDouble("cRSI30_20"));
            if (rs.wasNull())
                dto.setRsi30Days20(null);

            dto.setRsi30Days30(rs.getDouble("cRSI30_30"));
            if (rs.wasNull())
                dto.setRsi30Days30(null);

            dto.setRsi30probability(rs.getDouble("cRSI30probability"));
            if (rs.wasNull())
                dto.setRsi30probability(null);

            // Simple Moving Averages
            dto.setSma5(CurrencyDB.getAsEuroOrOriginal(currency,
                                                       rs.getDouble("cSMA5"),
                                                       DayCounter.yesterday(),
                                                       convertToEuro));
            if (rs.wasNull())
                dto.setSma5(null);

            dto.setSma10(CurrencyDB.getAsEuroOrOriginal(currency,
                                                        rs.getDouble("cSMA10"),
                                                        DayCounter.yesterday(),
                                                        convertToEuro));
            if (rs.wasNull())
                dto.setSma10(null);

            dto.setSma20(CurrencyDB.getAsEuroOrOriginal(currency,
                                                        rs.getDouble("cSMA20"),
                                                        DayCounter.yesterday(),
                                                        convertToEuro));
            if (rs.wasNull())
                dto.setSma20(null);

            dto.setSma30(CurrencyDB.getAsEuroOrOriginal(currency,
                                                        rs.getDouble("cSMA30"),
                                                        DayCounter.yesterday(),
                                                        convertToEuro));
            if (rs.wasNull())
                dto.setSma30(null);

            // Exponential Moving Averages
            dto.setEma5(CurrencyDB.getAsEuroOrOriginal(currency,
                                                       rs.getDouble("cEMA5"),
                                                       DayCounter.yesterday(),
                                                       convertToEuro));
            if (rs.wasNull())
                dto.setEma5(null);

            dto.setEma10(CurrencyDB.getAsEuroOrOriginal(currency,
                                                        rs.getDouble("cEMA10"),
                                                        DayCounter.yesterday(),
                                                        convertToEuro));
            if (rs.wasNull())
                dto.setEma10(null);

            dto.setEma20(CurrencyDB.getAsEuroOrOriginal(currency,
                                                        rs.getDouble("cEMA20"),
                                                        DayCounter.yesterday(),
                                                        convertToEuro));
            if (rs.wasNull())
                dto.setEma20(null);

            dto.setEma30(CurrencyDB.getAsEuroOrOriginal(currency,
                                                        rs.getDouble("cEMA30"),
                                                        DayCounter.yesterday(),
                                                        convertToEuro));
            if (rs.wasNull())
                dto.setEma30(null);

            // Support and Resistance
            dto.setSupport(CurrencyDB.getAsEuroOrOriginal(currency,
                                                          rs.getDouble("cSupport"),
                                                          DayCounter.yesterday(),
                                                          convertToEuro));

            if (rs.wasNull())
                dto.setSupport(null);

            dto.setResistance(CurrencyDB.getAsEuroOrOriginal(currency,
                                                             rs.getDouble("cResistance"),
                                                             DayCounter.yesterday(),
                                                             convertToEuro));
            if (rs.wasNull())
                dto.setResistance(null);

            return dto;
        });
    }


    /**
     * Speichert oder aktualisiert eine Liste von Indikatoren in der Datenbank.
     * Verwendet Batch-Update für bessere Performance.
     * 
     * @param indicators Liste der zu speichernden Indikatoren
     */
    public void upsertIndicators(List<IndicatorDto> indicators)
    {
        if (indicators == null || indicators.isEmpty())
        { return; }

        String sql = "INSERT INTO tIndicators (" + "cSymbol, cDateLong, cCurrency, cDayCounter, "
                        + "cBB15low, cBB15mid, cBB15high, "
                        + "cRSI, cVolume, "
                        + "cMACDvalue, cMACDsignal, "
                        + "cRSI30_5, cRSI30_10, cRSI30_20, cRSI30_30, cRSI30probability, "
                        + "cSMA5, cSMA10, cSMA20, cSMA30, "
                        + "cEMA5, cEMA10, cEMA20, cEMA30, "
                        + "cSupport, cResistance"
                        + ") VALUES ("
                        + ":symbol, :dateLong, :dayCounter, "
                        + ":bb15low, :bb15mid, :bb15high, "
                        + ":rsi, :volume, "
                        + ":macdValue, :macdSignal, "
                        + ":rsi30_5, :rsi30_10, :rsi30_20, :rsi30_30, :rsi30probability, "
                        + ":sma5, :sma10, :sma20, :sma30, "
                        + ":ema5, :ema10, :ema20, :ema30, "
                        + ":support, :resistance"
                        + ") ON DUPLICATE KEY UPDATE "
                        + "cBB15low = VALUES(cBB15low), cBB15mid = VALUES(cBB15mid), cBB15high = VALUES(cBB15high), "
                        + "cRSI = VALUES(cRSI), cVolume = VALUES(cVolume), "
                        + "cMACDvalue = VALUES(cMACDvalue), cMACDsignal = VALUES(cMACDsignal), "
                        + "cRSI30_5 = VALUES(cRSI30_5), cRSI30_10 = VALUES(cRSI30_10), cRSI30_20 = VALUES(cRSI30_20), cRSI30_30 = VALUES(cRSI30_30), cRSI30probability = VALUES(cRSI30probability), "
                        + "cSMA5 = VALUES(cSMA5), cSMA10 = VALUES(cSMA10), cSMA20 = VALUES(cSMA20), cSMA30 = VALUES(cSMA30), "
                        + "cEMA5 = VALUES(cEMA5), cEMA10 = VALUES(cEMA10), cEMA20 = VALUES(cEMA20), cEMA30 = VALUES(cEMA30), "
                        + "cSupport = VALUES(cSupport), cResistance = VALUES(cResistance)";

        MapSqlParameterSource[] batch = indicators.stream().map(dto -> {
            MapSqlParameterSource params = new MapSqlParameterSource();
            long dayCounter = DayCounter.get(dto.getDate());

            params.addValue("symbol", dto.getSymbol());
            params.addValue("dateLong", dto.getDate());
            params.addValue("currency", dto.getCurrency());
            params.addValue("dayCounter", dayCounter);

            params.addValue("bb15low", dto.getBb15low());
            params.addValue("bb15mid", dto.getBb15mid());
            params.addValue("bb15high", dto.getBb15high());

            params.addValue("rsi", dto.getRsi());
            params.addValue("volume", dto.getVolume());

            params.addValue("macdValue", dto.getMacdValue());
            params.addValue("macdSignal", dto.getMacdSignal());

            params.addValue("rsi30_5", dto.getRsi30Days5());
            params.addValue("rsi30_10", dto.getRsi30Days10());
            params.addValue("rsi30_20", dto.getRsi30Days20());
            params.addValue("rsi30_30", dto.getRsi30Days30());
            params.addValue("rsi30probability", dto.getRsi30probability());

            params.addValue("sma5", dto.getSma5());
            params.addValue("sma10", dto.getSma10());
            params.addValue("sma20", dto.getSma20());
            params.addValue("sma30", dto.getSma30());

            params.addValue("ema5", dto.getEma5());
            params.addValue("ema10", dto.getEma10());
            params.addValue("ema20", dto.getEma20());
            params.addValue("ema30", dto.getEma30());

            params.addValue("support", dto.getSupport());
            params.addValue("resistance", dto.getResistance());

            return params;
        }).toArray(MapSqlParameterSource[]::new);

        namedParameterJdbcTemplate.batchUpdate(sql, batch);
    }


    /**
     * Get the maximum cDayCounter value for each symbol in tIndicators table
     * @return Map with symbol as key and max cDayCounter as value
     */
    public Map<String, Long> getMaxDayCounterPerSymbol()
    {
        String sql = "SELECT cSymbol, MAX(cDayCounter) as maxDay FROM tIndicators GROUP BY cSymbol";
        List<Map<String, Object>> results = namedParameterJdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("symbol", rs.getString("cSymbol"));
            map.put("maxDay", rs.getLong("maxDay"));
            return map;
        });

        Map<String, Long> maxDayMap = new HashMap<>();
        for (Map<String, Object> result : results)
        {
            maxDayMap.put((String)result.get("symbol"), (Long)result.get("maxDay"));
        }
        return maxDayMap;
    }
}
