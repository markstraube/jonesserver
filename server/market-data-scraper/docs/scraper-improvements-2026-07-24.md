# Options aggressor reliability improvements — 2026-07-24

## What changed

1. **Quote pre-roll around trade anchors** (`ua.aggressor.quote-preroll-seconds=5`)
   - Each sampled BID_ASK island now starts before the selected trade-time quantile.
   - This captures the prevailing NBBO needed to classify the first trades of an island.

2. **Quote-first budget allocation** (`ua.aggressor.quote-budget-share=0.50`)
   - A per-contract historical request slice now reserves roughly half its equivalents for BID_ASK.
   - Prevents the failure mode where TRADES pagination consumes nearly all budget and leaves only one/no quote page.

3. **Maximum sampled-quote age** (`ua.aggressor.max-quote-age-seconds=2`)
   - Trades are no longer classified against an old sampled quote after a long gap.
   - Such trades remain UNKNOWN rather than creating false directional confidence.

## Expected live effect

The SNDK-style result `tickCoverage=1` but `classifiedShare≈0` / `PARTIAL: QUOTES` should improve materially because quote islands now include pre-trade NBBO context and receive a guaranteed share of the pacing budget.

## Still pending

- Structural stage-2 candidate set (spot-near / largest OI / gamma walls independent of unusual-volume gate).
- Persistent T+1 flow-to-OI finalization.
- Flow-adjusted dealer gamma with observed-position coverage.
- Detailed UNKNOWN reason and quote-age diagnostics in the JSON schema.
