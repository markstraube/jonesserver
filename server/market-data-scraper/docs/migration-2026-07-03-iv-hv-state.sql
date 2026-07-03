-- Migration for tables created from the initial persistence-schema.sql:
-- adds implied/historical volatility and the market-state tag.
--   mysql -u <user> -p marketdata < migration-2026-07-03-iv-hv-state.sql

ALTER TABLE market_snapshot
    ADD COLUMN market_state VARCHAR(10) NULL AFTER snapshot_ts,
    ADD COLUMN iv DOUBLE NULL AFTER put_call_ratio_flow,
    ADD COLUMN hv DOUBLE NULL AFTER iv;

-- Cleanup: remove the holiday phantom rows of July 3 so Monday's first snapshot
-- does not compute deltas against fabricated stale-tick data.
DELETE FROM market_snapshot WHERE DATE(snapshot_ts) = '2026-07-03';
