package com.straube.jones.cmd;


import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.Instant;

import com.straube.jones.cmd.currencies.CurrencyDB;
import com.straube.jones.cmd.currencies.EuroRates;
import com.straube.jones.cmd.db.OnVistaModel;
import com.straube.jones.cmd.db.StockCounterDB;
import com.straube.jones.cmd.db.StocksModel;
import com.straube.jones.cmd.onVista.OnVistaCollector;
import com.straube.jones.cmd.onVista.StocksLoader;
import com.straube.jones.cmd.onVista.StocksParser;

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
            StocksParser.insertFinderJsonToStocksTable(targetFolder.toPath());

            StocksLoader.generateAndSaveCharts(Instant.now().toEpochMilli() - 1000L * 60 * 60 * 24 * 365, Instant.now().toEpochMilli(), new String[0], 64, 48, webDataRoot + "/1Y");
            StocksLoader.generateAndSaveCharts(Instant.now().toEpochMilli() - 1000L * 60 * 60 * 24 * 28, Instant.now().toEpochMilli(), new String[0], 64, 48, webDataRoot + "/1M");
            StocksLoader.generateAndSaveCharts(Instant.now().toEpochMilli() - 1000L * 60 * 60 * 24 * 365, Instant.now().toEpochMilli(), new String[0], 400, 300, webDataRoot + "/1Y");
            StocksLoader.generateAndSaveCharts(Instant.now().toEpochMilli() - 1000L * 60 * 60 * 24 * 28, Instant.now().toEpochMilli(), new String[0], 400, 300, webDataRoot + "/1M");

            break;
        case "stocks":
            if ("true".equals(createModel))
            {
                StocksModel.create();
            }
            DirectoryStream.Filter<Path> filter = file -> {
                return file.toFile().isDirectory();
            };
            try (DirectoryStream<Path> rootPath = Files.newDirectoryStream(Paths.get(dataRoot, "onVista", "finder2"), filter))
            {
                rootPath.forEach(path -> {
                    try
                    {
                        StocksParser.insertFinderJsonToStocksTable(path);
                    }
                    catch (SQLException e)
                    {
                        e.printStackTrace();
                    }
                });
            }
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
        default:
            System.out.println("Usage: -Ddata.root=./data -Dcommand=[onVista | stocks | eurorates | importOeNB | fundamentals] -DcreateModel=[false | true]");

        }
        System.out.println("Done - leaving program");
    }
}
