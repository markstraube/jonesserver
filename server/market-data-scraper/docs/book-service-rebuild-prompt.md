# Task: Rebuild market-data-scraper from fetch-on-request to a streaming Last-Value-Cache ("Book") architecture

You are working on `market-data-scraper`, a Java 21 / Spring Boot service that collects US equity
market data from Interactive Brokers (TWS API v10.47 via IB Gateway on a headless Ubuntu host) and
serves JSON snapshots over REST, with MySQL persistence (hybrid scalar/JSON schema).

Read the entire codebase before changing anything. Confirm every assumption below against the
actual code and say so explicitly if reality differs — do not silently adapt the plan.

---

## 1. Why this rebuild (problem statement)

The IBKR TWS API is not request/response. It is an asynchronous tick stream over a persistent
socket: `EClientSocket` sends fire-and-forget messages, an `EReader` thread invokes `EWrapper`
callbacks, correlation is only via client-chosen `reqId`, fields of a "quote" (BID=1, ASK=2,
LAST=4, VOLUME=8, ...) arrive independently, only on change, in no guaranteed order, initially
served from the Gateway's local cache.

The current architecture fights this: every REST snapshot triggers fresh IBKR requests wrapped in
`CompletableFuture`s with timeouts, hoping all relevant ticks arrive inside a window. This has
produced a family of production incidents, all with the same root cause:

- stale LAST price served from Gateway cache during a fast market (price-tick race condition;
  currently mitigated by a volume-sentinel + grace-period workaround in `fetchQuote`)
- frozen IV/HV ticks (generic ticks 104/106 silent) indistinguishable from a calm market
- frozen VOLUME on individual tickers, undetected for an entire pre-market session
- per-contract volume=null on first tick in the UA scan (mitigated by a re-fetch)

Root cause in one sentence: **a streaming source is being consumed by a request/response client,
and silence on a stream is indistinguishable from stillness.**

## 2. Target architecture (this is fixed — do not redesign it)

```
IBKR Gateway ──ticks──▶ SubscriptionManager ──▶ MarketDataBook (in-memory last-value cache)
                            │                        ▲              │
                            │ (permanent streams,    │ (writes)     │ (synchronous reads only)
                            │  auto-resubscribe)     │              ▼
                        UA/OI Scanner (polling) ─────┘         REST / snapshot assembly
                                                               MySQL persistence
```

**MarketDataBook** — the single source of truth for current market state.
- One `TickerBook` per configured symbol. Contents: quote fields (bid, ask, last, open, high,
  low, close, volume), options metrics (iv, hv, putCallRatio, call/put volume), auction/NOII
  fields (auctionPrice, auctionVolume, imbalance, regulatoryImbalance), and the UA/OI scan
  results (written by the polling scanner).
- **Every field carries two timestamps**: `lastChangedAt` (value actually changed) and
  `lastSeenAt` (any tick for this field arrived, even same-value). This pair is the core of the
  design: old `lastChangedAt` + fresh `lastSeenAt` = calm market (healthy); both old = dead feed.
- A switch to Delayed/Frozen (callback values ​​2–4) belongs to the dataQuality block. Don't set lastSeenAt to the current time.
- Thread-safe for one writer thread per source and many readers. Reads never block on IBKR.
- No history, no ring buffers — last value only. History lives in MySQL as before.

**SubscriptionManager** — owns all permanent streaming subscriptions and their lifecycle.
- On connect: one streaming `reqMktData(reqId, contract, "100,104,106,225", false, false, null)`
  per watchlist symbol, plus SPY. One market-data line per symbol — quote, options metrics and
  auction NOII share the same subscription. Never use `snapshot=true` anywhere.
- On `connectionClosed`: mark every field of every ticker as invalidated (ages frozen +
  `connectionLost` flag), do NOT clear values — last known values remain readable, flagged stale.
- On reconnect (the Gateway restarts nightly via IBC — this is routine, not an error): full
  resubscribe with fresh reqIds, staggered to respect pacing limits (≤ ~50 msgs/sec; stagger
  subscriptions ~100ms apart is plenty).
