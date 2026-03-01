package com.straube.jones.cmd;


import java.io.File;
import java.nio.file.Paths;
import java.time.Instant;

import com.straube.jones.cmd.currencies.CurrencyDB;
import com.straube.jones.cmd.currencies.EuroRates;
import com.straube.jones.cmd.db.OnVistaModel;
import com.straube.jones.cmd.db.StockCounterDB;
import com.straube.jones.cmd.onVista.OnVistaCollector;
import com.straube.jones.cmd.onVista.StocksLoader;
import com.straube.jones.cmd.strategies.Strategy001;
import com.straube.jones.cmd.yahoo.SymbolResolver;
import com.straube.jones.cmd.yahoo.YahooPriceDownloader;
import com.straube.jones.cmd.yahoo.YahooPriceImporter;

public class Main
{
    public static void main(final String[] args)
        throws Exception
    {
        final String dataRoot = System.getProperty("data.root", "./data");
        final String webDataRoot = System.getProperty("web.data.root", "./data");
        final String command = System.getProperty("command", "onVista");
        final String createModel = System.getProperty("createModel", "false");
        final String dateString = System.getProperty("dateString", "2024-03-31");

        switch (command)
        {
        case "onVista":
            if ("true".equals(createModel))
            {
                OnVistaModel.create();
            }
            OnVistaCollector onVista = new OnVistaCollector(dataRoot);
            File targetFolder = onVista.getJsonFromFinder();
            onVista.updateFinderJsonToOnVistaTable(targetFolder);

            StocksLoader.generateAndSaveCharts(Instant.now().toEpochMilli() - 1000L * 60 * 60 * 24 * 365,
                                               Instant.now().toEpochMilli(),
                                               new String[0],
                                               64,
                                               48,
                                               webDataRoot + "/1Y");
            StocksLoader.generateAndSaveCharts(Instant.now().toEpochMilli() - 1000L * 60 * 60 * 24 * 28,
                                               Instant.now().toEpochMilli(),
                                               new String[0],
                                               64,
                                               48,
                                               webDataRoot + "/1M");
            StocksLoader.generateAndSaveCharts(Instant.now().toEpochMilli() - 1000L * 60 * 60 * 24 * 365,
                                               Instant.now().toEpochMilli(),
                                               new String[0],
                                               400,
                                               300,
                                               webDataRoot + "/1Y");
            StocksLoader.generateAndSaveCharts(Instant.now().toEpochMilli() - 1000L * 60 * 60 * 24 * 28,
                                               Instant.now().toEpochMilli(),
                                               new String[0],
                                               400,
                                               300,
                                               webDataRoot + "/1M");

            break;
        case "yahoo":
            int daysBack = 5;
            String yahooFolder = dataRoot + "/yahoo/daily";
            System.out.println("Starting Yahoo Price download to: " + yahooFolder
                            + " for the past "
                            + daysBack
                            + " days.");
            YahooPriceDownloader.fetchPrices(daysBack, yahooFolder);
            System.out.println("Download finished.");
            System.out.println("Starting Yahoo Price Import from: " + yahooFolder);
            YahooPriceImporter importer = new YahooPriceImporter();
            importer.uploadPriceData(yahooFolder);
            System.out.println("Import finished.");
            System.out.println("Checking for missing yahoo symbols in OnVista Table");
            SymbolResolver.updateOnVista();
            System.out.println("Update finished.");

            break;
        case "eurorates":
            File ef = new File(dataRoot, "onVista");
            ef.mkdirs();
            EuroRates euroRates = new EuroRates(ef);
            euroRates.load();
            break;
        case "importOeNB":
            CurrencyDB.importCSV(Paths.get(dataRoot, "onVista/eurorates/OeNB.csv"));
            break;
        case "fundamentals":
            StockCounterDB.reloadAllCounter(dataRoot, dateString, true);
            break;
        case "strategy001":
            new Strategy001().execute();
            break;
        default:
            System.out.println("Usage: -Ddata.root=./data -Dcommand=[onVista | stocks | eurorates | importOeNB | fundamentals | strategy001] -DcreateModel=[false | true]");

        }
        System.out.println("Done - leaving program");
    }
}
