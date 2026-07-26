# market-data-scraper options v2.6

This release separates option-chain discovery, contract catalog/change detection, strike candidate selection, historical trade collection and historical quote collection.

Key fix: all historical `BID_ASK` calls now set `ignoreSize=true`, so IBKR does not spend the 1000-tick page on size-only changes.

Run locally:

```bash
./mvnw test
```

Live verification:

```text
GET /api/v1/options/MU/chain?refresh=true
GET /api/v1/options/MU/chain/changes
GET /api/v1/options/MU?refresh=true
```
