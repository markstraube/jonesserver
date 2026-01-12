package com.straube.jones.controller;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
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
import com.straube.jones.trader.Updater;
import com.straube.jones.trader.collectors.SwingTradeQueryService;
import com.straube.jones.trader.collectors.TradingIndicatorService;
import com.straube.jones.trader.dto.BuyPriceTargetsDto;
import com.straube.jones.trader.dto.DailyPrice;
import com.straube.jones.trader.dto.HistoricalAnalysisDto;
import com.straube.jones.trader.dto.IndicatorDto;
import com.straube.jones.trader.dto.RSI30PredictionDto;
import com.straube.jones.trader.dto.RatingDto;
import com.straube.jones.trader.dto.SwingTradeDetailDto;
import com.straube.jones.trader.dto.SwingTradeOverviewDto;
import com.straube.jones.trader.indicators.MomentumIndicators;
import com.straube.jones.trader.indicators.RSIPrediction;
import com.straube.jones.trader.indicators.RatingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/swing-trades")
@Tag(name = "Swing Trading API", description = "Swing-Trading Watchlist und Detailanalyse")
public class SwingTradeController
{

    private static final String DATA_ROOT_FOLDER = System.getProperty("data.root", "/opt/tomcat/data");
    private final SwingTradeQueryService queryService;
    private final TradingIndicatorService indicatorService;
    private final RatingService ratingService;
    private final IndicatorService indicatorDtoService;
    private final MarketDataService marketDataService;
    private final Updater updater;
    private final MomentumIndicators momentumIndicators;

    public SwingTradeController(SwingTradeQueryService queryService,
                                TradingIndicatorService indicatorService,
                                RatingService ratingService,
                                IndicatorService indicatorDtoService,
                                MarketDataService marketDataService,
                                Updater updater,
                                MomentumIndicators momentumIndicators)
    {
        this.queryService = queryService;
        this.indicatorService = indicatorService;
        this.ratingService = ratingService;
        this.indicatorDtoService = indicatorDtoService;
        this.marketDataService = marketDataService;
        this.updater = updater;
        this.momentumIndicators = momentumIndicators;
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

            @Parameter(
                description = "Endzeitpunkt für die Abfrage als Unix-Timestamp in Millisekunden. " +
                             "Wenn nicht angegeben, wird das aktuelle Datum verwendet.",
                required = false,
                example = "1735689600000"
            )
            @RequestParam(required = false) Long endTime)
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
    public ResponseEntity<TradingStrategyAnalyzer.StrategyAnalysis> analyzeReport(
            @Parameter(description = "Symbol der Aktie (z.B. US0378331005)", required = true) @PathVariable String symbol,
            @Parameter(description = "Optionaler Endzeitpunkt (Timestamp in ms)", required = false) @RequestParam(required = false) Long endTime)
    {
        long endTimestamp = (endTime != null) ? endTime : System.currentTimeMillis();

        // 1. Indikatoren abrufen (in Originalwährung für präzise Analyse)
        List<IndicatorDto> indicators = indicatorDtoService.getIndicatorsFromDB(List.of(symbol), null, endTimestamp, false);

        if (indicators == null || indicators.isEmpty())
        {
            return ResponseEntity.notFound().build();
        }

        // 2. Aktuellen Preis abrufen
        long dayCounter = DayCounter.get(endTimestamp);
        List<DailyPrice> prices = marketDataService.getMarketData(symbol, dayCounter);

        if (prices == null || prices.isEmpty())
        {
            return ResponseEntity.notFound().build();
        }

        DailyPrice latestPrice = prices.get(0);

        // 3. Analyse durchführen
        TradingStrategyAnalyzer.StrategyAnalysis analysis = TradingStrategyAnalyzer.analyzeStock(indicators, latestPrice.getAdjClose());

        return ResponseEntity.ok(analysis);
    }


