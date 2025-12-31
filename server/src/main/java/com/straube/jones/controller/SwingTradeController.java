package com.straube.jones.controller;


import com.straube.jones.trader.dto.SwingTradeDetailDto;
import com.straube.jones.trader.dto.SwingTradeOverviewDto;
import com.straube.jones.trader.dto.TradingAnalysisResult;
import com.straube.jones.agent.StocksAgent;
import com.straube.jones.trader.service.SwingTradeQueryService;
import com.straube.jones.trader.service.TradingIndicatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.io.File;
import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/swing-trades")
@Tag(name = "Swing Trading API", description = "Swing-Trading Watchlist und Detailanalyse")
public class SwingTradeController
{

    private static final String DATA_ROOT_FOLDER = System.getProperty("data.root",
                                                                      "/home/mark/Software/data");
    private final SwingTradeQueryService queryService;
    private final TradingIndicatorService indicatorService;

    public SwingTradeController(SwingTradeQueryService queryService, TradingIndicatorService indicatorService)
    {
        this.queryService = queryService;
        this.indicatorService = indicatorService;
    }


    @GetMapping
    @Operation(summary = "Watchlist abrufen", description = "Liefert eine Liste von Swing-Trading-Kandidaten mit Status und Kennzahlen.")
    @ApiResponse(responseCode = "200", description = "Erfolgreiche Abfrage", content = @Content(schema = @Schema(implementation = SwingTradeOverviewDto.class)))
    public ResponseEntity<List<SwingTradeOverviewDto>> getWatchlist(@Parameter(description = "Filter nach Status (GREEN, YELLOW, RED)")
    @RequestParam(required = false)
    String status,
                                                                    @Parameter(description = "Minimales Chance-Risiko-Verhältnis")
                                                                    @RequestParam(required = false)
                                                                    Double minCrv,
                                                                    @Parameter(description = "Maximaler RSI-Wert")
                                                                    @RequestParam(required = false)
                                                                    Double maxRsi)
    {
        return ResponseEntity.ok(queryService.getWatchlist(status, minCrv, maxRsi));
    }


    @GetMapping("/{symbol}")
    @Operation(summary = "Detailansicht abrufen", description = "Liefert detaillierte Analyse-Daten zu einer Aktie.")
    @ApiResponse(responseCode = "200", description = "Gefunden", content = @Content(schema = @Schema(implementation = SwingTradeDetailDto.class)))
    @ApiResponse(responseCode = "404", description = "Symbol nicht gefunden")
    public ResponseEntity<SwingTradeDetailDto> getDetail(@Parameter(description = "Aktiensymbol (z. B. AAPL)")
    @PathVariable
    String symbol)
    {
        return queryService.getDetail(symbol)
                           .map(ResponseEntity::ok)
                           .orElse(ResponseEntity.notFound().build());
    }


    @GetMapping("/report")
    @Operation(summary = "Technischen Analyse-Report abrufen", description = "Erstellt einen detaillierten technischen Analyse-Report mit verschiedenen Konfigurationen (Standard, Kurzfristig, Langfristig).")
    @ApiResponse(responseCode = "200", description = "Report erfolgreich erstellt", content = @Content(schema = @Schema(implementation = TradingIndicatorService.Report.class)))
    @ApiResponse(responseCode = "404", description = "Symbol nicht gefunden oder keine Daten verfügbar")
    public ResponseEntity<TradingIndicatorService.Report> getReport(@Parameter(description = "Aktiensymbol (z. B. AAPL)", required = true)
    @RequestParam
    String symbol)
    {
        TradingIndicatorService.Report report = indicatorService.getReport(symbol);
        if (report == null)
        { return ResponseEntity.notFound().build(); }
        return ResponseEntity.ok(report);
    }


    @GetMapping("/health")
    @Operation(summary = "Health Check", description = "Prüft, ob der Service läuft.")
    public ResponseEntity<Map<String, Object>> health()
    {
        return ResponseEntity.ok(Map.of("status", "UP", "timestamp", LocalDateTime.now().toString()));
    }


    @GetMapping("/{symbol}/analyze")
    @Operation(summary = "Detaillierte AI-Analyse erstellen", description = "Erstellt einen technischen Report basierend auf Indikatoren und lässt diesen vom AI Agent analysieren. Das Ergebnis ist ein strukturierter JSON-Bericht mit Handlungsempfehlungen.")
    @ApiResponse(responseCode = "200", description = "Erfolgreiche Analyse", content = @Content(schema = @Schema(implementation = TradingAnalysisResult.class)))
    @ApiResponse(responseCode = "404", description = "Symbol nicht gefunden oder keine Daten verfügbar")
    public ResponseEntity<TradingAnalysisResult> analyzeReport(@Parameter(description = "Symbol der Aktie (z.B. US0378331005)", required = true)
    @PathVariable
    String symbol)
    {
        // Cache Logic
        String dateStr = LocalDate.now().toString();
        String filename = symbol + "-" + dateStr + ".json";
        File cacheDir = new File(DATA_ROOT_FOLDER, "cache");
        if (!cacheDir.exists())
        {
            cacheDir.mkdirs();
        }
        File cacheFile = new File(cacheDir, filename);

        ObjectMapper mapper = new ObjectMapper();

        if (cacheFile.exists())
        {
            try
            {
                TradingAnalysisResult cachedResult = mapper.readValue(cacheFile, TradingAnalysisResult.class);
                return ResponseEntity.ok(cachedResult);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        // 1. Erstelle den technischen Report mit Indikatoren
        TradingIndicatorService.Report report = indicatorService.getReport(symbol);

        if (report == null)
        { return ResponseEntity.notFound().build(); }

        // 2. Lasse den Report vom AI Agent analysieren
        TradingAnalysisResult result = StocksAgent.analyzeReport(report);

        // Save to cache
        try
        {
            mapper.writerWithDefaultPrettyPrinter().writeValue(cacheFile, result);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return ResponseEntity.ok(result);
    }
}
