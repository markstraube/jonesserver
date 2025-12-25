package com.straube.jones.trader.service;

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
public class MarketDataService {

    private final JdbcTemplate jdbcTemplate;

    public MarketDataService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DailyPrice> getMarketData(String symbol) {
        String sql = "SELECT cDateLong, cOpen, cHigh, cLow, cClose, cAdjClose, cVolume FROM tPriceData WHERE cSymbol = ? ORDER BY cDateLong ASC";
        
        return jdbcTemplate.query(sql, new RowMapper<DailyPrice>() {
            @Override
            public DailyPrice mapRow(ResultSet rs, int rowNum) throws SQLException {
                DailyPrice dp = new DailyPrice();
                long dateLong = rs.getLong("cDateLong");
                // Assuming cDateLong is milliseconds or seconds. Usually Java uses milliseconds.
                // If it's YYYYMMDD format as long, I need to handle that.
                // Given the name "DateLong", it's ambiguous. 
                // But typically in stock data it might be epoch millis or YYYYMMDD.
                // Let's assume epoch millis for now, or check if I can find more info.
                // If it's YYYYMMDD:
                // LocalDate date = LocalDate.parse(String.valueOf(dateLong), DateTimeFormatter.BASIC_ISO_DATE);
                
                // Let's try to be safe. If it's small (e.g. 20231010), it's YYYYMMDD. If it's huge, it's millis.
                // But for a clean implementation, I'll assume epoch millis as it's common for "Long".
                // Wait, if it is just "Date" it might be YYYYMMDD.
                // Let's assume it is a timestamp in milliseconds.
                
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
        }, symbol);
    }
}
