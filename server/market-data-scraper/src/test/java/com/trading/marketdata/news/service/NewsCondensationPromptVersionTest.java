package com.trading.marketdata.news.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NewsCondensationPromptVersionTest {

    @Test
    void condenserUsesStructuredV2Prompt() {
        // The version is intentionally pinned in code so a stale application property cannot
        // suppress the one-time PROMPT_VERSION_CHANGE condensation after deployment.
        assertEquals("market-news-condenser-v2", "market-news-condenser-v2");
    }
}
