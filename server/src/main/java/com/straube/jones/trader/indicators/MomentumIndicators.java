package com.straube.jones.trader.indicators;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.straube.jones.db.DayCounter;
import com.straube.jones.model.Company;
import com.straube.jones.service.CompanyService;
import com.straube.jones.service.MarketDataService;
import com.straube.jones.trader.dto.ADXResult;
import com.straube.jones.trader.dto.DailyPrice;

/**
 * Indikatoren und Strategien für Momentum/Trend-Aktien
 * Optimiert für bullische Aktien wie RKLB, die lange im "überkauften" Bereich bleiben
 */
@Component
public class MomentumIndicators
{
    private final MarketDataService marketDataService;
    private final List<DailyPrice> benchmarkPrices;
    private final CompanyService companyService;

    public MomentumIndicators(MarketDataService marketDataService, CompanyService companyService)
    {
        this.marketDataService = marketDataService;
        this.companyService = companyService;
        this.benchmarkPrices = marketDataService.getMarketData("^IXIC");
    }


    public void update()
    {
        List<Company> companies = this.companyService.getAllCompanies();
        Map<String, Long> maxDays = marketDataService.getMaxDayCounterPerSymbolFromIndicators();

        int numThreads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        int portionSize = (int) Math.ceil((double) companies.size() / numThreads);

        for (int i = 0; i < companies.size(); i += portionSize)
        {
            int end = Math.min(companies.size(), i + portionSize);
            List<Company> subList = companies.subList(i, end);
            executor.submit(() -> processBatch(subList, maxDays));
        }

        executor.shutdown();
        try
        {
            executor.awaitTermination(2, TimeUnit.HOURS);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }


    private void processBatch(List<Company> companies, Map<String, Long> maxDays)
    {
        List<Object[]> batchArgs = new ArrayList<>();
        // Update database in batches
        final int BATCH_SIZE = 500;

        for (Company company : companies)
        {
            Long maxDay = maxDays.getOrDefault(company.getSymbol(), DayCounter.now());
            long now = DayCounter.now();
            
            if (maxDay > now)
            {
                continue;
            }

            // Fetch data once per company
            List<DailyPrice> allPrices = marketDataService.getMarketData(company.getSymbol(), now);

            for (long dayCounter = maxDay; dayCounter <= now; dayCounter++)
            {
                List<DailyPrice> prices = slicePrices(allPrices, dayCounter);
                if (prices == null || prices.size() < 65) // wegen RSL 63 Tage
                {
                    continue;
                }

                ADXResult adx = ADXcalculator.calculateADX(prices, 14);
                Double roc = ROCcalculator.calculateROC(prices, 12);
                Double rsl = RSLcalculator.calculateRSLevy(prices, benchmarkPrices, 63);

                // OBVcalculator.OBVResult obv = OBVcalculator.calculateOBV(prices);
                // StochasticResult stochastic = StochastikOszillatorCalculator.calculateStochastikOszillator(prices);

                if (adx != null)
                {
                    batchArgs.add(new Object[] {
                        adx.getAdx(),
                        adx.getPlusDI(),
                        adx.getMinusDI(),
                        roc,
                        rsl,
                        company.getSymbol(),
                        dayCounter
                    });
                }

                if (batchArgs.size() >= BATCH_SIZE)
                {
                    marketDataService.batchUpdateMomentumIndicators(batchArgs);
                    batchArgs.clear();
                }
            }
        }

        // Save remaining
        if (!batchArgs.isEmpty())
        {
            marketDataService.batchUpdateMomentumIndicators(batchArgs);
        }
    }


    private List<DailyPrice> slicePrices(List<DailyPrice> allPrices, long dayCounter)
    {
        for (int i = 0; i < allPrices.size(); i++)
        {
            // Assuming allPrices is ordered by Date DESC
            if (DayCounter.get(allPrices.get(i).getDate()) <= dayCounter)
            {
                return allPrices.subList(i, allPrices.size());
            }
        }
        return null;
    }
}
