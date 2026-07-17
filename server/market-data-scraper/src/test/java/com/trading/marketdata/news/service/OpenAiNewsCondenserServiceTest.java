package com.trading.marketdata.news.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiNewsCondenserServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void structuredOutputRetainsLegacyAndAddsMachineReadableFields() throws Exception {
        OpenAiNewsCondenserService.Output output = new OpenAiNewsCondenserService.Output();
        output.overview = "Mixed semiconductor tape";
        output.activeNarratives = List.of("AI memory demand remains strong");
        output.contradictions = List.of("Strong pricing but weak equities");
        output.newlyMaterialFacts = List.of("Memory stocks recovered intraday");
        output.dominantTheme = "Fundamentals remain constructive while valuation is questioned";
        output.marketSentiment = "mixed";
        output.confidence = 1.4;
        output.bullishFactors = List.of("Strong DRAM pricing");
        output.bearishFactors = List.of("Sector correction");
        output.materialEvents = List.of("Policy restrictions discussed");
        output.watchItems = List.of("Follow-through after the rebound");
        output.materialTickers = List.of("mu", "MU", "sndk");

        OpenAiNewsCondenserService.normalize(output);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(output));

        assertEquals("Mixed semiconductor tape", json.get("overview").asText());
        assertTrue(json.has("activeNarratives"));
        assertTrue(json.has("contradictions"));
        assertTrue(json.has("newlyMaterialFacts"));
        assertEquals("MIXED", json.get("marketSentiment").asText());
        assertEquals(1.0, json.get("confidence").asDouble());
        assertEquals(List.of("MU", "SNDK"), output.materialTickers);
    }

    @Test
    void normalizationProvidesSafeCollectionsAndClampsInvalidValues() {
        OpenAiNewsCondenserService.Output output = new OpenAiNewsCondenserService.Output();
        output.marketSentiment = "uncertain";
        output.confidence = -0.25;
        output.materialTickers = List.of(" mu ", "", "sndk");

        OpenAiNewsCondenserService.normalize(output);

        assertEquals("MIXED", output.marketSentiment);
        assertEquals(0.0, output.confidence);
        assertEquals(List.of("MU", "SNDK"), output.materialTickers);
        assertTrue(output.bullishFactors.isEmpty());
        assertTrue(output.watchItems.isEmpty());
    }
}