    @GetMapping("/prediction/rsi30")
    @Operation(summary = "RSI30 Vorhersage abrufen", description = "Analysiert die Wahrscheinlichkeit, dass der RSI-Wert einer Aktie innerhalb der nächsten 30 Tage unter 30 fällt. "
                    + "Die Analyse basiert auf historischen Preisdaten der letzten 60 Tage und technischen Indikatoren. "
                    + "Die Methode liefert:\n"
                    + "- **Wahrscheinlichkeitseinschätzung**: Prozentuale Wahrscheinlichkeit für RSI < 30\n"
                    + "- **Analysefaktoren**: Detaillierte Aufschlüsselung der berücksichtigten Faktoren (RSI-Abstand, MACD, Bollinger Bands, Volatilität, Verlust-Serien, Trends)\n"
                    + "- **Historische Analyse**: Volatilität, Drawdowns, Verlusttage der letzten 30 Tage\n"
                    + "- **Kaufpreis-Ziele**: Geschätzte Zielpreise für verschiedene Zeithorizonte (5, 10, 20, 30 Tage)\n\n"
                    + "**Verwendung:**\n"
                    + "- `end_time` definiert den Analysezeitpunkt (in der Regel aktueller Zeitpunkt oder ein Datum in der Vergangenheit)\n"
                    + "- Das System ermittelt automatisch den optimalen Start-Zeitpunkt für die Datenabfrage (60 Handelstage zurück)\n"
                    + "- Mindestens 35 Handelstage an Preisdaten werden für eine zuverlässige Analyse benötigt")
    @ApiResponse(responseCode = "200", description = "RSI30 Vorhersage erfolgreich erstellt", content = @Content(schema = @Schema(implementation = RSI30PredictionDto.class)))
    @ApiResponse(responseCode = "404", description = "Symbol nicht gefunden oder nicht genügend historische Daten verfügbar")
    @ApiResponse(responseCode = "400", description = "Ungültige Parameter")
    public ResponseEntity<RSI30PredictionDto> getRSI30Prediction(@Parameter(description = "Aktiensymbol (Yahoo Finance Identifier, z.B. TSLA, AAPL, MSFT)", required = true, example = "TSLA")
    @RequestParam
    String symbol,

                                                                 @Parameter(description = "Endzeitpunkt für die Analyse als Java Timestamp in Millisekunden. "
                                                                                 + "Die Analyse verwendet Daten bis zu diesem Zeitpunkt. "
                                                                                 + "Wenn nicht angegeben, wird der aktuelle Zeitpunkt verwendet.", required = false, example = "1735689600000")
                                                                 @RequestParam(required = false)
                                                                 Long endTime)
    {
        // 1. Bestimme den Endzeitpunkt
        long endDayCounter = (endTime != null) ? DayCounter.get(endTime) : DayCounter.now();

        // 2. Erstelle technischen Report für den Analysezeitpunkt
        // Hinweis: Der Report benötigt mindestens 60 Handelstage an Daten für zuverlässige Indikator-Berechnungen
        TradingIndicatorService.Report report = indicatorService.getReport(symbol, endDayCounter);

        if (report == null)
        { return ResponseEntity.notFound().build(); }

        // 3. Hole historische Preisdaten
        List<DailyPrice> prices = marketDataService.getMarketData(symbol, endDayCounter);

        if (prices == null || prices.size() < 35)
        {
            // Nicht genug Daten für eine verlässliche Analyse
            return ResponseEntity.notFound().build();
        }

        // 4. Führe RSI30-Vorhersage durch
        RSIPrediction.RSI30Probability probability = RSIPrediction.estimateRSI30Probability(report, prices);
        RSIPrediction.BuyPriceTargets buyTargets = RSIPrediction.calculateBuyPriceTargets(report, prices);

        // 5. Konvertiere in DTO
        RSI30PredictionDto predictionDto = new RSI30PredictionDto();
        predictionDto.setSymbol(symbol);
        predictionDto.setTimestamp(endTime != null ? endTime : System.currentTimeMillis());

        // Extrahiere aktuelle Werte aus dem Report (Mid-Term Analyse)
        TradingIndicatorService.Analysis midTermAnalysis = null;
        for (TradingIndicatorService.ReportEntry entry : report.getAnalyses())
        {
            if (entry.getName().contains("Mid Term"))
            {
                midTermAnalysis = entry.getResult();
                break;
            }
        }

        if (midTermAnalysis != null)
        {
            predictionDto.setCurrentPrice(midTermAnalysis.getCurrentPrice());
            predictionDto.setCurrentRsi(midTermAnalysis.getRsi());
        }

        // Setze Wahrscheinlichkeits-Daten
        predictionDto.setProbabilityPercent(probability.getProbabilityPercent());
        predictionDto.setAssessment(probability.getAssessment());
        predictionDto.setEstimatedDaysToRSI30(probability.getDaysToReachRSI30Estimate());
        predictionDto.setFactors(probability.getFactors());

        // Konvertiere historische Analyse
        if (probability.getHistoricalAnalysis() != null)
        {
            HistoricalAnalysisDto histDto = new HistoricalAnalysisDto();
            RSIPrediction.HistoricalAnalysis hist = probability.getHistoricalAnalysis();
            histDto.setAvgDailyVolatility(hist.getAvgDailyVolatility());
            histDto.setConsecutiveLossDays(hist.getConsecutiveLossDays());
            histDto.setAvgLossOnDownDays(hist.getAvgLossOnDownDays());
            histDto.setMaxDrawdown(hist.getMaxDrawdown());
            histDto.setAvgGainOnUpDays(hist.getAvgGainOnUpDays());
            histDto.setTotalDownDays(hist.getTotalDownDays());
            histDto.setPriceChange30Days(hist.getPriceChange30Days());
            predictionDto.setHistoricalAnalysis(histDto);
        }

        // Konvertiere Kaufpreis-Ziele
        BuyPriceTargetsDto targetsDto = new BuyPriceTargetsDto();
        targetsDto.setCurrentPrice(buyTargets.getCurrentPrice());
        targetsDto.setTarget5Days(buyTargets.getTarget5Days());
        targetsDto.setTarget10Days(buyTargets.getTarget10Days());
        targetsDto.setTarget20Days(buyTargets.getTarget20Days());
        targetsDto.setTarget30Days(buyTargets.getTarget30Days());
        targetsDto.setRequiredDailyDecline(buyTargets.getRequiredDailyDecline());
        targetsDto.setVolatilityAssessment(buyTargets.getVolatilityAssessment());
        predictionDto.setBuyPriceTargets(targetsDto);

        return ResponseEntity.ok(predictionDto);
    }


    @PostMapping("/update-all")
    @Operation(summary = "Update Everything", description = "Triggers async update of prices, indicators and ratings.")
    public ResponseEntity<String> updateAll()
    {
        //new Thread(updater::updateAllJob).start();
        new Thread(momentumIndicators::updateAllJob).start();
        return ResponseEntity.accepted().body("Update job started");
    }

}
