# Options collectors v2.7

Adds a process-local `collectorStatus` block to every market snapshot.

Tracked collectors:

- `chainDiscovery`: conId/expiry/strike discovery and detected changes
- `marketSnapshot`: assembled options/OI/UA result
- `openInterest`: OI levels represented in the current options result
- `tradeHistory`: most recent historical trade collection
- `quoteHistory`: most recent historical NBBO collection and sampling status
- `snapshotAssembly`: complete REST snapshot assembly

Each entry contains `status`, `lastRun`, `durationMs`, optional `metrics`, and an optional
message. `systemHealth` summarizes warnings and errors. The status is deliberately not
persisted in `market_snapshot`: it describes the current process, not historical market data,
so no database migration is required for v2.7.
