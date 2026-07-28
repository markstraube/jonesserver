# v2.8 — Collector health framework

This version turns `collectorStatus` into a common operational health model.

## Additions

- First-class global `ibkrConnection` status, merged into every ticker snapshot.
- `systemHealth=ERROR` whenever the IBKR connection is unavailable.
- Explicit run lifecycle: `COMPLETED`, `SKIPPED`, `FAILED`.
- Stable collector IDs and dependency lists.
- Separate `startedAt`, `finishedAt`, `lastRun`, and `durationMs` fields.
- Standard metrics on every collector: `itemsRequested`, `itemsProcessed`,
  `itemsSucceeded`, and `itemsFailed`.
- Structured warning and error arrays in addition to the concise message.
- Chain discovery reports `SKIPPED` rather than a misleading zero-duration partial run
  when the underlying conId cannot be resolved.

The status remains process-local and is not persisted as market history.
