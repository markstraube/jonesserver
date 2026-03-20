package com.straube.jones.controller;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.straube.jones.db.DayCounter;
import com.straube.jones.service.IndicatorService;
import com.straube.jones.service.MarketDataService;
import com.straube.jones.trader.TradingStrategyAnalyzer;
import com.straube.jones.trader.collectors.SwingTradeQueryService;
import com.straube.jones.trader.collectors.TradingIndicatorService;
import com.straube.jones.trader.dto.DailyPrice;
import com.straube.jones.trader.dto.IndicatorDto;
import com.straube.jones.trader.dto.RatingDto;
import com.straube.jones.trader.dto.SwingTradeDetailDto;
import com.straube.jones.trader.dto.SwingTradeOverviewDto;
import com.straube.jones.trader.indicators.RatingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/swing-trades")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Swing Trading API", description = "Swing-Trading Watchlist und Detailanalyse")
public class SwingTradeController
{
    private final SwingTradeQueryService queryService;
    private final TradingIndicatorService indicatorService;
    private final RatingService ratingService;
    private final IndicatorService indicatorDtoService;
    private final MarketDataService marketDataService;
    
    public SwingTradeController(SwingTradeQueryService queryService,
                                TradingIndicatorService indicatorService,
                                RatingService ratingService,
                                IndicatorService indicatorDtoService,
                                MarketDataService marketDataService)
    {
        this.queryService = queryService;
        this.indicatorService = indicatorService;
        this.ratingService = ratingService;
        this.indicatorDtoService = indicatorDtoService;
        this.marketDataService = marketDataService;
    }


