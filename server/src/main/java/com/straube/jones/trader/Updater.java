package com.straube.jones.trader;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.straube.jones.dataprovider.yahoo.YahooPriceDownloader;
import com.straube.jones.dataprovider.yahoo.YahooPriceImporter;
import com.straube.jones.db.DayCounter;
import com.straube.jones.model.Company;
import com.straube.jones.service.CompanyService;
import com.straube.jones.service.MarketDataService;
import com.straube.jones.trader.collectors.IndicatorCollector;
import com.straube.jones.trader.collectors.TradingIndicatorService;
import com.straube.jones.trader.indicators.MomentumIndicators;

@Component
public class Updater
{

    private final CompanyService companyService;
    private final MarketDataService marketDataService;
    private final TradingIndicatorService indicatorService;
    private final IndicatorCollector indicatorCollector;
    private final MomentumIndicators momentumIndicators;

    public Updater(CompanyService companyService,
                   MarketDataService marketDataService,
                   TradingIndicatorService indicatorService,
                   IndicatorCollector indicatorCollector,
                   MomentumIndicators momentumIndicators)
    {
        this.companyService = companyService;
        this.marketDataService = marketDataService;
        this.indicatorService = indicatorService;
        this.indicatorCollector = indicatorCollector;
        this.momentumIndicators = momentumIndicators;
    }


    @Scheduled(cron = "${updater.schedule.cron:0 0 6 * * ?}")
    public void updateAllJob()
    {
        try
        {
            // 1. List companies
            List<Company> companies = companyService.getAllCompanies();
            Map<String, Long> maxDays = marketDataService.getMaxDayCounterPerSymbol();

            // Partition Logic
            int numThreads = 4;
            List<List<Company>> partitions = new ArrayList<>();
            int chunkSize = (int)Math.ceil((double)companies.size() / numThreads);

            for (int i = 0; i < companies.size(); i += chunkSize)
            {
                int end = Math.min(i + chunkSize, companies.size());
                partitions.add(companies.subList(i, end));
            }

            ExecutorService executor = Executors.newFixedThreadPool(partitions.size());
            List<Future< ? >> futures = new ArrayList<>();

            for (int i = 0; i < partitions.size(); i++ )
            {
                final List<Company> partition = partitions.get(i);
                final int threadIndex = i;
                futures.add(executor.submit(() -> processBatch(partition, maxDays, threadIndex)));
            }

            // Wait for all threads to finish
            for (Future< ? > f : futures)
            {
                try
                {
                    f.get();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }

            executor.shutdown();

            // 3. updateRatings
            indicatorService.updateRatings();

            // 4. updateIndicators
            indicatorCollector.updateIndicators();

            // 5. update Momentum Indicators
            momentumIndicators.update();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }


    private void processBatch(List<Company> companies, Map<String, Long> maxDays, int threadIndex)
    {
        // Unique folder for this thread
        String threadFolder = YahooPriceDownloader.DAILY_PRICE_FOLDER + "/thread-" + threadIndex;
        long today = DayCounter.now();

        for (Company company : companies)
        {
            String symbol = company.getSymbol();
            String isin = company.getIsin();
            if (symbol == null || isin == null)
            {
                continue;
            }

            Long maxDay = maxDays.get(symbol);
            int daysBack;

            if (maxDay != null)
            {
                long diff = today - maxDay;
                // Ensure we fetch at least 1 day if behind, or enough to cover gaps
                daysBack = (int)diff;
                if (daysBack <= 0)
                {
                    continue;
                }
            }
            else
            {
                // No data, fetch 2 years
                daysBack = 365 * 2;
            }

            try
            {
                YahooPriceDownloader.fetchPrices(daysBack, symbol, isin, threadFolder);
                YahooPriceImporter.uploadPriceData(threadFolder);
            }
            catch (Exception e)
            {
                // Log error but continue
                System.err.println("Error updating " + symbol
                                + " in thread "
                                + threadIndex
                                + ": "
                                + e.getMessage());
                e.printStackTrace();
            }
        }

        // Final cleanup of the folder (optional, if files are deleted by importer this should be empty)
        // File dir = new File(threadFolder);
        // if(dir.exists() && dir.isDirectory() && dir.list().length == 0) dir.delete();
    }
}
