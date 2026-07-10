# Reconnaissance report — Book rebuild (2026-07-10)

Pre-rebuild state as of commit `ab7498e`. Line numbers refer to that state; the quirk
inventory is the checklist for constraint 1 ("preserve documented IBKR quirks").

## File inventory and responsibilities

| File | Responsibility (pre-rebuild) |
|---|---|
| `ibkr/IbkrConnectionManager.java` | Socket lifecycle, EReader loop (virtual thread), reconnect watchdog (30s), reqId sequence |
| `ibkr/IbkrWrapper.java` | All EWrapper callbacks; per-reqId future/builder maps for quotes, metrics, chains, contract details, contract activity, auction |
| `ibkr/IbkrMarketDataService.java` | Fetch-on-request entry points: `fetchQuote` (volume-sentinel + grace), `fetchOptionsMetrics`, `fetchOptionsChain`, `fetchConId`, `fetchContractActivity`, `fetchAuctionData` (fixed collection window) |
| `ibkr/IbkrQuoteResult.java` | Quote accumulation builder with static per-reqId builder cache |
| `ibkr/IbkrOptionsResult.java` | IV/HV/PCR result; PCR computed from ticks 29/30 |
| `ibkr/IbkrAuctionResult.java` | Auction tick collection (34/35/36/61), harvest-after-window semantics |
| `ibkr/IbkrOptionContractActivity.java` | Per-contract volume/OI/bid/ask/last + frozen-quote-at-last pair |
| `service/QuoteService.java` | IBKR-first quote with Yahoo fallback, 60s cache |
| `service/OptionsService.java` | IBKR metrics + UA scan, Barchart fallback, 120s cache |
| `service/AuctionService.java` | NOII window gating (wall clock, ET) around `fetchAuctionData` |
| `service/OptionActivityService.java` | UA/OI scanner: conId→chain→strike selection→per-contract vol/OI, tiered caches, day memory, aggressor classification |
| `service/MarketStateService.java` | PRE/REGULAR/POST/CLOSED/UNKNOWN via Yahoo SPY chart meta, 60s cache, fail-open to UNKNOWN |
| `service/DerivedMetricsService.java` | Pure feature arithmetic incl. deltas vs. previous persisted snapshot |
| `persistence/*` | MySQL history, @Async writes, quality gate (CLOSED skip + stale-tick signature skip) |
| `controller/MarketDataController.java` | REST endpoints, parallel snapshot assembly, batch |
| `scraper/*` | Yahoo/Finviz/Barchart/MarketChameleon scrapers (non-IBKR, untouched by rebuild) |

## IBKR quirk inventory (must survive the rebuild)

