# Session-boundary correction (2026-07-17)

Changes:

- Adds `OVERNIGHT` as a distinct active US-equity trading session.
- Maps Yahoo `PREPRE` / `POSTPOST` to `OVERNIGHT` and infers the IBKR overnight window
  (20:00-04:00 America/New_York, Sunday evening through Friday morning) when Yahoo/SPY says
  `CLOSED`.
- Scheduled snapshots now run during `OVERNIGHT`.
- Options scans are OI-only outside `REGULAR`, avoiding guaranteed volume-tick timeouts when
  the equity trades overnight but the US options market is closed.
- Snapshot price and volume deltas are emitted only when current and previous snapshots belong
  to the same named session and attributed US trading date.
- A cumulative-volume reset (current volume lower than previous volume) produces `null`, never
  a negative `volumeDelta`.
- Full-day and time-normalized RVOL are emitted only during `REGULAR`; extended-hours volume is
  not compared with a regular-session average-day curve.

No database migration is required: `marketState` already stores a string and accepts the new
`OVERNIGHT` value.
