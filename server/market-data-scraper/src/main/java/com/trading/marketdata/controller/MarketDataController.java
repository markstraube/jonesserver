package com.trading.marketdata.controller;

import com.trading.marketdata.model.AuctionData;
import com.trading.marketdata.model.DerivedMetrics;
import com.trading.marketdata.model.MarketSnapshot;
import com.trading.marketdata.model.NewsItem;
import com.trading.marketdata.model.OptionsData;
import com.trading.marketdata.model.QuoteData;
import com.trading.marketdata.model.ShortData;
import com.trading.marketdata.persistence.SnapshotPersistenceService;
import com.trading.marketdata.service.AuctionService;
import com.trading.marketdata.service.DerivedMetricsService;
import com.trading.marketdata.service.MarketStateService;
import com.trading.marketdata.service.NewsService;
import com.trading.marketdata.service.OptionsService;
import com.trading.marketdata.service.QuoteService;
import com.trading.marketdata.service.ShortInterestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Market Data", description = "Intraday market data for active trading")
public class MarketDataController {

    private static final Logger log = LoggerFactory.getLogger(MarketDataController.class);

    private final QuoteService quoteService;
    private final OptionsService optionsService;
    private final ShortInterestService shortInterestService;
    private final NewsService newsService;
    private final DerivedMetricsService derivedMetricsService;
    private final SnapshotPersistenceService persistenceService;
    private final MarketStateService marketStateService;
    private final AuctionService auctionService;

    // Virtual thread executor for parallel scraping
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public MarketDataController(QuoteService quoteService,
                                OptionsService optionsService,
                                ShortInterestService shortInterestService,
                                NewsService newsService,
                                DerivedMetricsService derivedMetricsService,
                                SnapshotPersistenceService persistenceService,
                                MarketStateService marketStateService,
                                AuctionService auctionService) {
        this.quoteService = quoteService;
        this.optionsService = optionsService;
        this.shortInterestService = shortInterestService;
        this.newsService = newsService;
        this.derivedMetricsService = derivedMetricsService;
        this.persistenceService = persistenceService;
        this.marketStateService = marketStateService;
        this.auctionService = auctionService;
    }

    @GetMapping("/quote/{ticker}")
    @Operation(summary = "Get current quote", description = "Fetches price, volume, VWAP estimate and intraday range for the given ticker.")
    public QuoteData getQuote(
            @Parameter(description = "Ticker symbol, e.g. AAPL") @PathVariable String ticker) {
        return quoteService.getQuote(ticker.toUpperCase());
    }

    @GetMapping("/options/{ticker}")
    @Operation(summary = "Get options data", description = "Fetches Put/Call ratio, IV Rank, Max Pain and unusual activity for the given ticker.")
    public OptionsData getOptions(
            @Parameter(description = "Ticker symbol, e.g. AAPL") @PathVariable String ticker) {
        return optionsService.getOptions(ticker.toUpperCase());
    }

    @GetMapping("/short/{ticker}")
    @Operation(summary = "Get short interest data", description = "Fetches Short Float %, Days-to-Cover and Short Ratio for the given ticker.")
    public ShortData getShortData(
            @Parameter(description = "Ticker symbol, e.g. AAPL") @PathVariable String ticker) {
        return shortInterestService.getShortData(ticker.toUpperCase());
    }

    @GetMapping("/news/{ticker}")
    @Operation(summary = "Get latest news", description = "Fetches recent news headlines sorted by recency.")
    public List<NewsItem> getNews(
            @Parameter(description = "Ticker symbol, e.g. AAPL") @PathVariable String ticker,
            @Parameter(description = "Maximum number of news items to return (1-50, default 10)")
            @RequestParam(defaultValue = "10") int limit) {
        return newsService.getNews(ticker.toUpperCase(), limit);
    }

    @GetMapping("/snapshot/{ticker}")
    @Operation(summary = "Get full market snapshot", description = "Aggregates quote, options, short interest and news data for the given ticker in parallel.")
    public MarketSnapshot getSnapshot(
            @Parameter(description = "Ticker symbol, e.g. AAPL") @PathVariable String ticker) {
        String upper = ticker.toUpperCase();
        return buildSnapshot(upper);
    }

    @GetMapping("/snapshot/batch")
    @Operation(summary = "Get snapshots for multiple tickers", description = "Fetches full market snapshots for a comma-separated list of tickers in parallel.")
    public List<MarketSnapshot> getBatchSnapshots(
            @Parameter(description = "Comma-separated list of ticker symbols, e.g. AAPL,MSFT,TSLA")
            @RequestParam String tickers) {
        List<String> symbols = Arrays.stream(tickers.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toUpperCase)
                .distinct()
                .toList();

        List<CompletableFuture<MarketSnapshot>> futures = symbols.stream()
                .map(sym -> CompletableFuture.supplyAsync(() -> buildSnapshot(sym), executor))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    @GetMapping("/auction/{ticker}")
    @Operation(summary = "Get Nasdaq auction/NOII data",
            description = "Opening/closing cross imbalance via IBKR Generic Tick 225. Only "
                    + "delivers data during Nasdaq NOII dissemination windows (~09:28-09:30 and "
                    + "~15:50-16:00 ET); outside them the request is skipped unless force=true. "
                    + "Lightweight by design: poll this at 15-30s during auction windows instead "
                    + "of the full snapshot.")
    public AuctionData getAuction(
            @Parameter(description = "Ticker symbol, e.g. MU") @PathVariable String ticker,
            @Parameter(description = "Bypass the auction-window gate (for live tick-mapping verification)")
            @RequestParam(defaultValue = "false") boolean force) {
        return auctionService.getAuctionData(ticker.toUpperCase(), force);
    }

    private MarketSnapshot buildSnapshot(String ticker) {
        CompletableFuture<QuoteData> quoteFuture =
                CompletableFuture.supplyAsync(() -> quoteService.getQuote(ticker), executor);
        CompletableFuture<OptionsData> optionsFuture =
                CompletableFuture.supplyAsync(() -> optionsService.getOptions(ticker), executor);
        CompletableFuture<ShortData> shortFuture =
                CompletableFuture.supplyAsync(() -> shortInterestService.getShortData(ticker), executor);
        CompletableFuture<List<NewsItem>> newsFuture =
                CompletableFuture.supplyAsync(() -> newsService.getNews(ticker, 10), executor);
        // Self-gating: outside the two NOII windows this returns null immediately without
        // any IBKR request, so the snapshot path only pays the collection window when
        // auction data can actually exist.
        CompletableFuture<AuctionData> auctionFuture =
                CompletableFuture.supplyAsync(() -> auctionService.getAuctionData(ticker, false), executor);

        CompletableFuture.allOf(quoteFuture, optionsFuture, shortFuture, newsFuture, auctionFuture).join();

        QuoteData quote = quoteFuture.join();
        OptionsData options = optionsFuture.join();
        ShortData shortData = shortFuture.join();

        // Derived features: previous persisted snapshot (if any) supplies the delta reference.
        // findPrevious() is intentionally called BEFORE persisting this snapshot.
        DerivedMetrics derived = derivedMetricsService.compute(
                quote, options, shortData, persistenceService.findPrevious(ticker).orElse(null));

        MarketSnapshot snapshot = new MarketSnapshot(
                ticker,
                Instant.now(),
                marketStateService.getMarketState().name(),
                quote,
                options,
                shortData,
                newsFuture.join(),
                derived,
                auctionFuture.join()
        );

        persistenceService.persist(snapshot); // @Async, never blocks or fails the response
        return snapshot;
    }
}
