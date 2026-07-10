-- Migration for tables created from the initial persistence-schema.sql:
-- adds the field-granular data-quality record of the Book architecture.
--   mysql -u <user> -p marketdata < migration-2026-07-10-data-quality.sql
--
-- data_quality_json holds per-section ages and staleness at persist time
-- (quote / optionsMetrics / auction / uaScan, each with ageSeconds,
-- changedAgeSeconds, stale, invalidated, plus connectionLost and IBKR
-- marketDataType). Null for tickers served by scrapers instead of the Book.

ALTER TABLE market_snapshot
    ADD COLUMN data_quality_json JSON NULL AFTER auction_json;
