# Market Data Scraper

Intraday US-equity market data service for active trading. Primary source is Interactive
Brokers (TWS API via IB Gateway) consumed as what it actually is — an asynchronous tick
stream — cached in an in-memory last-value **Book** and served as JSON snapshots over REST.
Yahoo/Finviz/Barchart scrapers remain as fallback and for data IBKR does not provide
(short interest, news, max pain). Snapshot history is persisted to MySQL.

## Architecture: the Book

```
IBKR Gateway ──ticks──▶ SubscriptionManager ──▶ MarketDataBook (in-memory last-value cache)
                            │                        ▲              │
                            │ (permanent streams,    │ (writes)     │ (synchronous reads only)
                            │  auto-resubscribe)     │              ▼
                        UA/OI Scanner (polling) ─────┘         REST / snapshot assembly
                                                               MySQL persistence
```

- **MarketDataBook** (`book/`) — single source of truth for current market state. One
  `TickerBook` per symbol: quote fields, options metrics (IV/HV/put-call ratio), auction/NOII
  fields, and the UA/OI scan results. **Every field carries two timestamps**:
  `lastChangedAt` (value changed) and `lastSeenAt` (any tick arrived, even same-value).
  Old changed + fresh seen = calm market; both old = dead feed. Reads never block on IBKR.
  No history in memory — history lives in MySQL.
- **SubscriptionManager** (`book/`) — owns the permanent streaming subscriptions: one
  `reqMktData(…, "100,104,106,225", snapshot=false)` line per watchlist symbol plus SPY,
  established on connect, re-established with fresh reqIds after the (routine) nightly IBC
  Gateway restart, staggered 100ms for pacing. `snapshot=true` is banned everywhere: it is
  answered from the Gateway cache (stale LAST in fast markets) and cannot carry generic
  ticks. **Liveness watchdog**: SPY is the heartbeat anchor — if SPY's volume is fresh but
  another ticker's has been silent beyond the threshold during REGULAR, that stream is dead,
  not calm: it is cancelled and resubscribed (`AUTO_RESUBSCRIBE` log event). Errors 100
  (pacing violation → backoff retry) and 101 (market-data line budget → no retry) are
  handled and reported separately.
- **UA/OI scanner** (`OptionActivityService`) — stays polling (hundreds of option contracts
  cannot be streamed within the line budget). A background loop scans the watchlist every
  `book.scan-interval-ms` and writes results into the Book, timestamped; snapshots read them
  from there and label their age honestly via `dataQuality`.
- **Read path** — REST endpoints read the Book synchronously. Non-watchlist tickers keep the
  scraper fallbacks (Yahoo quote, Barchart options) and scan on demand.
- **dataQuality** (additive JSON block, Book symbols only) — per section
  (quote/optionsMetrics/auction/uaScan): `ageSeconds`, `changedAgeSeconds`, `stale`
  (market-state-aware: silence outside REGULAR is stillness, not failure), `invalidated`,
  plus `connectionLost` and IBKR `marketDataType` (1 realtime / 2 frozen / 3 delayed /
  4 delayed-frozen).

Non-goals, by design: no message queue, no event sourcing, no tick history, no websocket
push — an in-process cache plus lifecycle management.


## Interpretation safety added in v10.1

- `aggressorProfile.positionInference` is now suppressed (`UNKNOWN`) unless the flow profile
  is at least `MEDIUM` quality. The JSON also carries `positionInferenceConfidence` and
  `positionInferenceReason`, so an LLM cannot mistake a tiny classified sample for a firm
  opening/closing conclusion.
- `derived.dealerGamma` now carries `signModel`, `observedDealerPositions=false`, and a
  coverage-based `confidence`. The GEX numbers remain model estimates, never observed dealer
  inventory.
- Build verification in this delivery was limited to static source checks because the
  execution environment could not download Maven; run `./mvnw test` in a network-enabled
  development environment before deployment.

## Requirements

- Java 21+, Maven 3.8+
- IB Gateway on `ibkr.host:ibkr.port` (default 127.0.0.1:4001) with API enabled;
  `lib/TwsApi.jar` installed locally (system-scope dependency)
- MySQL for snapshot history (optional — `persistence.enabled=false` degrades to no history)

