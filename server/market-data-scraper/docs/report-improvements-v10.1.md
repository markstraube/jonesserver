# Report safety improvements (v10.1)

## Implemented

1. **Quality-gated position inference**
   - `OPENING_BUYS`, `OPENING_WRITES`, `SHORT_COVER`, and `CLOSING_SALES` are emitted only
     when the aggressor profile is `MEDIUM` or `HIGH` quality.
   - Low/insufficient profiles return `positionInference: UNKNOWN` plus an explicit reason.
   - 0DTE contracts retain `EXPIRES_TODAY` because the limitation is structural, not a
     missing-data condition.

2. **Explicit inference confidence**
   - Added `positionInferenceConfidence`.
   - Added `positionInferenceReason`.
   - The reason explicitly documents the epoch mismatch: OI delta describes the prior
     session while aggressor dominance describes the current session.

3. **Dealer-gamma model disclosure** *(completed in the follow-up patch — the fields were
   documented here before they existed in code)*
   - Added `signModel` (constant `CUSTOMERS_LONG_PUTS_SHORT_CALLS`).
   - Added `observedDealerPositions: false`.
   - Added coverage-based `confidence`: `MEDIUM` only when BOTH global and wall coverage are
     >= 0.70, otherwise `LOW`; deliberately never `HIGH` (modeled quantity under an
     unverifiable positioning assumption).
   - Follow-up also fixed the null-OI-delta inference branch: quality-sufficient profiles
     whose contract has no prior-session OI in memory now report
     `positionInferenceConfidence: INSUFFICIENT` with an accurate reason instead of a HIGH-
     confidence UNKNOWN with a reason claiming a join that never happened.

## Recommended next phase

The highest-value remaining IBKR-native feature is an equity microstructure block based on
`reqTickByTickData` (`Last`/`AllLast` + `BidAsk`): 1/5/15-minute buy-initiated and
sell-initiated share volume, VWAP distance, classification coverage, largest prints and,
optionally, Level-2 imbalance. This requires a new permanent tick-by-tick subscription path
and careful market-data-line budgeting, so it was not mixed into this safety patch.