- Liveness watchdog: SPY is the heartbeat anchor (it ticks near-continuously in REGULAR). If
  SPY's volume `lastSeenAt` is fresh (< N seconds) but another ticker's is stale (> M seconds)
  during REGULAR market state, cancel and resubscribe that ticker's stream, log a structured
  event (`AUTO_RESUBSCRIBE ticker=... staleSeconds=...`). Thresholds configurable; sensible
  defaults N=10, M=120. Watchdog only acts in REGULAR (pre/post market silence is normal).

**Read path** — REST endpoints and snapshot assembly read the Book synchronously.
- The snapshot JSON keeps its existing shape (backward compatible) and gains an additive
  `dataQuality` block per section: per-field or per-group `ageSeconds` (derived from
  `lastSeenAt`), `changedAgeSeconds`, and a `stale` boolean computed against market state.
- The persistence quality gate becomes field-granular: a snapshot with fresh quote but stale IV
  is persisted with IV marked stale instead of being all-or-nothing.

**UA/OI scanner** — stays polling (scanning dozens–hundreds of option contracts cannot be
permanently streamed within the ~100-line budget). Unchanged in its request mechanics, but its
results are written into the Book with the same timestamp pair, and snapshot assembly reads them
from there. An OI profile from 40 minutes ago is then honestly labeled as such.

**Explicit non-goals**: no message queue, no event sourcing, no tick history, no websocket push
to clients, no new frameworks. This is an in-process cache plus lifecycle management.

## 3. Hard constraints

1. **Preserve the documented IBKR quirks as knowledge.** The codebase contains hard-won,
   wire-log-verified facts in comments. They must survive the rebuild, moved to wherever the
   corresponding logic lives. Non-exhaustive list — find them all before deleting any file:
   - requested generic tick numbers ≠ delivered tick type ids (100 → 29/30 option volume,
     104/106 → IV/HV tick ids as handled in the wrapper, 225 → 34/35/36/61 auction)
   - put/call ratio must be computed from ticks 29 and 30, not taken from a single tick
   - tick 36 imbalance sign semantics are wire-log-verified — keep the verification note
   - IBKR error 200 on MU monthlies can mean a genuine strike-grid gap, not a failure
   - errors 2104/2107/2119 etc. are informational farm-status messages, not failures
   - `snapshot=true` requests cannot carry generic tick lists (and are banned here anyway)
   - Integer.MAX_VALUE is a sentinel in size ticks and must be filtered
2. **No legacy code survives a phase that supersedes it.** Each phase ends with an explicit
   deletion list (given below) and `./mvnw compile` green plus tests green. Dead code, unused
   futures/maps in the wrapper, and obsolete config keys are removed in the same commit that
   makes them obsolete — not "later".
3. **REST API stays backward compatible.** Existing JSON fields keep name, type, and semantics.
   New information is additive. Existing endpoints keep their paths.
4. **MySQL migrations are additive** (new columns/JSON only, provided as versioned .sql files in
   docs/, applied manually — the app never mutates schema; ddl-auto stays none).
5. **Session/state awareness**: volume resets at session boundaries; the watchdog and staleness
   computation must consult the existing `MarketStateService` so that pre-market silence, closed
   market, and holidays are never classified as feed failure.
6. Virtual-threads execution model and existing logging style are preserved.
7. Commit per phase, message format: `book-phase-N: <summary>`, body lists deletions.
8. Handle Error 100 separate don't mix them with Error 101, make the explicitly visible in the report

## 4. Phases

Each phase must compile, pass tests, and be independently deployable. Do not start a phase
before the previous one is complete including deletions.

### Phase 0 — Book scaffolding (no behavior change)
- Add `book/` package: `MarketDataBook`, `TickerBook`, `TimestampedField<T>` (value,
  lastChangedAt, lastSeenAt, setter that updates both correctly on change vs. same-value).
- Config: `book.watchlist=MU,SNDK,INTC,AVGO,MRVL,AMD`, `book.liveness-anchor=SPY`, thresholds.
- Unit tests for `TimestampedField` semantics (change vs. seen, invalidation) and Book
  concurrency (writer + concurrent readers, no torn reads).
- Nothing reads or writes the Book yet in production paths.
- Deletions: none.

