package com.trading.marketdata.analysis;

import java.util.List;

/**
 * Empirical intraday cumulative-volume curve of one symbol, built from the service's OWN
 * persisted snapshots (cumulative day volume per scan). Purpose: time-normalize relative
 * volume — a plain RVOL of 0.47 at 13:00 reads as "quiet day" when it is in fact an
 * AVERAGE day half-way through; the honest comparison is against the average volume AT THE
 * SAME TIME OF DAY. The curve is deliberately empirical, not modeled: equity volume
 * follows a pronounced U (open and close carry disproportionate share), and the U differs
 * by symbol — the symbol's own history is the model.
 *
 * Construction: the session [0, SESSION_SECONDS] is split into fixed bins; each qualifying
 * historical day contributes its cumulative share (cumulative volume ÷ the day's final
 * volume) at every bin edge via last-observation-carried-forward; bins average across
 * days. A day qualifies only when its last snapshot lies in the final stretch of the
 * session — a day whose recording stopped at noon would otherwise donate shares computed
 * against HALF a day's volume and bend the whole curve upward.
 *
 * Pure and dependency-free; the service layer owns queries, timezones and caching.
 */
public final class IntradayVolumeCurve {

    /** Regular US equity session length: 09:30–16:00 ET. */
    public static final int SESSION_SECONDS = 6 * 3600 + 1800;
    static final int BIN_SECONDS = 300; // 5-minute bins → 78 bins + closing edge
    static final int BINS = SESSION_SECONDS / BIN_SECONDS;
    /** A day qualifies when its last snapshot is at/after this point (15:30 ET). */
    static final int QUALIFYING_LAST_SNAPSHOT_SECONDS = SESSION_SECONDS - 1800;

    /** One observation: cumulative day volume at a moment of the session. */
    public record Point(int secondsIntoSession, long cumulativeVolume) {}

    private final double[] shareAtBinEdge; // index i = share at second i*BIN_SECONDS
    private final int daysUsed;

    private IntradayVolumeCurve(double[] shareAtBinEdge, int daysUsed) {
        this.shareAtBinEdge = shareAtBinEdge;
        this.daysUsed = daysUsed;
    }

    public int daysUsed() { return daysUsed; }

    /**
     * Builds the curve from per-day snapshot series, or returns null when fewer than
     * {@code minDays} days qualify — too little history for a curve that gates a metric.
     * Points may arrive unsorted; days with a zero final volume are skipped.
     */
    public static IntradayVolumeCurve build(List<List<Point>> days, int minDays) {
        double[] sum = new double[BINS + 1];
        int used = 0;
        for (List<Point> day : days) {
            if (day == null || day.isEmpty()) continue;
            List<Point> sorted = day.stream()
                    .sorted(java.util.Comparator.comparingInt(Point::secondsIntoSession))
                    .toList();
            Point last = sorted.get(sorted.size() - 1);
            if (last.secondsIntoSession() < QUALIFYING_LAST_SNAPSHOT_SECONDS) continue; // partial day
            double finalVolume = last.cumulativeVolume();
            if (finalVolume <= 0) continue;

            int p = 0;
            long carried = 0; // LOCF: share before the first snapshot is 0
            for (int bin = 0; bin <= BINS; bin++) {
                int edge = bin * BIN_SECONDS;
                while (p < sorted.size() && sorted.get(p).secondsIntoSession() <= edge) {
                    carried = sorted.get(p).cumulativeVolume();
                    p++;
                }
                sum[bin] += Math.min(carried / finalVolume, 1.0);
            }
            used++;
        }
        if (used < minDays) return null;
        double[] avg = new double[BINS + 1];
        for (int i = 0; i <= BINS; i++) avg[i] = sum[i] / used;
        avg[BINS] = 1.0; // by construction ~1, pinned exactly so post-close comparisons equal plain RVOL
        return new IntradayVolumeCurve(avg, used);
    }

    /**
     * Expected cumulative share of an average day's volume at {@code secondsIntoSession},
     * linearly interpolated between bin edges. Clamped to the session: negative → 0
     * (pre-market), beyond the close → 1.
     */
    public double shareAt(int secondsIntoSession) {
        if (secondsIntoSession <= 0) return shareAtBinEdge[0] * 0; // 0 — kept explicit
        if (secondsIntoSession >= SESSION_SECONDS) return 1.0;
        int bin = secondsIntoSession / BIN_SECONDS;
        double within = (secondsIntoSession - bin * BIN_SECONDS) / (double) BIN_SECONDS;
        return shareAtBinEdge[bin] + (shareAtBinEdge[bin + 1] - shareAtBinEdge[bin]) * within;
    }
}
