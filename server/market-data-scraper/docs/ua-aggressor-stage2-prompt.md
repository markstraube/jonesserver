# Task: UA scan stage 2 — per-contract aggressor classification from historical ticks

You are working on `market-data-scraper`, the Java 21 / Spring Boot service with the
MarketDataBook streaming architecture (permanent IBKR streams → last-value cache with
lastChangedAt/lastSeenAt timestamp pairs, polling UA/OI scanner, MySQL persistence). The Book
rebuild and the IBKR news integration are complete and deployed.

Read the current codebase before changing anything — in particular `OptionActivityService`
(the UA/OI scanner), `IbkrWrapper` (pending-future mechanics for per-contract requests),
`IbkrMarketDataService.fetchContractActivity`, `TickerBook`/`TimestampedField` (Book write
contract), and the UA result model inside `OptionsData`. Confirm every assumption below
against the actual code and say so explicitly if reality differs — do not silently adapt.

---

## 1. Problem statement

The UA scan flags contracts on volume/OI ratios and premium size, but it cannot say WHO was
aggressing. The snapshot fields (last, bid, ask at scan time) classify at most the final
print of the day; the 800 contracts before it are invisible. "Unusual volume" without
direction is half a signal: 5,000 calls traded could be an institution lifting offers
(bullish, urgent) or a fund writing covered calls into the bid (neutral to bearish).

Goal: for every contract the stage-1 scan flags, reconstruct the day's trade-by-trade
aggressor distribution — buy volume, sell volume, unknown volume — plus sweep/block texture,
and persist it with the UA result. This is an ESCALATION stage: the expensive analysis runs
only on the handful of candidates the cheap filter already flagged.

## 2. Verified API facts (extracted from the project's own lib/TwsApi.jar — trust these)

Request (EClient), one call per whatToShow:

```java
// reqId, contract, startDateTime, endDateTime, numberOfTicks, whatToShow,
// useRth, ignoreSize, miscOptions
client.reqHistoricalTicks(reqId, contract, start, "", 1000, "TRADES",  0, false, null);
client.reqHistoricalTicks(reqId, contract, start, "", 1000, "BID_ASK", 0, false, null);
```

- Exactly ONE of startDateTime/endDateTime is non-empty. Format `yyyyMMdd HH:mm:ss` plus
  explicit timezone suffix (use `US/Eastern`, e.g. `"20260710 09:30:00 US/Eastern"`).
  Start set + end empty = ticks FORWARD from start; paginate by advancing start past the
  last received tick's time until the day is covered or fewer than the requested count
  return.
- numberOfTicks max 1000 per request.
- Callbacks (DefaultEWrapper — override in IbkrWrapper):
  - `historicalTicksLast(int reqId, List<HistoricalTickLast> ticks, boolean done)`
  - `historicalTicksBidAsk(int reqId, List<HistoricalTickBidAsk> ticks, boolean done)`
  - `done=true` marks the final batch of one request; complete the pending future then.
- `HistoricalTickLast`: `time()` (epoch SECONDS, long), `price()`, `size()` (Decimal),
  `exchange()` (String), `specialConditions()` (String), `tickAttribLast()`
  (`pastLimit()`, `unreported()`).
- `HistoricalTickBidAsk`: `time()` (epoch seconds), `priceBid()`, `priceAsk()`,
  `sizeBid()`, `sizeAsk()` (Decimal).
- `Decimal` must be converted the same way the existing tickSize handling does — reuse that
  code path's conversion, including the Integer.MAX_VALUE sentinel filter.
- Pacing: historical tick requests share the historical-data budget (~60 requests per 10
  minutes; BID_ASK requests count DOUBLE). Budget the stage accordingly (see §5).
- One-second timestamp resolution means several trades and several quote updates can share
  one timestamp — the classifier below must handle that explicitly.

## 3. Classification spec (Lee-Ready adapted for options — this is fixed)

