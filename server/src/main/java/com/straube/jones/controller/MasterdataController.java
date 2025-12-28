package com.straube.jones.controller;


import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.straube.jones.dataprovider.yahoo.SymbolResolver;
import com.straube.jones.dataprovider.yahoo.YahooPriceDownloader;
import com.straube.jones.dataprovider.yahoo.YahooPriceImporter;
import com.straube.jones.db.DBConnection;
import com.straube.jones.dto.CompanyRequest;
import com.straube.jones.dto.CompanyResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/masterdata")
@Tag(name = "Masterdata API", description = "API for managing master data of companies and stocks. Provides functionality to create and update company information based on ISIN.")
public class MasterdataController
{

    @Operation(summary = "Create or Update Company Master Data", description = "**Use Case:** Ensures that a company exists in the master data and its price data is initialized. **Logic:** Checks if the ISIN exists in `tSymbols`. If not, resolves the symbol via Yahoo Finance, downloads historical prices, and imports them. Finally, updates or creates the company record in `tCompany` based on the downloaded metadata. **Returns:** The updated company master data.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Company data successfully created or updated", content = @Content(schema = @Schema(implementation = CompanyResponse.class))),
                           @ApiResponse(responseCode = "500", description = "Internal server error, e.g., if symbol resolution fails or database error occurs")})
    @PostMapping("/company")
    public CompanyResponse company(@RequestBody
    CompanyRequest request)
        throws Exception
    {
        String isin = request.getIsin();
        String symbol = null;

        try (Connection conn = DBConnection.getStocksConnection())
        {
            // 1. Check tSymbols
            // Using cIndex as per text instructions. If DB has cImdex, this will fail and need adjustment.
            String selectSymbolSql = "SELECT cSymbol FROM tSymbols WHERE cIsin = ? AND cIndex = 1";
            try (PreparedStatement ps = conn.prepareStatement(selectSymbolSql))
            {
                ps.setString(1, isin);
                try (ResultSet rs = ps.executeQuery())
                {
                    if (rs.next())
                    {
                        symbol = rs.getString("cSymbol");
                    }
                }
            }

            if (symbol == null)
            {
                // Not found
                List<String> symbols = SymbolResolver.getCodeForISIN(isin);
                if (symbols.isEmpty())
                { throw new RuntimeException("No symbol found for ISIN: " + isin); }

                // Insert into tSymbols
                String insertSql = "INSERT INTO tSymbols (cSymbol, cIsin, cIndex) VALUES (?, ?, ?)";
                try (PreparedStatement psInsert = conn.prepareStatement(insertSql))
                {
                    int idx = 1;
                    for (String s : symbols)
                    {
                        psInsert.setString(1, s);
                        psInsert.setString(2, isin);
                        psInsert.setInt(3, idx++ );
                        try
                        {
                            psInsert.executeUpdate();
                        }
                        catch (SQLException e)
                        {
                            // Ignore if exists, or log
                            System.err.println("Failed to insert symbol " + s + ": " + e.getMessage());
                        }
                    }
                }

                // Download and Import for each symbol
                for (String s : symbols)
                {
                    String path = System.getProperty("user.home") + "/Software/data/yahoo/prices/" + s;
                    // Ensure directory exists (YahooPriceDownloader does this, but good to be sure)

                    YahooPriceDownloader.fetchPrices(365, path, s, isin);

                    YahooPriceImporter importer = new YahooPriceImporter();
                    importer.uploadPriceData(path);
                }

                // Use the first symbol (index 1)
                symbol = symbols.get(0);
            }
            else
            {
                // If symbol was found, we still need to ensure tCompany is updated as per instructions
                // "Implementiere dann ein Funktion..."
                // We call uploadPriceData which handles the update from JSON.
                // Note: If the file doesn't exist (because we didn't download), this won't do anything.
                String path = System.getProperty("user.home") + "/Software/data/yahoo/prices/" + symbol;
                YahooPriceImporter importer = new YahooPriceImporter();
                importer.uploadPriceData(path);
            }

            conn.commit(); // Commit any changes made by importer if it didn't commit itself (it does commit itself)

            // Return the current record from tCompany
            return getCompany(conn, symbol);
        }
    }


    private CompanyResponse getCompany(Connection conn, String symbol)
        throws SQLException
    {
        String sql = "SELECT * FROM tCompany WHERE cSymbol = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, symbol);
            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    CompanyResponse response = new CompanyResponse();
                    response.setcId(rs.getString("cId"));
                    response.setcSymbol(rs.getString("cSymbol"));
                    response.setcIsin(rs.getString("cIsin"));
                    response.setcShortName(rs.getString("cShortName"));
                    response.setcLongName(rs.getString("cLongName"));
                    response.setcCurrency(rs.getString("cCurrency"));
                    response.setcInstrumentType(rs.getString("cInstrumentType"));
                    response.setcFirstTradeDate(rs.getDate("cFirstTradeDate"));
                    response.setcExchangeName(rs.getString("cExchangeName"));
                    response.setcFullExchangeName(rs.getString("cFullExchangeName"));
                    response.setcExchangeTimezoneName(rs.getString("cExchangeTimezoneName"));
                    response.setcTimezone(rs.getString("cTimezone"));
                    response.setcHasPrePostMarketData(rs.getBoolean("cHasPrePostMarketData"));
                    response.setcPriceHint(rs.getInt("cPriceHint"));
                    response.setcDataGranularity(rs.getString("cDataGranularity"));
                    response.setcCreated(rs.getTimestamp("cCreated"));
                    response.setcUpdated(rs.getTimestamp("cUpdated"));
                    return response;
                }
            }
        }
        return null;
    }
}
