package com.trading.marketdata.ibkr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalRequestBudgetTest {

    @Test
    void sliceCapsConsumptionButDrawsFromParent() {
        HistoricalRequestBudget root = new HistoricalRequestBudget(20);
        HistoricalRequestBudget slice = root.slice(5);

        assertTrue(slice.tryConsume(3));
        assertTrue(slice.tryConsume(2));
        assertFalse(slice.tryConsume(1)); // slice cap reached
        assertEquals(15, root.remaining()); // parent charged exactly what the slice used
    }

    @Test
    void unusedSliceReservesNothing() {
        HistoricalRequestBudget root = new HistoricalRequestBudget(20);
        HistoricalRequestBudget frugal = root.slice(10);
        assertTrue(frugal.tryConsume(2)); // uses 2 of its 10

        assertEquals(18, root.remaining()); // the other 8 never left the pool
        HistoricalRequestBudget next = root.slice(18);
        assertTrue(next.tryConsume(18));
        assertEquals(0, root.remaining());
    }

    @Test
    void sliceNeverExceedsParentRemaining() {
        HistoricalRequestBudget root = new HistoricalRequestBudget(4);
        HistoricalRequestBudget slice = root.slice(10); // asks for more than exists
        assertTrue(slice.tryConsume(4));
        assertFalse(slice.tryConsume(1));
        assertEquals(0, root.remaining());
    }

    @Test
    void sliceRespectsConcurrentParentDrain() {
        // Defensive: even if the parent shrinks after slicing (not the escalation pattern,
        // but nothing should break), consumption clears BOTH budgets.
        HistoricalRequestBudget root = new HistoricalRequestBudget(6);
        HistoricalRequestBudget slice = root.slice(5);
        assertTrue(root.tryConsume(4)); // parent drained directly
        assertTrue(slice.tryConsume(2));
        assertFalse(slice.tryConsume(1)); // slice has cap left (3) but parent is empty
        assertEquals(0, root.remaining());
    }
}
