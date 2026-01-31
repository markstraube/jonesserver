package com.straube.jones.accounting.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.straube.jones.accounting.dto.BudgetDto;
import com.straube.jones.accounting.dto.PerformanceDto;
import com.straube.jones.accounting.dto.TransactionDto;
import com.straube.jones.dataprovider.userprefs.UserPrefsRepo;
import com.straube.jones.db.DBConnection;
import com.straube.jones.db.DayCounter;
import com.straube.jones.model.User;

@Repository
public class AccountingRepository {
    public static void main(String[] args) throws Exception {
        String username = "mark";
        String jsonFilePath = "C:\\opt\\tomcat\\data\\userprefs\\3\\portfolio.json";
        String json = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(jsonFilePath)),
                java.nio.charset.StandardCharsets.UTF_8);
        org.json.JSONArray arr = new org.json.JSONArray(json);
        AccountingRepository repo = new AccountingRepository();
        com.straube.jones.model.User user = new com.straube.jones.model.User();
        user.setUsername(username);
        try (java.sql.Connection conn = com.straube.jones.db.DBConnection.getStocksConnection()) {
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                String id = obj.optString("id", null);
                String isin = obj.optString("isin", null);
                String symbol = obj.optString("symbol", null);
                String stockName = obj.optString("stockName", null);
                Integer quantity = obj.has("quantity") ? obj.optInt("quantity") : null;
                Double purchasePrice = obj.has("purchasePrice") ? obj.optDouble("purchasePrice") : null;
                String status = obj.optString("status", null);
                String createdAt = obj.optString("createdAt", null);
                String updatedAt = obj.optString("updatedAt", null);
                String saleDate = obj.optString("saleDate", null);
                Double salePrice = obj.has("salePrice") ? obj.optDouble("salePrice") : null;
                String userName = username;

                String sql = "INSERT INTO tPortfolio (cId, cUser, cIsin, cSymbol, cStockName, cQuantity, cPurchasePrice, cState, cCreatedAt, cPurchaseDate, cSaleDate, cSalePrice, cUpdatedAt) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, id);
                    stmt.setString(2, userName);
                    stmt.setString(3, isin);
                    stmt.setString(4, symbol);
                    stmt.setString(5, stockName);
                    if (quantity != null) stmt.setInt(6, quantity); else stmt.setNull(6, java.sql.Types.INTEGER);
                    if (purchasePrice != null) stmt.setDouble(7, purchasePrice); else stmt.setNull(7, java.sql.Types.DOUBLE);
                    stmt.setString(8, status);
                    // createdAt und purchaseDate
                    if (createdAt != null && !createdAt.isEmpty()) stmt.setTimestamp(9, java.sql.Timestamp.valueOf(createdAt.replace("T", " ").replace("Z", ""))); else stmt.setNull(9, java.sql.Types.TIMESTAMP);
                    if (obj.has("purchaseDate")) {
                        String pd = obj.optString("purchaseDate");
                        if (pd != null && !pd.isEmpty()) {
                            if (pd.length() == 10) { // yyyy-MM-dd
                                stmt.setTimestamp(10, java.sql.Timestamp.valueOf(pd + " 00:00:00"));
                            } else {
                                stmt.setTimestamp(10, java.sql.Timestamp.valueOf(pd.replace("T", " ").replace("Z", "")));
                            }
                        } else {
                            stmt.setNull(10, java.sql.Types.TIMESTAMP);
                        }
                    } else {
                        stmt.setNull(10, java.sql.Types.TIMESTAMP);
                    }
                    // saleDate
                    if (saleDate != null && !saleDate.isEmpty()) {
                        if (saleDate.length() == 10) {
                            stmt.setTimestamp(11, java.sql.Timestamp.valueOf(saleDate + " 00:00:00"));
                        } else {
                            stmt.setTimestamp(11, java.sql.Timestamp.valueOf(saleDate.replace("T", " ").replace("Z", "")));
                        }
                    } else {
                        stmt.setNull(11, java.sql.Types.TIMESTAMP);
                    }
                    if (salePrice != null) stmt.setDouble(12, salePrice); else stmt.setNull(12, java.sql.Types.DOUBLE);
                    if (updatedAt != null && !updatedAt.isEmpty()) stmt.setTimestamp(13, java.sql.Timestamp.valueOf(updatedAt.replace("T", " ").replace("Z", ""))); else stmt.setNull(13, java.sql.Types.TIMESTAMP);
                    stmt.executeUpdate();
                }
                System.out.println("Imported position: " + symbol + " (" + status + ") for user " + userName);
            }
            conn.commit();
        }
    }

    // --- Performance / Budget ---

    public Optional<BudgetDto> getLatestBudget(User user) {
        String sql = "SELECT cBudget, cPortfolio, cCash, cDate FROM tPerformance WHERE cUser = ? ORDER BY cDate DESC LIMIT 1";
        try (Connection conn = DBConnection.getStocksConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getUsername());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new BudgetDto(
                            rs.getDouble("cBudget"),
                            rs.getDouble("cPortfolio"),
                            rs.getDouble("cCash"),
                            rs.getTimestamp("cDate").toInstant().toString()));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching latest budget", e);
        }
        return Optional.empty();
    }

    public void savePerformance(User user, Double budget, Double portfolio, Double cash, long dayCounter,
            boolean keepMe, Timestamp timestamp) {
        String sql = "INSERT INTO tPerformance (cUser, cDate, cBudget, cPortfolio, cCash, cDayCounter, cKeepMe) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getStocksConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getUsername());
            stmt.setTimestamp(2, timestamp);
            setDoubleOrNull(stmt, 3, budget);
            setDoubleOrNull(stmt, 4, portfolio);
            setDoubleOrNull(stmt, 5, cash);
            stmt.setLong(6, dayCounter);
            stmt.setBoolean(7, keepMe);
            stmt.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Error saving performance", e);
        }
    }

    public List<PerformanceDto> getPerformance(User user, long from, long to) {
        String sql = "SELECT cDate, cBudget, cPortfolio, cCash, cDayCounter FROM tPerformance WHERE cUser = ? AND cKeepMe = 1 AND cDayCounter BETWEEN ? AND ?";
        List<PerformanceDto> list = new ArrayList<>();
        try (Connection conn = DBConnection.getStocksConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getUsername());
            stmt.setLong(2, from);
            stmt.setLong(3, to);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new PerformanceDto(
                            rs.getTimestamp("cDate").toInstant().toString(),
                            rs.getDouble("cBudget"),
                            rs.getDouble("cPortfolio"),
                            rs.getDouble("cCash"),
                            rs.getLong("cDayCounter")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching performance history", e);
        }
        return list;
    }

    public List<PerformanceDto> getPerformanceLastDays(User user, int days) {
        // "Performance der letzten 5 Werktage... nicht nur cKeepMe=true"
        // Actually prompt says: "Hole alle tPerformance Einträge der letzten 5 Tage (nicht nur cKeepMe=true)"
        // But how many entries? Every 30 seconds? That's huge. 
        // Maybe it implies "all snapshots stored"?
        // No, "Scheduler... In tPerformance speichern".
        // If I query last 5 days without keepMe=1, I get thousands of rows. 
        // "GET /api/accounting/performance/week ... Response: Gleiche Struktur wie /performance"
        // Maybe the requirement means "For the scope of the week view, show details".
        // I will implement as requested: filter by day range, ignore keepMe.

        long today = DayCounter.now(); // Assuming DayCounter.now() exists and returns int/long
        long from = DayCounter.before(days);

        String sql = "SELECT cDate, cBudget, cPortfolio, cCash, cDayCounter FROM tPerformance WHERE cUser = ? AND cDayCounter >= ? ORDER BY cId ASC";
        List<PerformanceDto> list = new ArrayList<>();
        try (Connection conn = DBConnection.getStocksConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getUsername());
            stmt.setLong(2, from);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new PerformanceDto(
                            rs.getTimestamp("cDate").toInstant().toString(),
                            rs.getDouble("cBudget"),
                            rs.getDouble("cPortfolio"),
                            rs.getDouble("cCash"),
                            rs.getLong("cDayCounter")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching weekly performance", e);
        }
        return list;
    }

    public void cleanupPerformance(User user, LocalDateTime threshold) {
        String sql = "DELETE FROM tPerformance WHERE cUser = ? AND cDate < ? AND cKeepMe = 0";
        try (Connection conn = DBConnection.getStocksConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getUsername());
            stmt.setTimestamp(2, Timestamp.valueOf(threshold));
            stmt.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Error cleaning up performance data", e);
        }
    }

    // --- Portfolio ---

    public void addPortfolioPosition(User user, TransactionDto tx) {
        String sql = "INSERT INTO tPortfolio (cId, cUser, cIsin, cSymbol, cStockName, cQuantity, cPurchasePrice, cState, cCreatedAt, cPurchaseDate) VALUES (?, ?, ?, ?, ?, ?, ?, 'active', NOW(), NOW())";
        try (Connection conn = DBConnection.getStocksConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, UUID.randomUUID().toString()); // Generate ID
            stmt.setString(2, user.getUsername());
            stmt.setString(3, tx.getIsin());
            stmt.setString(4, tx.getSymbol());
            stmt.setString(5, tx.getStockName());
            stmt.setInt(6, tx.getQuantity());
            setDoubleOrNull(stmt, 7, tx.getPrice());
            stmt.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Error adding portfolio position", e);
        }
    }

    public void closePortfolioPosition(String positionId, Double salePrice) {
        String sql = "UPDATE tPortfolio SET cState = 'closed', cSalePrice = ?, cSaleDate = NOW(), cUpdatedAt = NOW() WHERE cId = ?";
        try (Connection conn = DBConnection.getStocksConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            setDoubleOrNull(stmt, 1, salePrice);
            stmt.setString(2, positionId);
            int updated = stmt.executeUpdate();
            if (updated == 0) {
                throw new RuntimeException("Position not found or update failed: " + positionId);
            }
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Error closing portfolio position", e);
        }
    }

    public void updatePortfolioPositionQuantity(String positionId, int newQuantity) {
        String sql = "UPDATE tPortfolio SET cQuantity = ?, cUpdatedAt = NOW() WHERE cId = ?";
        try (Connection conn = DBConnection.getStocksConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, newQuantity);
            stmt.setString(2, positionId);
            stmt.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating position quantity", e);
        }
    }

    public void createPartialSalePosition(User user, String originalId, int quantity, Double purchasePrice,
            String isin, String symbol, String stockName) {
        // Create new position for the REMAINING part?
        // Wait, "Bei Teil-Verkauf: Erstelle neuen Eintrag mit verbleibender Menge, setze Original auf cState='closed'"
        // So the NEW entry is the one that stays ACTIVE with remaining quantity.
        // The ORIGINAL one is closed with the sold quantity?
        // Actually, prompt says: "Bei Teil-Verkauf: Erstelle neuen Eintrag mit verbleibender Menge, setze Original auf cState='closed', cPartialSale=1"
        // This implies the original record tracks the sale (or at least the fact it was closed/partially sold), and the NEW record tracks the remaining stock.
        // But parameters for "Close" usually include sale price.
        // If I close the original, does it represent the sold part or the whole history?
        // Usually: Original = Sold Part (or mixed).
        // Let's stick to prompt: "Original -> closed". "New -> remaining quantity".
        // The "Original" should probably have the sale info if it represents the sale?
        // "Bei Teil-Verkauf: ... setze Original auf cState='closed', cSaleDate, cSalePrice" (This is in "Gesamt-Verkauf" line, but applies?)
        // Let's assume the Original becomes the "Sold" record (with the quantity sold?)
        // Prompt says "Bei Teil-Verkauf: Erstelle neuen Eintrag mit verbleibender Menge".
        // So:
        // 1. New Entry: Quantity = Remaining (OldQty - SoldQty). State = Active.
        // 2. Old Entry: State = Closed. Quantity = ? (Original Qty? Or Sold Qty?)
        // If I keep Original Qty in Old Entry, and mark it closed, it looks like I sold everything.
        // Ideally I should update Old Entry Quantity to `SoldQty` and mark closed.
        // But prompt logic: "Erstelle neuen Eintrag mit verbleibender Menge".
        // implies the old entry covers the sold part (or the history).
        // I will implement: 
        // - Insert New with Remaining Qty.
        // - Update Old with Sold Qty (implicit or explicit?) and Close.

        // I will follow the logic in Service. Here just provide methods.
        // already have addPortfolioPosition, updatePortfolioPositionQuantity.
        // I need a generic insertWithParentRef?
        // Reuse addPortfolioPosition but maybe I need to set parentRef.
        // I'll add a method valid for partial copy.

        String sql = "INSERT INTO tPortfolio (cId, cUser, cIsin, cSymbol, cStockName, cQuantity, cPurchasePrice, cState, cCreatedAt, cPurchaseDate, cParentRef) VALUES (?, ?, ?, ?, ?, ?, ?, 'active', NOW(), NOW(), ?)";
        try (Connection conn = DBConnection.getStocksConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, UUID.randomUUID().toString());
            stmt.setString(2, user.getUsername());
            stmt.setString(3, isin);
            stmt.setString(4, symbol);
            stmt.setString(5, stockName);
            stmt.setInt(6, quantity); // Remaining qty
            setDoubleOrNull(stmt, 7, purchasePrice); // Original purchase price
            stmt.setString(8, originalId);
            stmt.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Error creating partial position", e);
        }
    }

    public void markPartialSaleOriginal(String positionId, Double salePrice, int soldQuantity) {
        // The prompt says "setze Original auf cState='closed', cPartialSale=1".
        // It doesn't explicitly say "change quantity to soldQuantity".
        // However, for accounting correctness, the closed record should reflect what was sold.
        // If I sold 10 out of 100.
        // New record: 90 active.
        // Old record: 100 closed? -> Then I sold 100. Wrong.
        // Old record: 10 closed? -> Then I sold 10. Correct.
        // So I should validly update quantity to soldQuantity.
        String sql = "UPDATE tPortfolio SET cState = 'closed', cPartialSale = 1, cSalePrice = ?, cSaleDate = NOW(), cQuantity = ?, cUpdatedAt = NOW() WHERE cId = ?";
        try (Connection conn = DBConnection.getStocksConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            setDoubleOrNull(stmt, 1, salePrice);
            stmt.setInt(2, soldQuantity);
            stmt.setString(3, positionId);
            stmt.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Error marking partial sale", e);
        }
    }

    public List<TransactionDto> getActivePositions(User user) {

        try {
            String jsonPrefs = UserPrefsRepo.getPrefs(user, "portfolio");
            return TransactionDto.fromJson(jsonPrefs);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
        // String sql = "SELECT cId, cIsin, cSymbol, cStockName, cQuantity, cPurchasePrice FROM tPortfolio WHERE cUser = ? AND cState = 'active'";
        // List<TransactionDto> list = new ArrayList<>();
        // try (Connection conn = DBConnection.getStocksConnection();
        //         PreparedStatement stmt = conn.prepareStatement(sql)) {
        //     stmt.setString(1, user.getUsername());
        //     try (ResultSet rs = stmt.executeQuery()) {
        //         while (rs.next()) {
        //             TransactionDto dto = new TransactionDto();
        //             dto.setPositionId(rs.getString("cId"));
        //             dto.setIsin(rs.getString("cIsin"));
        //             dto.setSymbol(rs.getString("cSymbol"));
        //             dto.setStockName(rs.getString("cStockName"));
        //             dto.setQuantity(rs.getInt("cQuantity"));
        //             dto.setPrice(rs.getDouble("cPurchasePrice"));
        //             list.add(dto);
        //         }
        //     }
        // } catch (SQLException e) {
        //     throw new RuntimeException("Error fetching portfolio", e);
        // }
        // return list;
    }

    public List<TransactionDto> getActivePositionsDB(User user) {
        String sql = "SELECT cId, cIsin, cSymbol, cStockName, cQuantity, cPurchasePrice FROM tPortfolio WHERE cUser = ? AND cState = 'active'";
        List<TransactionDto> list = new ArrayList<>();
        try (Connection conn = DBConnection.getStocksConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getUsername());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    TransactionDto dto = new TransactionDto();
                    dto.setPositionId(rs.getString("cId"));
                    dto.setIsin(rs.getString("cIsin"));
                    dto.setSymbol(rs.getString("cSymbol"));
                    dto.setStockName(rs.getString("cStockName"));
                    dto.setQuantity(rs.getInt("cQuantity"));
                    dto.setPrice(rs.getDouble("cPurchasePrice"));
                    list.add(dto);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching portfolio", e);
        }
        return list;
    }

    public Optional<TransactionDto> getPosition(String id) {
        String sql = "SELECT cId, cUser, cIsin, cSymbol, cStockName, cQuantity, cPurchasePrice, cState FROM tPortfolio WHERE cId = ?";
        try (Connection conn = DBConnection.getStocksConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    TransactionDto dto = new TransactionDto();
                    dto.setPositionId(rs.getString("cId"));
                    // dto.setUserId(rs.getString("cUser")); // needed for security check?
                    dto.setIsin(rs.getString("cIsin"));
                    dto.setSymbol(rs.getString("cSymbol"));
                    dto.setStockName(rs.getString("cStockName"));
                    dto.setQuantity(rs.getInt("cQuantity"));
                    dto.setPrice(rs.getDouble("cPurchasePrice"));
                    // dto.setState(rs.getString("cState"));
                    return Optional.of(dto);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching position " + id, e);
        }
        return Optional.empty();
    }

    public String getPositionUser(String id) {
        String sql = "SELECT cUser FROM tPortfolio WHERE cId = ?";
        try (Connection conn = DBConnection.getStocksConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next())
                    return rs.getString("cUser");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private void setDoubleOrNull(PreparedStatement stmt, int index, Double value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, java.sql.Types.DOUBLE);
        } else {
            stmt.setDouble(index, value);
        }
    }
}
