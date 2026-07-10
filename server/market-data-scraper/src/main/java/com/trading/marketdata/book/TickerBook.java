package com.trading.marketdata.book;

import com.trading.marketdata.model.NewsItem;
import com.trading.marketdata.model.OptionsData;

import java.time.Instant;
import java.util.List;

/**
 * Last-value cache for one symbol: every field the streaming subscription (quotes, options
 * metrics, auction/NOII) or the polling UA/OI scanner produces, each with the
 * lastChangedAt/lastSeenAt timestamp pair. No history, no ring buffers — history lives in
 * MySQL as before.
 *
 * Writers: the EReader callback thread (tick fields) and the scanner thread (scan fields) —
 * disjoint field sets, one writer each. Readers: any thread, never blocking on IBKR.
 */
public final class TickerBook {

    private final String symbol;

    // --- Quote fields (default ticks of the streaming subscription) ---
    private final TimestampedField<Double> bid = new TimestampedField<>();
    private final TimestampedField<Double> ask = new TimestampedField<>();
    private final TimestampedField<Double> last = new TimestampedField<>();
    private final TimestampedField<Double> open = new TimestampedField<>();
    private final TimestampedField<Double> high = new TimestampedField<>();
    private final TimestampedField<Double> low = new TimestampedField<>();
    private final TimestampedField<Double> close = new TimestampedField<>();
    private final TimestampedField<Long> volume = new TimestampedField<>();

    // --- Options metrics (generic ticks 104/106 → 23/24, 100 → 29/30) ---
    private final TimestampedField<Double> impliedVolatility = new TimestampedField<>();
    private final TimestampedField<Double> historicalVolatility = new TimestampedField<>();
    private final TimestampedField<Long> callOptionVolume = new TimestampedField<>();
    private final TimestampedField<Long> putOptionVolume = new TimestampedField<>();

    // --- Auction/NOII (generic tick 225 → 34/35/36/61) ---
    private final TimestampedField<Double> auctionPrice = new TimestampedField<>();
    private final TimestampedField<Long> auctionVolume = new TimestampedField<>();
    private final TimestampedField<Long> imbalance = new TimestampedField<>();
    private final TimestampedField<Long> regulatoryImbalance = new TimestampedField<>();

    // --- UA/OI scan results (written by the polling scanner, not by ticks) ---
    private final TimestampedField<List<OptionsData.UnusualActivity>> unusualActivity = new TimestampedField<>();
    private final TimestampedField<List<OptionsData.OiLevel>> oiProfile = new TimestampedField<>();

    // --- IBKR news (generic tick 292 → tickNews callback; article text via reqNewsArticle) ---
    // Bounded newest-first list, deduped by articleId. The ONE exception to "no history" in
    // the Book: news is inherently a short list, not a scalar — but it is still last-value
    // in spirit (a fixed-size window of the latest items, no unbounded growth, history in
    // MySQL as for everything else). Written exclusively on the EReader thread: both
    // tickNews (headline arrives) and newsArticle (body arrives) are EWrapper callbacks,
    // so the single-writer contract of TimestampedField holds without extra locking.
    private final TimestampedField<List<NewsItem>> news = new TimestampedField<>();

    // --- Data-quality block state ---
    // marketDataType per IBKR callback: 1 = realtime, 2 = frozen, 3 = delayed, 4 = delayed-
    // frozen. A switch to 2–4 is a quality statement about the whole line, not a data tick:
    // it deliberately does NOT touch any field's lastSeenAt (a frozen line is exactly the
    // "silence" the timestamp pair exists to expose).
    private volatile Integer marketDataType;
    private volatile Instant marketDataTypeSince;

    private final List<TimestampedField<?>> allFields = List.of(
            bid, ask, last, open, high, low, close, volume,
            impliedVolatility, historicalVolatility, callOptionVolume, putOptionVolume,
            auctionPrice, auctionVolume, imbalance, regulatoryImbalance,
            unusualActivity, oiProfile, news);

