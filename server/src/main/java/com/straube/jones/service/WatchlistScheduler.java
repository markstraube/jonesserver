package com.straube.jones.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.straube.jones.dataprovider.userprefs.UserPrefsRepo;
import com.straube.jones.model.User;
import com.straube.jones.trader.collectors.SwingTradeQueryService;
import com.straube.jones.trader.dto.SwingTradeOverviewDto;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.SpringApplication;
import com.straube.jones.StocksApplication;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WatchlistScheduler
{

    private final SwingTradeQueryService queryService;
    private final ObjectMapper objectMapper;

    public WatchlistScheduler(SwingTradeQueryService queryService, ObjectMapper objectMapper)
    {
        this.queryService = queryService;
        this.objectMapper = objectMapper;
    }


    //@Scheduled(cron = "${scheduler.watchlist.cron}", zone = "${scheduler.watchlist.zone}")
    public void saveWatchlistIsins()
    {
        try
        {
            // Get all watchlist items (no filters)
            List<SwingTradeOverviewDto> watchlist = queryService.getWatchlist(null, null, null);

            // Extract ISINs
            List<String> isins = watchlist.stream()
                                          .map(SwingTradeOverviewDto::getIsin)
                                          .collect(Collectors.toList());

            // Convert to JSON
            String json = objectMapper.writeValueAsString(isins);

            // Save to user prefs
            UserPrefsRepo.savePrefs(null,"data-X", json);

            System.out.println("Saved " + isins.size() + " ISINs to data-X user preference.");

        }
        catch (IOException e)
        {
            System.err.println("Failed to save watchlist ISINs: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public static void main(String[] args)
    {
        System.out.println("Starting WatchlistScheduler manual execution...");
        ConfigurableApplicationContext context = SpringApplication.run(StocksApplication.class, args);

        try
        {
            WatchlistScheduler scheduler = context.getBean(WatchlistScheduler.class);
            scheduler.saveWatchlistIsins();
            System.out.println("Manual execution completed successfully.");
        }
        catch (Exception e)
        {
            System.err.println("Manual execution failed: " + e.getMessage());
            e.printStackTrace();
        }
        finally
        {
            context.close();
            System.exit(0);
        }
    }
}