Join: for each trade, the prevailing quote is the LATEST BidAsk tick with
`quoteTime <= tradeTime`. Same-second collisions: use the latest quote with
`quoteTime < tradeTime` when one exists in a strictly earlier second; if the only candidates
share the trade's second, use the last of them (arrival order within the callback list is
chronological). If no quote at or before the trade exists (trade before first quote of the
window), classify UNKNOWN.

Per trade, with bid B, ask A (require A > B > 0, else UNKNOWN):

- price >= A            → BUY  (aggressor lifted the offer)
- price <= B            → SELL (aggressor hit the bid)
- otherwise compute quotePosition = (price − B) / (A − B) in (0,1):
  - quotePosition >= 0.65 → BUY_LEAN
  - quotePosition <= 0.35 → SELL_LEAN
  - else                  → UNKNOWN (midpoint zone; deliberately NOT tick-tested —
    midpoint prints in options are disproportionately spread legs and price-improvement
    fills, a tick-test fallback would add noise, not signal)

Exclusions BEFORE classification (excluded volume is reported separately, not silently
dropped):
- `specialConditions()` containing combo/spread-leg markers → excluded as SPREAD_LEG. Log
  every distinct specialConditions string seen at DEBUG for the first sessions — the code
  set is exchange-dependent and poorly documented; we build the definitive marker list from
  our own wire observations (start with the documented single-leg-of-multi-leg conditions,
  keep the matcher configurable via `ua.aggressor.spread-condition-markers`).
- `tickAttribLast().unreported()` → excluded as UNREPORTED (off-exchange/late prints carry
  no aggressor information at the NBBO).

Aggregate per contract into an `AggressorProfile`:
- buyVolume (BUY + BUY_LEAN), sellVolume (SELL + SELL_LEAN), unknownVolume,
  excludedSpreadVolume, excludedUnreportedVolume — keep the strong and LEAN buckets also
  separately (buyStrongVolume etc.); the JSON reports both granularities
- buyNotional / sellNotional (price × size × 100)
- vwapBuy / vwapSell
- sweepCount, sweepVolume, largestSweepVolume (see §4)
- blockCount, blockVolume, largestBlockVolume
- tickCoverage: analyzed trade volume ÷ contract day volume from the scan (honesty metric —
  pagination caps mean we may not see everything)
- firstTradeAt / lastTradeAt of the analyzed window

## 4. Sweep and block detection (fixed)

After classification, on the chronologically ordered trade list:

- SWEEP: a run of >= 3 prints of the same contract within a 500ms window (config
  `ua.aggressor.sweep-window-ms`, remember: 1s timestamp resolution → same-second is the
  practical grouping) hitting >= 2 distinct `exchange()` values, all with the same
  classified direction (LEAN counts toward its direction). Sum as one sweep event.
  Direction of the sweep = the shared direction. This is the urgency signal — someone
  cleared the book across venues.
- BLOCK: any single print with size >= `ua.aggressor.block-min-contracts` (default 100).
  Blocks are counted regardless of classification bucket (a midpoint block is still a
  block — negotiated prints cluster at mid).

## 5. Architecture (this is fixed — do not redesign)

- New package `analysis/`: `AggressorClassifier` (pure functions: quote join, per-trade
  classification, sweep/block detection, aggregation — NO IBKR types leak in except the two
  HistoricalTick* lists at the boundary, convert immediately to internal records) and
  `AggressorProfile` (result record).
- `IbkrMarketDataService` gains `fetchDayTicks(contract, sessionStartEt)` returning the
  joined raw material (trades + quotes), built on new pending-future plumbing in
  `IbkrWrapper` following the existing per-contract pattern (pending map + future +
  cleanup in `error()`; route by reqId; complete on `done=true`). Timeout: reuse the
  existing per-contract timeout config; on timeout return what arrived, flagged partial.