    TickerBook(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    public TimestampedField<Double> bid() { return bid; }
    public TimestampedField<Double> ask() { return ask; }
    public TimestampedField<Double> last() { return last; }
    public TimestampedField<Double> open() { return open; }
    public TimestampedField<Double> high() { return high; }
    public TimestampedField<Double> low() { return low; }
    public TimestampedField<Double> close() { return close; }
    public TimestampedField<Long> volume() { return volume; }

    public TimestampedField<Double> impliedVolatility() { return impliedVolatility; }
    public TimestampedField<Double> historicalVolatility() { return historicalVolatility; }
    public TimestampedField<Long> callOptionVolume() { return callOptionVolume; }
    public TimestampedField<Long> putOptionVolume() { return putOptionVolume; }

    public TimestampedField<Double> auctionPrice() { return auctionPrice; }
    public TimestampedField<Long> auctionVolume() { return auctionVolume; }
    public TimestampedField<Long> imbalance() { return imbalance; }
    public TimestampedField<Long> regulatoryImbalance() { return regulatoryImbalance; }

    public TimestampedField<List<OptionsData.UnusualActivity>> unusualActivity() { return unusualActivity; }
    public TimestampedField<List<OptionsData.OiLevel>> oiProfile() { return oiProfile; }

    public TimestampedField<List<NewsItem>> news() { return news; }

    /**
     * Records one arriving headline (tickNews). Newest first, deduped by articleId (IBKR
     * re-ticks the same story, e.g. after reconnect), capped at {@code maxItems}. EReader
     * thread only — see the field comment.
     */
    public void appendNews(NewsItem item, int maxItems, Instant now) {
        List<NewsItem> cur = news.value();
        java.util.ArrayList<NewsItem> next = new java.util.ArrayList<>(maxItems + 1);
        next.add(item);
        if (cur != null) {
            for (NewsItem n : cur) {
                if (next.size() >= maxItems) break;
                if (item.articleId() != null && item.articleId().equals(n.articleId())) continue;
                next.add(n);
            }
        }
        news.update(List.copyOf(next), now);
    }

    /**
     * Attaches the asynchronously fetched article body (newsArticle callback) to the item it
     * belongs to. A miss is normal, not an error: the item may have been rotated out of the
     * bounded window between request and response. EReader thread only.
     *
     * @return true when the item was found and updated
     */
    public boolean attachArticleText(String articleId, String fullText, Instant now) {
        List<NewsItem> cur = news.value();
        if (cur == null || articleId == null) return false;
        boolean hit = false;
        java.util.ArrayList<NewsItem> next = new java.util.ArrayList<>(cur.size());
        for (NewsItem n : cur) {
            if (articleId.equals(n.articleId()) && n.fullText() == null) {
                next.add(n.withFullText(fullText));
                hit = true;
            } else {
                next.add(n);
            }
        }
        if (hit) news.update(List.copyOf(next), now);
        return hit;
    }

    public void setMarketDataType(int type, Instant now) {
        this.marketDataType = type;
        this.marketDataTypeSince = now;
    }

    public Integer marketDataType() { return marketDataType; }
    public Instant marketDataTypeSince() { return marketDataTypeSince; }

    /**
     * There is NO direct put/call-ratio tick in the TWS API — the ratio must be computed from
     * the call/put option volume ticks (generic tick 100 → tick types 29/30). Null until both
     * sides have been seen or when call volume is zero.
     */
    public Double putCallRatio() {
        Long calls = callOptionVolume.value();
        Long puts = putOptionVolume.value();
        if (calls == null || puts == null || calls <= 0) return null;
        return (double) puts / calls;
    }

    /** Marks every field invalidated (connection lost). Values and ages stay readable. */
    void invalidateAll() {
        for (TimestampedField<?> f : allFields) {
            f.invalidate();
        }
    }

    /**
     * IBKR error 10090 names the REQUESTED generic tick number whose subscription the
     * account lacks (e.g. "SPY ARCA/Auction:225"). Marks that tick group's Book fields as
     * notSubscribed so their silence is attributed to the subscription, not the market:
     *   225 → auction/NOII fields (delivered tick types 34/35/36/61)
     *   100 → call/put option volume (29/30; the computed PCR dies with them)
     *   104 → historical volatility (23)
     *   106 → implied volatility (24)
     *   292 → news headlines (tickNews callback; needs at least one news feed subscription)
     * Default ticks (bid/ask/last/volume/OHLC) need no generic tick and are never affected.
     *
     * @return true when the number mapped to a known group
     */
    public boolean markNotSubscribedGenericTick(int genericTick) {
        switch (genericTick) {
            case 225 -> {
                auctionPrice.markNotSubscribed();
                auctionVolume.markNotSubscribed();
                imbalance.markNotSubscribed();
                regulatoryImbalance.markNotSubscribed();
            }
            case 100 -> {
                callOptionVolume.markNotSubscribed();
                putOptionVolume.markNotSubscribed();
            }
            case 104 -> historicalVolatility.markNotSubscribed();
            case 106 -> impliedVolatility.markNotSubscribed();
            case 292 -> news.markNotSubscribed();
            default -> {
                return false;
            }
        }
        return true;
    }
}