### Phase 1 — Quotes from permanent streams
- `SubscriptionManager` subscribes quote streams (generic tick list still "" in this phase) for
  watchlist + SPY on connect; wrapper routes tickPrice/tickSize for these reqIds into the Book.
- `QuoteService.getQuote` reads the Book (with market-state-aware staleness) instead of calling
  `IbkrMarketDataService.fetchQuote`.
- Reconnect handling: resubscribe on connectionClosed→reconnect; Book invalidation flag.
- Verification: snapshot under live market shows price within own high/low; wire log shows
  exactly one quote subscription per symbol at startup and none per REST request.
- **Deletions**: `fetchQuote` and its entire support machinery in the wrapper — quote futures
  map, volume-sentinel signal map, grace-period config `ibkr.quote-grace-ms`, the
  `tickSnapshotEnd` quote branch, `IbkrQuoteResult`'s static builder cache if now unused. The
  volume-sentinel race fix was a workaround for the fetch model; in the Book model it is
  obsolete by construction. Remove it fully; keep only its explanatory knowledge as a short
  note in SubscriptionManager's javadoc ("why we never use snapshot=true").

### Phase 2 — Options metrics and auction onto the same streams
- Widen the subscription's generic tick list to "100,104,106,225". Wrapper routes IV/HV/PCR
  volumes (29/30) and auction ticks (34/35/36/61) into the Book.
- `OptionsService` reads iv/hv/putCallRatio from the Book; `AuctionService` reads auction fields
  from the Book. Auction time-window logic changes meaning: it no longer gates *requests* (the
  stream is always on) but *interpretation* — outside NOII dissemination windows auction fields
  are placeholder zeros from IBKR; keep `dataAvailable` semantics based on window + ages, and
  keep the wire-log-verified sign handling for tick 36.
- Verification: IV present in REGULAR without any per-request metrics fetch; auction fields
  populate in the 09:28 and 15:50 ET windows.
- **Deletions**: `fetchOptionsMetrics` and `fetchAuctionData` including their pending maps,
  futures, and collection-window logic; config `auction.collect-window-ms`; the metrics 120s
  cache if one exists (the Book replaces it).

### Phase 3 — Liveness watchdog and lifecycle hardening
- Implement the SPY-anchored watchdog with auto-resubscribe as specified above, plus a
  structured startup/reconnect log summary (N subscriptions, reqId ranges).
- Handle the nightly IBC Gateway restart as a first-class scenario: expected disconnect window
  configurable; during it, no watchdog alarms, clean resubscribe after.
- Verification: kill the Gateway connection manually; observe invalidation, reconnect,
  resubscribe, ages recovering; simulate a single dead subscription (cancel it via a test hook)
  and observe AUTO_RESUBSCRIBE.
- Deletions: any remaining per-request reconnect workarounds made redundant.

### Phase 4 — Book-based snapshot assembly, field-granular quality, persistence
- UA/OI scanner writes results into the Book (timestamped); snapshot assembly composes entirely
  from the Book plus the non-IBKR sources (shorts, news) as today.
- Add the additive `dataQuality` block to the snapshot JSON; make the persistence quality gate
  field-granular; add `data_quality_json` column (additive migration).
- Verification: a deliberately stale IV (test hook) yields a persisted snapshot with IV flagged
  stale instead of a blocked write; `minutesSincePrevious`-based delta logic in derived metrics
  ignores fields flagged stale.
- Deletions: any remaining fetch-on-request paths for IBKR data; obsolete config keys; update
  README and docs/ to describe only the Book architecture.

## 5. Working style

- Before Phase 0, produce a short reconnaissance report: file inventory, where each current
  responsibility lives, every IBKR-quirk comment found (file + line), and any mismatch with the
  assumptions in this prompt. Wait for confirmation only if you find a contradiction that
  changes the plan; otherwise proceed.
- Prefer small, reviewable diffs inside each phase; one commit per phase at minimum.
- When touching the wrapper, never break the non-quote paths (UA scan, chain requests) — they
  keep their existing future-based mechanics until their designated phase.
- If a decision is genuinely open (naming, package layout), decide and note it — do not stall.
