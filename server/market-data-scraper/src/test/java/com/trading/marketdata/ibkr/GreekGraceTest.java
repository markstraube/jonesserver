package com.trading.marketdata.ibkr;

import com.ib.client.Decimal;
import com.trading.marketdata.book.MarketDataBook;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Greek grace: tickOptionComputation habitually lags the volume/OI completion triggers
 * (the documented Gateway greek-stall pattern) — without the grace, nearly every scanned
 * contract completed before its greeks arrived (observed live: 0 of 46 OI rows with a
 * gamma in REGULAR). These tests drive the wrapper with real TWS-API types through the
 * exact tick sequences.
 */
class GreekGraceTest {

    private static IbkrWrapper wrapper(long graceMs) throws Exception {
        IbkrWrapper w = new IbkrWrapper(new MarketDataBook(), e -> {});
        var fld = IbkrWrapper.class.getDeclaredField("greekGraceMs");
        fld.setAccessible(true);
        fld.setLong(w, graceMs);
        return w;
    }

    @Test
    void greekTickCompletesEarlyWithGamma() throws Exception {
        IbkrWrapper w = wrapper(700);
        CompletableFuture<IbkrOptionContractActivity> f = w.registerContractActivityRequest(1, false, "C");
        w.tickSize(1, 8, Decimal.get(500L)); // completion condition, no gamma yet
        assertFalse(f.isDone(), "grace must defer completion");
        w.tickOptionComputation(1, 13, 0, 0.45, 0.5, 12.0, 0.0, 0.031, 0.2, -0.1, 930.0);
        IbkrOptionContractActivity a = f.get(300, TimeUnit.MILLISECONDS);
        assertEquals(0.031, a.gamma(), 1e-9);
        assertEquals(500L, a.volume());
    }

    @Test
    void graceTimeoutCompletesWithoutGamma() throws Exception {
        IbkrWrapper w = wrapper(150);
        CompletableFuture<IbkrOptionContractActivity> f = w.registerContractActivityRequest(2, false, "C");
        w.tickSize(2, 8, Decimal.get(300L));
        IbkrOptionContractActivity a = f.get(600, TimeUnit.MILLISECONDS);
        assertNull(a.gamma());
        assertEquals(300L, a.volume());
    }

    @Test
    void zeroGraceKeepsOldBehavior() throws Exception {
        IbkrWrapper w = wrapper(0);
        CompletableFuture<IbkrOptionContractActivity> f = w.registerContractActivityRequest(3, false, "C");
        w.tickSize(3, 8, Decimal.get(100L));
        assertTrue(f.isDone());
    }

    @Test
    void gammaBeforeCompletionNeedsNoDeferral() throws Exception {
        IbkrWrapper w = wrapper(700);
        CompletableFuture<IbkrOptionContractActivity> f = w.registerContractActivityRequest(4, false, "C");
        w.tickOptionComputation(4, 13, 0, 0.5, 0.5, 10.0, 0.0, 0.02, 0.2, -0.1, 930.0);
        w.tickSize(4, 8, Decimal.get(50L));
        assertTrue(f.isDone());
        assertEquals(0.02, f.get().gamma(), 1e-9);
    }

    @Test
    void oiPathGraceKeepsWireVerifiedField27Handling() throws Exception {
        IbkrWrapper w = wrapper(150);
        CompletableFuture<IbkrOptionContractActivity> f = w.registerContractActivityRequest(5, true, "P");
        w.tickSize(5, 8, Decimal.get(80L));
        assertFalse(f.isDone(), "waitForOI: volume alone must not complete");
        w.tickSize(5, 27, Decimal.get(0L)); // spurious CALL_OI for a put — wire-verified: ignore
        assertFalse(f.isDone());
        w.tickSize(5, 28, Decimal.get(1234L)); // real PUT_OI → completion condition → grace
        assertFalse(f.isDone(), "grace must defer after matching OI");
        IbkrOptionContractActivity a = f.get(600, TimeUnit.MILLISECONDS);
        assertEquals(1234L, a.openInterest());
        assertNull(a.gamma());
    }
}
