# Options collector architecture (v2.6)

## Independent collectors

- `OptionChainDiscoveryCollector`: discovers IBKR expiries/strikes and updates the catalog.
- `OptionChainDiscoveryJob`: refreshes the watchlist independently from snapshots.
- `OptionContractCatalog`: stores the latest discovered chain and detects additions/removals.
- `OptionCandidateSelector`: ranks only IBKR-listed strikes; 2.5 strikes remain eligible.
- `OptionHistoricalTradeCollector`: retrieves historical option trades.
- `OptionHistoricalQuoteCollector`: retrieves historical BID/ASK price states.
- `OptionHistoricalFlowCollector`: coordinates both collectors under one shared pacing budget.

## API

- `GET /api/v1/options/{ticker}/chain`
- `GET /api/v1/options/{ticker}/chain?refresh=true`
- `GET /api/v1/options/{ticker}/chain/changes`

## Important IBKR request rule

Historical BID_ASK requests use `ignoreSize=true`. Size-only quote churn is filtered by IBKR before the 1000-tick response limit is consumed.

## Current persistence boundary

The chain catalog/change history is process-local in v2.6. Market snapshots continue to use the existing Book/cache/persistence mechanisms. Durable database persistence for chain versions, raw historical trades/quotes and daily OI is intentionally a separate migration because the deployment currently uses `spring.jpa.hibernate.ddl-auto=none` and therefore requires an explicit schema migration.
