package com.trading.marketdata.ibkr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * IBKR prefixes some tickNews headlines with brace-wrapped metadata, e.g.
 * "{K:n/a,C:0.6}Micron raises guidance". The metadata is provider-internal
 * (sentiment/keyword flags) and must not leak into the stored headline.
 */
class HeadlineMetaStripTest {

    @Test
    void stripsLeadingBraceGroup() {
        assertEquals("Micron raises guidance",
                IbkrWrapper.stripHeadlineMeta("{K:n/a,C:0.6}Micron raises guidance"));
    }

    @Test
    void leavesPlainHeadlinesAlone() {
        assertEquals("Plain headline", IbkrWrapper.stripHeadlineMeta("Plain headline"));
    }

    @Test
    void onlyLeadingGroupIsStripped() {
        assertEquals("Earnings {beat} expected",
                IbkrWrapper.stripHeadlineMeta("Earnings {beat} expected"));
    }

    @Test
    void trimsWhitespace() {
        assertEquals("Headline", IbkrWrapper.stripHeadlineMeta("{A:1} Headline "));
    }
}
