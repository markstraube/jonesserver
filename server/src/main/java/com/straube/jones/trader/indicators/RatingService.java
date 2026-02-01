package com.straube.jones.trader.indicators;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.straube.jones.dataprovider.yahoo.SymbolResolver;
import com.straube.jones.db.DayCounter;
import com.straube.jones.trader.dto.RatingDto;

@Service
public class RatingService
{
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public RatingService(JdbcTemplate jdbcTemplate)
    {
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }


    public List<RatingDto> getRatings(List<String> codes, Long startTime, Long endTime)
    {
        if (codes == null || codes.isEmpty())
        { return Collections.emptyList(); }

        List<String> symbols = new ArrayList<>();
        codes.forEach(code -> {
            symbols.add(SymbolResolver.resolveCode(code));
        });
        long startDay = (startTime != null) ? DayCounter.get(startTime) : 0; // 0 is 1.1.2000
        long endDay = (endTime != null) ? DayCounter.get(endTime) : DayCounter.now();

        String sql = "SELECT cSymbol, cShort, cMid, cLong, cDayCounter FROM tRatings WHERE cSymbol IN (:symbols) AND cDayCounter >= :startDay AND cDayCounter <= :endDay ORDER BY cSymbol ASC, cDayCounter DESC";

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("symbols", symbols);
        parameters.addValue("startDay", startDay);
        parameters.addValue("endDay", endDay);

        return namedParameterJdbcTemplate.query(sql, parameters, (rs, rowNum) -> {
            RatingDto dto = new RatingDto();
            dto.setSymbol(rs.getString("cSymbol"));
            dto.setShortTerm(rs.getString("cShort"));
            dto.setMidTerm(rs.getString("cMid"));
            dto.setLongTerm(rs.getString("cLong"));
            dto.setDate(DayCounter.toTimestamp(rs.getLong("cDayCounter")));
            return dto;
        });
    }


    public void saveRating(RatingDto rating)
    {
        String sql = "INSERT INTO tRatings (cSymbol, cShort, cMid, cLong, cDayCounter) VALUES (:symbol, :shortTerm, :midTerm, :longTerm, :date)";
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("symbol", rating.getSymbol());
        parameters.addValue("shortTerm", rating.getShortTerm());
        parameters.addValue("midTerm", rating.getMidTerm());
        parameters.addValue("longTerm", rating.getLongTerm());
        parameters.addValue("date", DayCounter.get(rating.getDate()));

        namedParameterJdbcTemplate.update(sql, parameters);
    }


    public void deleteRatingsForSymbol(String symbol)
    {
        String sql = "DELETE FROM tRatings WHERE cSymbol = :symbol";
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("symbol", symbol);
        namedParameterJdbcTemplate.update(sql, parameters);
    }


    public void deleteRatingsForDate(long date, String symbol)
    {
        String sql = "DELETE FROM tRatings WHERE cDayCounter = :date" + " AND cSymbol = :symbol";
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("date", date);
        parameters.addValue("symbol", symbol);
        namedParameterJdbcTemplate.update(sql, parameters);
    }


    public void deleteRatingsForDate(long date)
    {
        String sql = "DELETE FROM tRatings WHERE cDayCounter = :date";
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("date", date);
        namedParameterJdbcTemplate.update(sql, parameters);
    }


    public void saveRatingsBatch(List<RatingDto> ratings)
    {
        if (ratings == null || ratings.isEmpty())
        { return; }
        String sql = "INSERT INTO tRatings (cSymbol, cShort, cMid, cLong, cDayCounter) VALUES (:symbol, :shortTerm, :midTerm, :longTerm, :date)";
        MapSqlParameterSource[] batch = ratings.stream().map(rating -> {
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("symbol", rating.getSymbol());
            parameters.addValue("shortTerm", rating.getShortTerm());
            parameters.addValue("midTerm", rating.getMidTerm());
            parameters.addValue("longTerm", rating.getLongTerm());
            parameters.addValue("date", DayCounter.get(rating.getDate()));
            return parameters;
        }).toArray(MapSqlParameterSource[]::new);

        namedParameterJdbcTemplate.batchUpdate(sql, batch);
    }


    /**
     * Get the maximum cDayCounter value for each symbol in tRatings table
     * 
     * @return Map with symbol as key and max cDayCounter as value
     */
    public Map<String, Long> getMaxDayCounterPerSymbol()
    {
        String sql = "SELECT cSymbol, MAX(cDayCounter) as maxDay FROM tRatings GROUP BY cSymbol";
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