1. **Requested generic tick ≠ delivered tick type** — `IbkrWrapper.java:438-446` (100→29/30, 104→23, 106→24), `IbkrAuctionResult.java:6-14` (225→34/35/36/61), `IbkrMarketDataService.java:152-156`.
2. **No PCR tick; compute from 29/30** — `IbkrOptionsResult.java:38-44`, `IbkrWrapper.java:444`.
3. **Tick 36 imbalance sign semantics unverified/wire-log-instrumented; store raw, never abs()** — `IbkrWrapper.java:132-158`, `AuctionData.java:21-26`. INFO logs on every auction tick ARE the verification instrument.
4. **Integer.MAX_VALUE sentinel in size ticks must be filtered** — `IbkrWrapper.java:150,161-167`.
5. **Error 200 on strike/expiry combos = genuine strike-grid gap, not failure; negative-cache, never retry** — `IbkrMarketDataService.java:294-305`, `IbkrContractNotFoundException.java`, plus MU-monthly context at `OptionActivityService.java:178-184`.
6. **2104/2106/2107/2108/2119/2158 are informational farm-status, not failures** — `IbkrWrapper.java:465-468`.
7. **10167 = delayed-data notice, informational, data still arrives** — `IbkrWrapper.java:470-476`.
8. **snapshot=true cannot carry generic tick lists; snapshot answers come from Gateway cache (stale LAST in fast markets)** — `IbkrMarketDataService.java:66-79,259-262,328-331`. This is the root of the volume-sentinel workaround.
9. **Delayed tick ids: 66/67/68/72/73/75/76 (prices), 74 (volume), 88 (last timestamp)** — `IbkrWrapper.java:97-105,118,179`.
10. **IBKR sends BOTH OI fields 27 and 28 for every option contract regardless of right; field 27 arrives first with placeholder 0 — match on contract's right** — `IbkrWrapper.java:195-215`.
11. **Chain callback: filter on tradingClass == ticker, not exchange==SMART (adjusted classes like "2MU"); union across exchanges** — `IbkrWrapper.java:259-269`, `IbkrOptionsChainResult.java:20-23`.
12. **conId must be resolved via reqContractDetails before reqSecDefOptParams (conId=0 unreliable)** — `IbkrMarketDataService.java:208-216`.
13. **Gateway "Model is not valid" stall: one retry on per-contract timeouts** — `OptionActivityService.java:619-641`.
14. **Volume=null on "successful" per-contract fetch (Gateway model stall); one volume-only re-read** — `OptionActivityService.java:563-605`.
15. **tickSnapshotEnd fires ~11s after a snapshot=true request (never on-demand)** — `IbkrMarketDataService.java:32-37`. Historical knowledge; snapshot=true is banned in the Book model.
16. **Option "last" for an untraded contract is the previous session's print** — `IbkrOptionContractActivity.java:18-20`.
17. **IBKR sends -1/0 placeholder prices on quoteless sides; drop, don't store** — `IbkrWrapper.java:64-70`.
18. **CLOSED-market stale-tick composites (fabricated price/OHLC mixes) must never be persisted** — `SnapshotPersistenceService.java:60-79`.

## Mismatches vs. the rebuild prompt

None change the target architecture. Adaptations noted explicitly:

1. **"metrics 120s cache" (Phase 2 deletion list)**: the 120s `options` cache caches the
   *entire* `OptionsService.getOptions` result including the expensive UA/OI scan, not just
   IV/HV/PCR. Deleting it in Phase 2 would make every snapshot re-run a multi-minute scan.
   Adaptation: the cache is deleted in Phase 4 (when the scanner writes into the Book), not
   Phase 2.
2. **`IbkrOptionsResult` javadoc is wrong** (claims 106=PCR, 104=IV, 105=HV). The wrapper
   mapping (104→23 HV, 106→24 IV, no PCR tick) is the wire-verified truth and is what the
   Book keeps. The class is deleted in Phase 2.
3. **REST endpoints accept arbitrary tickers, the Book only holds watchlist+SPY.**
   Decision: Book symbols are served from the Book; non-watchlist tickers keep the existing
   fallback paths (Yahoo quote, Barchart options, on-demand UA scan). This preserves API
   compatibility for e.g. the startup validation call (`AAPL`).
4. **pom.xml declares tws-api coordinate "10.19"** but the wrapper uses the 10.47
   `error(int,long,int,String,String)` signature and the prompt says 10.47. The coordinate is
   a label for the local `lib/TwsApi.jar`; irrelevant to the rebuild, left as is.
5. **No test sources existed** (`src/test` absent, spring-boot-starter-test already in pom).
   Created in Phase 0.
6. **Quote cache (60s)**: `@Cacheable` on `getQuote` would serve 60s-old data on top of a
   live Book. Adaptation: Book reads are uncached; the cache remains only for the Yahoo
   fallback path.
7. Working tree had an uncommitted user deletion of `docs/migration-2026-07-03-iv-hv-state.sql`
   and an untracked `server/market-data-scraper.zip` — both left untouched by the rebuild
   commits.

## Error 100 vs. 101 (constraint 8)

Error **100** = "Max rate of messages per second has been exceeded" (pacing violation on the
request path) and error **101** = "Max number of tickers has been reached" (market-data line
budget exhausted) were previously indistinguishable in the generic `error()` warn path. The
SubscriptionManager handles them separately: 100 → `IBKR_PACING_VIOLATION`, subscription
retried with backoff (the stagger should make this unreachable); 101 → `IBKR_MAX_TICKERS`,
subscription marked permanently failed until the next reconnect (retrying cannot succeed).
Both appear as distinct failure reasons in the structured subscription summary report.
