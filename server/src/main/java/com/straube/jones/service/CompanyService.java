package com.straube.jones.service;


import com.straube.jones.model.Company;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;

@Service
public class CompanyService
{

    private final JdbcTemplate jdbcTemplate;

    public CompanyService(JdbcTemplate jdbcTemplate)
    {
        this.jdbcTemplate = jdbcTemplate;
    }


    public String getCompanyName(String symbol)
    {
        try
        {
            String sql = "SELECT cLongName FROM tCompany WHERE cSymbol = ?";
            return jdbcTemplate.queryForObject(sql, String.class, symbol);
        }
        catch (Exception e)
        {
            return "Unknown Company";
        }
    }


    public Company getCompany(String symbol)
    {
        try
        {
            String sql = "SELECT * FROM tCompany WHERE cSymbol = ?";
            return jdbcTemplate.queryForObject(sql, new CompanyRowMapper(), symbol);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    public java.util.List<String> getAllSymbols() {
        String sql = "SELECT cSymbol FROM tCompany ORDER BY cSymbol";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    public java.util.List<Company> getAllCompanies() {
        String sql = "SELECT * FROM tCompany ORDER BY cSymbol";
        return jdbcTemplate.query(sql, new CompanyRowMapper());
    }

    private static class CompanyRowMapper
        implements
        RowMapper<Company>
    {
        @Override
        public Company mapRow(ResultSet rs, int rowNum)
            throws SQLException
        {
            Company company = new Company();
            company.setId(rs.getString("cId"));
            company.setSymbol(rs.getString("cSymbol"));
            company.setIsin(rs.getString("cIsin"));
            company.setShortName(rs.getString("cShortName"));
            company.setLongName(rs.getString("cLongName"));
            company.setCurrency(rs.getString("cCurrency"));
            company.setInstrumentType(rs.getString("cInstrumentType"));

            java.sql.Date firstTradeDate = rs.getDate("cFirstTradeDate");
            if (firstTradeDate != null)
            {
                company.setFirstTradeDate(firstTradeDate.toLocalDate());
            }

            company.setExchangeName(rs.getString("cExchangeName"));
            company.setFullExchangeName(rs.getString("cFullExchangeName"));
            company.setExchangeTimezoneName(rs.getString("cExchangeTimezoneName"));
            company.setTimezone(rs.getString("cTimezone"));
            company.setHasPrePostMarketData(rs.getBoolean("cHasPrePostMarketData"));
            company.setPriceHint(rs.getObject("cPriceHint", Integer.class));
            company.setDataGranularity(rs.getString("cDataGranularity"));

            java.sql.Timestamp created = rs.getTimestamp("cCreated");
            if (created != null)
            {
                company.setCreated(created.toLocalDateTime());
            }

            java.sql.Timestamp updated = rs.getTimestamp("cUpdated");
            if (updated != null)
            {
                company.setUpdated(updated.toLocalDateTime());
            }

            return company;
        }
    }
}
