-- Create User Table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create Permission Table
CREATE TABLE IF NOT EXISTS permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL
);

-- Create User-Permission Junction Table
CREATE TABLE IF NOT EXISTS user_permissions (
    user_id BIGINT,
    permission_id BIGINT,
    PRIMARY KEY (user_id, permission_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

-- Create User Preferences Table
CREATE TABLE IF NOT EXISTS user_preferences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    preferences TEXT,
    user_id BIGINT UNIQUE,
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Insert Permissions
INSERT IGNORE INTO permissions (name) VALUES 
('PORTFOLIO_READ'), ('PORTFOLIO_CREATE'), ('PORTFOLIO_UPDATE'), ('PORTFOLIO_DELETE'),
('WATCHLIST_READ'), ('WATCHLIST_CREATE'), ('WATCHLIST_UPDATE'), ('WATCHLIST_DELETE'),
('BOARD_READ'), ('BOARD_CREATE'), ('BOARD_UPDATE'), ('BOARD_DELETE'),
('PORTFOLIO_EXECUTE_ADD'), ('WATCHLIST_EXECUTE_ADD'),
('ADMIN');

-- Note: Admin user creation is handled by DataInitializer on startup if not exists.

-- ---------------------------------------------------------------------------
-- Indexes for tTradegateIntraday
-- ---------------------------------------------------------------------------
-- Q1: Intraday data query  → WHERE cIsin = ? AND cTimestamp >= ? AND cTimestamp < ?  ORDER BY cTimestamp ASC
-- Q2: Last-day resolution  → SELECT MAX(cTimestamp) … WHERE cIsin = ?
-- Both are fully covered by the composite index (cIsin, cTimestamp):
--   • cIsin equality reduces the scan to one ISIN's rows
--   • cTimestamp range/sort is handled without a filesort
CREATE INDEX IF NOT EXISTS tTradegateIntraday_cIsin_cTimestamp_IDX
    ON tTradegateIntraday (cIsin, cTimestamp);

-- Q3: Cleanup DELETE → WHERE cTimestamp < ?
-- No cIsin filter → the composite index above cannot be used (leftmost-prefix rule).
-- A standalone cTimestamp index allows the DELETE to do an index-range scan
-- instead of a full table scan, which is critical given the high insert frequency.
CREATE INDEX IF NOT EXISTS tTradegateIntraday_cTimestamp_IDX
    ON tTradegateIntraday (cTimestamp);

-- ---------------------------------------------------------------------------
-- Indexes for tPriceData
-- ---------------------------------------------------------------------------
-- Q1: Point/range lookup  → WHERE cSymbol = ? AND cDayCounter = ?  (MarketDataService)
-- Q2: Range + sort        → WHERE cSymbol = ? AND cDayCounter <= ? ORDER BY cDayCounter DESC
-- Q3: Aggregation         → SELECT cSymbol, MAX(cDayCounter) FROM tPriceData GROUP BY cSymbol
-- Q4: Distinct            → SELECT DISTINCT cSymbol FROM tPriceData
-- All four are covered by the composite (cSymbol, cDayCounter) index.
CREATE INDEX IF NOT EXISTS tPriceData_cSymbol_cDayCounter_IDX
    ON tPriceData (cSymbol, cDayCounter);

-- Q5: ISIN lookup         → WHERE cIsin IN (...)  (PricePointLoader)
CREATE INDEX IF NOT EXISTS tPriceData_cIsin_IDX
    ON tPriceData (cIsin);

-- ---------------------------------------------------------------------------
-- Indexes for tRatings
-- ---------------------------------------------------------------------------
-- Q1: Symbol+day range    → WHERE cSymbol IN (...) AND cDayCounter BETWEEN ? AND ?  (RatingService)
-- Q2: Aggregation         → SELECT cSymbol, MAX(cDayCounter) FROM tRatings GROUP BY cSymbol
-- Both are covered by the composite (cSymbol, cDayCounter) index.
CREATE INDEX IF NOT EXISTS tRatings_cSymbol_cDayCounter_IDX
    ON tRatings (cSymbol, cDayCounter);

-- Q3: Global max          → SELECT MAX(cDayCounter) FROM tRatings  (SwingTradeQueryService)
-- Q4: Day filter          → WHERE cDayCounter = ? AND (cShort='BUY' OR ...)  (SwingTradeQueryService)
-- The standalone cDayCounter index serves both (composite above can't help when cSymbol is absent).
CREATE INDEX IF NOT EXISTS tRatings_cDayCounter_IDX
    ON tRatings (cDayCounter);