    @GetMapping
    @Operation(summary = "Watchlist abrufen", description = "Liefert eine Liste von Swing-Trading-Kandidaten basierend auf technischen Indikatoren und Ratings. "
                    + "Die Liste enthält Stammdaten von OnVista, aktuelle Indikatoren (RSI, MACD, Volumen) und Swing-Trading-Kennzahlen (CRV, Support-Abstand). "
                    + "Die Kandidaten werden vorab gefiltert nach Ratings (BUY in Short/Mid/Long).")
    @ApiResponse(responseCode = "200", description = "Erfolgreiche Abfrage der Watchlist", content = @Content(schema = @Schema(implementation = SwingTradeOverviewDto.class)))
    public ResponseEntity<List<SwingTradeOverviewDto>> getWatchlist(@Parameter(description = "Filter nach Status (GREEN, YELLOW, RED)")
    @RequestParam(required = false)
    String status,
                                                                    @Parameter(description = "Minimales Chance-Risiko-Verhältnis (CRV)")
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


    @GetMapping("/candidates")
    @Operation(summary = "Kandidaten abrufen", description = "Liefert eine Liste mit Stammdaten zu Unternehmen aus der tOnVista Tabelle, die basierend auf Ratings ausgewählt wurden.")
    @ApiResponse(responseCode = "200", description = "Erfolgreiche Abfrage", content = @Content(schema = @Schema(implementation = com.straube.jones.dto.OnVistaDto.class)))
    public ResponseEntity<List<com.straube.jones.dto.OnVistaDto>> getCandidates()
    {
        return ResponseEntity.ok(queryService.getCandidates());
    }


    @GetMapping("/report")
    @Operation(summary = "Technischen Analyse-Report abrufen", description = "Erstellt einen detaillierten technischen Analyse-Report mit verschiedenen Konfigurationen (Standard, Kurzfristig, Langfristig).")
    @ApiResponse(responseCode = "200", description = "Report erfolgreich erstellt", content = @Content(schema = @Schema(implementation = TradingIndicatorService.Report.class)))
    @ApiResponse(responseCode = "404", description = "Symbol nicht gefunden oder keine Daten verfügbar")
    public ResponseEntity<TradingIndicatorService.Report> getReport(@Parameter(description = "Aktiensymbol (z. B. AAPL)", required = true)
    @RequestParam
    String symbol,
                                                                    @Parameter(description = "Optionaler Endzeitpunkt (Timestamp in ms) für die Analyse", required = false)
                                                                    @RequestParam(required = false)
                                                                    Long endTime)
    {
        long day = (endTime != null) ? DayCounter.get(endTime) : DayCounter.now();
        TradingIndicatorService.Report report = indicatorService.getReport(symbol, day);
        if (report == null)
        { return ResponseEntity.notFound().build(); }
        return ResponseEntity.ok(report);
    }


    @PostMapping("/ratings")
    @Operation(summary = "Ratings abrufen", description = "Liefert Ratings für die übergebene Liste von Symbolen.")
    public ResponseEntity<List<RatingDto>> getRatings(@RequestBody
    List<String> codes,
                                                      @Parameter(description = "Startzeitpunkt (Timestamp ms)", required = false)
                                                      @RequestParam(required = false)
                                                      Long startTime,
                                                      @Parameter(description = "Endzeitpunkt (Timestamp ms)", required = false)
                                                      @RequestParam(required = false)
                                                      Long endTime)
    {
        return ResponseEntity.ok(ratingService.getRatings(codes, startTime, endTime));
    }


    @PostMapping("/indicators")
    @Operation(summary = "Technische Indikatoren abrufen", description = "Liefert detaillierte technische Indikatoren für die übergebene Liste von Aktiensymbolen. "
                    + "Die Methode gibt historische Indikator-Daten aus der tIndicators-Tabelle zurück.\n\n"
                    + "**Zurückgegebene Indikatoren:**\n"
                    + "- **Bollinger Bänder**: Oberes, mittleres und unteres Band (15-Tage-Periode)\n"
                    + "- **RSI (Relative Strength Index)**: Momentum-Indikator (0-100)\n"
                    + "- **MACD**: Moving Average Convergence Divergence mit Signal-Linie\n"
                    + "- **Gleitende Durchschnitte**: SMA und EMA für 5, 10, 20 und 30 Tage\n"
                    + "- **Unterstützung/Widerstand**: Berechnete Support- und Resistance-Levels\n"
                    + "- **Volumen**: Handelsvolumen\n\n"
                    + "**Zeitraum-Filter:**\n"
                    + "- `startTime`: Unix-Timestamp in Millisekunden für den Beginn des Abfragezeitraums (optional, Standard: 1.1.2000)\n"
                    + "- `endTime`: Unix-Timestamp in Millisekunden für das Ende des Abfragezeitraums (optional, Standard: aktuelles Datum)\n\n"
                    + "**Sortierung:** Die Ergebnisse werden nach Symbol (aufsteigend) und Datum (absteigend) sortiert zurückgegeben.\n\n"
                    + "**Verwendungsbeispiel:**\n"
                    + "```json\n"
                    + "POST /api/swing-trades/indicators?startTime=1704067200000&endTime=1735689600000\n"
                    + "Body: [\"AAPL\", \"MSFT\", \"TSLA\"]\n"
                    + "```\n\n"
                    + "**Response-Struktur:** Jedes IndicatorDto-Objekt enthält alle verfügbaren Indikatoren für ein Symbol und Datum. "
                    + "Felder können `null` sein, wenn für den jeweiligen Indikator keine Daten verfügbar sind.")
    @ApiResponse(responseCode = "200", description = "Erfolgreiche Abfrage - Liste von Indikator-Objekten mit allen technischen Kennzahlen", content = @Content(schema = @Schema(implementation = IndicatorDto.class)))
    @ApiResponse(responseCode = "400", description = "Ungültige Anfrage - z.B. leere Symbol-Liste oder ungültige Zeitstempel")
    public ResponseEntity<List<IndicatorDto>> getIndicators(@Parameter(description = "Liste von Aktiensymbolen oder Codes (z.B. AAPL, MSFT, TSLA oder interne ISIN-Codes). "
                    + "Die Codes werden automatisch in die entsprechenden Handelssymbole aufgelöst.", required = true, example = "[\"AAPL\", \"MSFT\", \"TSLA\"]")
    @RequestBody
    List<String> codes,

                                                            @Parameter(description = "Startzeitpunkt für die Abfrage als Unix-Timestamp in Millisekunden. "
                                                                            + "Wenn nicht angegeben, werden Daten ab dem 1.1.2000 abgerufen.", required = false, example = "1704067200000")
                                                            @RequestParam(required = false)
                                                            Long startTime,

                                                            @Parameter(description = "Endzeitpunkt für die Abfrage als Unix-Timestamp in Millisekunden. "
                                                                            + "Wenn nicht angegeben, wird das aktuelle Datum verwendet.", required = false, example = "1735689600000")
                                                            @RequestParam(required = false)
                                                            Long endTime)
    {
        return ResponseEntity.ok(indicatorDtoService.getIndicatorsFromDB(codes, startTime, endTime, true));
    }


    @GetMapping("/health")
    @Operation(summary = "Health Check", description = "Prüft, ob der Service läuft.")
    public ResponseEntity<Map<String, Object>> health()
    {
        return ResponseEntity.ok(Map.of("status", "UP", "timestamp", LocalDateTime.now().toString()));
    }


    @GetMapping("/{symbol}/analyze")
    @Operation(summary = "Detaillierte algorithmische Analyse erstellen", description = "Erstellt einen technischen Analyse-Report basierend auf der TradingStrategyAnalyzer-Logik. "
                    + "Die Analyse umfasst:\n"
                    + "- **Swing Trading Score**: Bewertung für kurzfristige Trends (RSI, Bollinger Bänder)\n"
                    + "- **Momentum Score**: Trendstärke und relative Stärke\n"
                    + "- **Gesamtempfehlung**: KAUFEN, HALTEN oder VERKAUFEN mit Konfidenzwert")
    @ApiResponse(responseCode = "200", description = "Erfolgreiche Analyse", content = @Content(schema = @Schema(implementation = TradingStrategyAnalyzer.StrategyAnalysis.class)))
    @ApiResponse(responseCode = "404", description = "Symbol nicht gefunden oder keine Daten verfügbar")
    public ResponseEntity<TradingStrategyAnalyzer.StrategyAnalysis> analyzeReport(@Parameter(description = "Symbol der Aktie (z.B. US0378331005)", required = true)
    @PathVariable
    String symbol,
                                                                                  @Parameter(description = "Optionaler Endzeitpunkt (Timestamp in ms)", required = false)
                                                                                  @RequestParam(required = false)
                                                                                  Long endTime)
    {
        long endTimestamp = (endTime != null) ? endTime : System.currentTimeMillis();

        // 1. Indikatoren abrufen (in Originalwährung für präzise Analyse)
        List<IndicatorDto> indicators = indicatorDtoService.getIndicatorsFromDB(List.of(symbol),
                                                                                null,
                                                                                endTimestamp,
                                                                                false);

        if (indicators == null || indicators.isEmpty())
        { return ResponseEntity.notFound().build(); }

        // 2. Aktuellen Preis abrufen
        long dayCounter = DayCounter.get(endTimestamp);
        List<DailyPrice> prices = marketDataService.getMarketData(symbol, dayCounter);

        if (prices == null || prices.isEmpty())
        { return ResponseEntity.notFound().build(); }

        DailyPrice latestPrice = prices.get(0);

        // 3. Analyse durchführen
        TradingStrategyAnalyzer.StrategyAnalysis analysis = TradingStrategyAnalyzer.analyzeStock(indicators,
                                                                                                 latestPrice.getAdjClose());

        return ResponseEntity.ok(analysis);
    }
}
