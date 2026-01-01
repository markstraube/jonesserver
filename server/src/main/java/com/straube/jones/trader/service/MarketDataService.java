package com.straube.jones.trader.service;


import com.straube.jones.db.DayCounter;
import com.straube.jones.trader.dto.DailyPrice;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class MarketDataService
{

    private final JdbcTemplate jdbcTemplate;

    public MarketDataService(JdbcTemplate jdbcTemplate)
    {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DailyPrice> getMarketData(String symbol)
    {
    return  getMarketData(symbol, DayCounter.now());
    }

    public List<DailyPrice> getMarketData(String symbol, long fromDayCounterDesc)
    {
        String sql = "SELECT cDate, cOpen, cHigh, cLow, cClose, cAdjClose, cVolume FROM tPriceData WHERE cSymbol = ? and cDayCounter <= ? ORDER BY cDayCounter DESC";
        return jdbcTemplate.query(sql, new RowMapper<DailyPrice>()
        {
            @Override
            public DailyPrice mapRow(ResultSet rs, int rowNum)
                throws SQLException
            {
                DailyPrice dp = new DailyPrice();
                long dateLong = rs.getDate("cDate").getTime();
                LocalDate date = Instant.ofEpochMilli(dateLong).atZone(ZoneId.systemDefault()).toLocalDate();
                dp.setDate(date);

                dp.setOpen(rs.getDouble("cOpen"));
                dp.setHigh(rs.getDouble("cHigh"));
                dp.setLow(rs.getDouble("cLow"));
                dp.setClose(rs.getDouble("cClose"));
                dp.setAdjClose(rs.getDouble("cAdjClose"));
                dp.setVolume(rs.getLong("cVolume"));
                return dp;
            }
        }, symbol, fromDayCounterDesc);
    }
}
