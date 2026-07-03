-- Snapshot history for market-data-scraper. Apply once on HOME-40:
--   mysql -u <user> -p < persistence-schema.sql
-- InnoDB profits from the 8GB buffer pool already configured on this host.

CREATE DATABASE IF NOT EXISTS marketdata
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE marketdata;

CREATE TABLE IF NOT EXISTS market_snapshot (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ticker                  VARCHAR(12)  NOT NULL,
    snapshot_ts             TIMESTAMP(6) NOT NULL,
    market_state            VARCHAR(10) NULL,

    -- quote scalars
    price                   DOUBLE NULL,
    change_pct              DOUBLE NULL,
    prev_close              DOUBLE NULL,
    open_price              DOUBLE NULL,
    high                    DOUBLE NULL,
    low                     DOUBLE NULL,
    volume                  BIGINT NULL,

    -- options scalars
    put_call_ratio_flow     DOUBLE NULL,
    iv                      DOUBLE NULL,
    hv                      DOUBLE NULL,
    iv_rank                 DOUBLE NULL,
    max_pain                DOUBLE NULL,
    oi_call_total           BIGINT NULL,
    oi_put_total            BIGINT NULL,
    oi_put_call_ratio       DOUBLE NULL,
    ua_call_volume          BIGINT NULL,
    ua_put_volume           BIGINT NULL,
    ua_call_notional_usd    DOUBLE NULL,
    ua_put_notional_usd     DOUBLE NULL,

    -- short data scalars
    short_float             DOUBLE NULL,
    days_to_cover           DOUBLE NULL,
    inst_own                DOUBLE NULL,

    -- full-fidelity payloads
    oi_profile_json         JSON NULL,
    unusual_activity_json   JSON NULL,
    news_json               JSON NULL,

    PRIMARY KEY (id),
    KEY idx_ticker_ts (ticker, snapshot_ts)
) ENGINE=InnoDB;