## Build & Run

```bash
mvn clean package
mvn spring-boot:run
# or
java -jar target/market-data-scraper.jar
```

Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

## API Endpoints

```bash
curl http://localhost:8080/api/v1/quote/MU               # Book read (watchlist) or Yahoo fallback
curl http://localhost:8080/api/v1/options/MU             # IV/HV/PCR + UA/OI scan from the Book
curl http://localhost:8080/api/v1/short/MU               # Finviz short interest
curl "http://localhost:8080/api/v1/news/MU?limit=5"      # Yahoo news
curl http://localhost:8080/api/v1/snapshot/MU            # full snapshot incl. dataQuality
curl "http://localhost:8080/api/v1/snapshot/batch?tickers=MU,AMD,AVGO"
curl "http://localhost:8080/api/v1/auction/MU?force=true" # NOII Book state (windows gate interpretation)
```

Existing JSON fields keep name, type and semantics; `dataQuality` is additive.

## Key configuration (`src/main/resources/application.properties`)

| Property | Default | Description |
|---|---|---|
| `book.watchlist` | MU,SNDK,INTC,AVGO,MRVL,AMD | Symbols with a permanent streaming line |
| `book.liveness-anchor` | SPY | Heartbeat anchor for the watchdog |
| `book.anchor-fresh-seconds` / `book.ticker-stale-seconds` | 10 / 120 | Watchdog thresholds (REGULAR only) |
| `book.watchdog-interval-ms` | 15000 | Watchdog cadence |
| `ibkr.expected-restart-window` | 23:45-00:20 | Nightly IBC restart window (ET); watchdog stands down |
| `book.scan-interval-ms` | 300000 | Background UA/OI scan cadence for the watchlist |
| `book.scan-refresh-seconds` | 120 | On-demand rescan threshold for non-watchlist tickers |
| `book.metrics-stale-seconds` / `book.scan-stale-seconds` | 600 / 1800 | dataQuality staleness thresholds |
| `auction.opening-window` / `auction.closing-window` | 09:20-09:36 / 15:45-16:02 | NOII interpretation gates (ET) |
| `book.debug-endpoints-enabled` | false | Test hook `POST /api/v1/book/debug/kill/{ticker}` |
| `persistence.enabled` | true | MySQL snapshot history |
| `ua.aggressor.enabled` | false | UA stage 2: per-contract aggressor profiles from historical ticks (additive `aggressorProfile` inside UA entries) |
| `ua.aggressor.max-candidates-per-cycle` / `ua.aggressor.max-requests-per-cycle` | 4 / 20 | Stage-2 escalation width and pacing budget (request-equivalents; BID_ASK counts double) |

## Persistence

Schema is applied manually (`ddl-auto=none`): fresh installs use
`docs/persistence-schema.sql`; existing installs apply the versioned migrations in `docs/`
(latest: `migration-2026-07-10-data-quality.sql`, adds `data_quality_json`). The quality
gate is field-granular: a snapshot with a fresh quote but stale IV is persisted with the IV
flagged stale in `data_quality_json` instead of being blocked; delta metrics ignore
stale-flagged inputs. Snapshots are never persisted while the market is CLOSED.

## Architecture Notes

- **Virtual threads** throughout (reader loop, subscription setup, parallel snapshot assembly)
- **Graceful degradation**: scraper failures return HTTP 200 with `dataAvailable: false`;
  a lost IBKR connection invalidates the Book (values stay readable, flagged stale) and the
  reconnect watchdog + SubscriptionManager restore the streams automatically
- **IBKR quirk knowledge** (wire-log-verified tick mappings, error semantics, sentinel
  filters) lives as comments at the code that implements it; the inventory is in
  `docs/book-rebuild-recon.md`, the rebuild spec in `docs/book-service-rebuild-prompt.md`

## News pipeline V8 migration

V8 makes news articles global and stores ticker membership in link tables. Existing V7 news tables are structurally incompatible.

For a development/test installation:

1. Run `docs/news-history-v7-to-v8-reset.sql`.
2. Run `docs/news-history-schema.sql`.
3. Restart the scraper.

This reset affects only derived news history, story, annotation and condensation data. It does not delete market snapshots.
