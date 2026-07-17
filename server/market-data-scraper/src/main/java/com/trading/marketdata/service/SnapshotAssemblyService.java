package com.trading.marketdata.service;

import com.trading.marketdata.model.AuctionData;
import com.trading.marketdata.model.DataQuality;
import com.trading.marketdata.model.DerivedMetrics;
import com.trading.marketdata.model.MarketSnapshot;
import com.trading.marketdata.model.NewsItem;
import com.trading.marketdata.model.OptionsData;
import com.trading.marketdata.model.QuoteData;
import com.trading.marketdata.model.ShortData;
import com.trading.marketdata.persistence.SnapshotPersistenceService;
import com.trading.marketdata.news.model.NewsContext;
import com.trading.marketdata.news.service.NewsHistoryService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Assembles and persists one full market snapshot for a ticker. Extracted verbatim from the
 * REST controller so that data COLLECTION no longer depends on data CONSUMPTION: the
 * ScheduledSnapshotJob calls this on its own clock, the REST endpoints call it on demand,
 * and both produce identical snapshots through identical code. Before the extraction,
 * MySQL history (volume curve, OI warm-up source, delta references) only grew when a
 * consumer happened to poll — the request/response coupling the Book rebuild removed from
 * the IBKR side, one level up.
 */
@Service
public class SnapshotAssemblyService {

    private final QuoteService quoteService;
    private final OptionsService optionsService;
    private final ShortInterestService shortInterestService;
    private final NewsService newsService;
    private final AuctionService auctionService;
    private final SnapshotQualityService qualityService;
    private final DerivedMetricsService derivedMetricsService;
    private final IntradayVolumeService intradayVolumeService;
    private final SnapshotPersistenceService persistenceService;
    private final MarketStateService marketStateService;
    private final NewsHistoryService newsHistoryService;

    // Virtual thread executor for parallel scraping (moved with the method)
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SnapshotAssemblyService(QuoteService quoteService,
                                   OptionsService optionsService,
                                   ShortInterestService shortInterestService,
                                   NewsService newsService,
                                   AuctionService auctionService,
                                   SnapshotQualityService qualityService,
                                   DerivedMetricsService derivedMetricsService,
                                   IntradayVolumeService intradayVolumeService,
                                   SnapshotPersistenceService persistenceService,
                                   MarketStateService marketStateService,
                                   NewsHistoryService newsHistoryService) {
        this.quoteService = quoteService;
        this.optionsService = optionsService;
        this.shortInterestService = shortInterestService;
        this.newsService = newsService;
        this.auctionService = auctionService;
        this.qualityService = qualityService;
        this.derivedMetricsService = derivedMetricsService;
        this.intradayVolumeService = intradayVolumeService;
        this.persistenceService = persistenceService;
        this.marketStateService = marketStateService;
        this.newsHistoryService = newsHistoryService;
    }

    public MarketSnapshot build(String ticker) {
        // Book symbols resolve quote/options/auction synchronously from the in-memory Book
        // (no IBKR request on the read path); the futures still parallelize the scraper-based
        // sources (shorts, news) and the fallback paths of non-Book tickers.
        CompletableFuture<QuoteData> quoteFuture =
                CompletableFuture.supplyAsync(() -> quoteService.getQuote(ticker), executor);
        CompletableFuture<OptionsData> optionsFuture =
                CompletableFuture.supplyAsync(() -> optionsService.getOptions(ticker), executor);
        CompletableFuture<ShortData> shortFuture =
                CompletableFuture.supplyAsync(() -> shortInterestService.getShortData(ticker), executor);
        CompletableFuture<List<NewsItem>> newsFuture =
                CompletableFuture.supplyAsync(() -> newsService.getNews(ticker, 10), executor);
        CompletableFuture<AuctionData> auctionFuture =
                CompletableFuture.supplyAsync(() -> auctionService.getAuctionData(ticker, false), executor);

        CompletableFuture.allOf(quoteFuture, optionsFuture, shortFuture, newsFuture, auctionFuture).join();

        QuoteData quote = quoteFuture.join();
        OptionsData options = optionsFuture.join();
        ShortData shortData = shortFuture.join();

        // Quality is read AFTER the sources so it describes the ages the response actually
        // carries. Null for non-Book tickers (scraper-served, no timestamp pairs).
        DataQuality quality = qualityService.forTicker(ticker);

        // Derived features: previous persisted snapshot (if any) supplies the delta reference.
        // findPrevious() is intentionally called BEFORE persisting this snapshot. Stale-flagged
        // fields contribute no deltas (see DerivedMetricsService).
        String marketState = marketStateService.getMarketState().name();
        DerivedMetrics derived = derivedMetricsService.compute(
                quote, options, shortData, persistenceService.findPrevious(ticker).orElse(null), quality,
                intradayVolumeService.expectedShareNow(ticker), marketState);

        List<NewsItem> currentNews = newsFuture.join();
        NewsContext newsContext = newsHistoryService.ingestAndBuild(ticker, currentNews);

        MarketSnapshot snapshot = new MarketSnapshot(
                ticker,
                Instant.now(),
                marketState,
                quote,
                options,
                shortData,
                currentNews,
                newsContext,
                derived,
                auctionFuture.join(),
                quality
        );

        persistenceService.persist(snapshot); // @Async, never blocks or fails the caller
        return snapshot;
    }
}
