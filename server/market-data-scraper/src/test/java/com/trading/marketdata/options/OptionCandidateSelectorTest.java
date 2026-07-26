package com.trading.marketdata.options;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class OptionCandidateSelectorTest {
    @Test
    void keepsTwoPointFiveStrikesEligible() {
        var selected = new OptionCandidateSelector().nearestStrikes(Set.of(900.0, 902.5, 905.0), 902.4, 2);
        assertEquals(902.5, selected.get(0));
        assertTrue(selected.contains(902.5));
    }
}
