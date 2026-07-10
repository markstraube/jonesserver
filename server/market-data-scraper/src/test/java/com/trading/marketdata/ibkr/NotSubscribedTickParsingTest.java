package com.trading.marketdata.ibkr;

import com.trading.marketdata.book.MarketDataBook;
import com.trading.marketdata.book.TickerBook;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Error 10090: the message text names the unsubscribed tick group — parse it structurally. */
class NotSubscribedTickParsingTest {

    @Test
    void parsesLiveObservedGermanMessage() {
        // Verbatim from the production log 2026-07-10 (note: no space before the tail)
        String msg = "Sie haben einen Teil der angeforderten Marktdaten nicht abonniert. "
                + "Ticks, für die kein Abo erforderlich ist, werden weiterhin angezeigt.SPY ARCA/Auction:225";
        assertEquals(List.of(225), IbkrWrapper.parseNotSubscribedGenericTicks(msg));
    }

    @Test
    void parsesMultipleGroupsAndIgnoresNoise() {
        String msg = "Not subscribed. MU NASDAQ/Auction:225,MU NASDAQ/OptionVolume:100 (14:45:24)";
        assertEquals(List.of(225, 100), IbkrWrapper.parseNotSubscribedGenericTicks(msg));
        assertTrue(IbkrWrapper.parseNotSubscribedGenericTicks("no tick group here 12:30").isEmpty(),
                "plain times must not parse as tick groups");
        assertTrue(IbkrWrapper.parseNotSubscribedGenericTicks(null).isEmpty());
    }

    @Test
    void genericTick225MarksAuctionFieldsOnly() {
        MarketDataBook book = new MarketDataBook();
        TickerBook spy = book.book("SPY");
        spy.last().update(560.0, Instant.now());

        assertTrue(spy.markNotSubscribedGenericTick(225));
        assertTrue(spy.auctionPrice().get().notSubscribed());
        assertTrue(spy.auctionVolume().get().notSubscribed());
        assertTrue(spy.imbalance().get().notSubscribed());
        assertTrue(spy.regulatoryImbalance().get().notSubscribed());
        assertFalse(spy.last().get().notSubscribed(), "default ticks are never affected");
        assertFalse(spy.impliedVolatility().get().notSubscribed(), "other generic groups untouched");
        assertFalse(spy.markNotSubscribedGenericTick(233), "unknown group must report unmapped");
    }

    @Test
    void arrivingTickClearsNotSubscribed() {
        TickerBook mu = new MarketDataBook().book("MU");
        mu.markNotSubscribedGenericTick(106);
        assertTrue(mu.impliedVolatility().get().notSubscribed());
        mu.impliedVolatility().update(0.55, Instant.now());
        assertFalse(mu.impliedVolatility().get().notSubscribed(),
                "a delivered tick proves the subscription covers the field");
    }
}