- Escalation in `OptionActivityService`: stage 1 flags candidates exactly as today; for the
  top `ua.aggressor.max-candidates-per-cycle` (default 4) candidates by premium notional,
  stage 2 fetches ticks and computes the profile. Budget math to respect in code review:
  per candidate = ceil(dayTicks/1000) TRADES requests + ceil(quoteTicks/1000)×2 BID_ASK
  request-equivalents; cap total request-equivalents per scan cycle at
  `ua.aggressor.max-requests-per-cycle` (default 20) and skip remaining candidates when
  exhausted (log SKIPPED_BUDGET).
- The profile is attached to the existing UA result entries (additive JSON fields) and
  written into the Book by the scanner exactly like today's scan results — same timestamp
  pair, no separate Book field.
- OI-delta join: using the existing `oiDayMemory`, when yesterday's OI for the contract is
  known, compute `oiDelta` and label `positionInference`: buy-dominant volume + OI up →
  OPENING_BUYS; buy-dominant + OI down → SHORT_COVER; sell-dominant + OI up →
  OPENING_WRITES; sell-dominant + OI down → CLOSING_SALES; otherwise MIXED/UNKNOWN.
  Buy-dominant means buyVolume >= 60% of (buy+sell); mirror for sell. This label is the
  single most valuable output — it turns "unusual volume" into a positioning thesis.
- Persistence: additive columns/JSON only, versioned .sql in docs/, ddl-auto stays none.
- REST stays backward compatible; new fields are additive inside the UA entries.

## 6. Hard constraints

1. Preserve all documented IBKR quirks; add the new wire-verified facts from §2 as code
   comments where the logic lives (especially: epoch-seconds resolution and its collision
   handling, the double-counting of BID_ASK pacing, Decimal sentinel filtering).
2. The classifier core is PURE and unit-tested exhaustively: quote-join edge cases (trade
   before first quote, same-second collisions, crossed/locked quotes → UNKNOWN),
   classification boundaries (exactly at bid/ask/0.35/0.65), sweep grouping (window
   boundary, single-exchange runs are NOT sweeps, mixed-direction runs are NOT sweeps),
   exclusion accounting (excluded volume appears in excluded buckets and nowhere else),
   tickCoverage arithmetic.
3. Stage 2 must be fully disableable via `ua.aggressor.enabled=false` with zero behavior
   change to stage 1.
4. No new frameworks, virtual-threads model and logging style preserved.
5. Do not touch the Book streaming paths, news, quotes, auction — this task lives entirely
   in the scanner escalation and the wrapper's per-contract request layer.
6. Commit format `aggressor-phase-N: <summary>`.

## 7. Phases

### Phase 0 — Classifier core (no IBKR, no behavior change)
Pure `AggressorClassifier` + `AggressorProfile` + the full unit-test battery from §6.2.
Internal records for trades/quotes; a small builder to feed test fixtures.

### Phase 1 — Tick fetch plumbing
Wrapper callbacks + pending futures + `fetchDayTicks` with pagination, timeout-partial
semantics, and the pacing budget accounting (request-equivalents counter per scan cycle,
exposed in the log line of each stage-2 run:
`UA_AGGRESSOR ticker=… contract=… requests=… coverage=…`).
Verification: against a liquid SPY weekly contract during REGULAR, fetch a day window and
log the reconstructed trade count vs. the contract's volume tick.

### Phase 2 — Escalation wiring + Book/persistence/REST
Candidate selection, profile computation, OI-delta join and positionInference, additive
JSON + migration, honest partial flags (timeout/budget-skip visible in the output).
Verification: a scan cycle on a UA day produces profiles whose
buyVolume+sellVolume+unknownVolume+excluded == analyzed volume, tickCoverage <= 1, and at
least one hand-checked contract's distribution matches a manual spot check of its tick
data.

## 8. Working style

Reconnaissance report before Phase 0 (current UA result model, wrapper pending-pattern,
oiDayMemory shape, any mismatch with §2/§5 assumptions). Small reviewable diffs, decide
open naming/layout questions yourself and note them, never break the existing scan
mechanics — stage 1 must behave identically with stage 2 disabled AND enabled.
