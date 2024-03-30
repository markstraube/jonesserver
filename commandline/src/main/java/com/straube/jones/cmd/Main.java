package com.straube.jones.cmd;


import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import com.straube.jones.cmd.currencies.EuroRates;
import com.straube.jones.cmd.db.OnVistaModel;
import com.straube.jones.cmd.db.StocksModel;
import com.straube.jones.cmd.onVista.OnVistaCollector;
import com.straube.jones.cmd.onVista.StocksParser;

public class Main
{
    public static void main(final String[] args)
        throws SQLException,
        IOException
    {
        final String dataRoot = System.getProperty("data.root", "./data");
        final String command = System.getProperty("command", "eurorates");
        final String createModel = System.getProperty("createModel", "false");
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
            Map<String, Double> rates = new HashMap<>();
            euroRates.load(rates);
            break;
        default:
            System.out.println("Usage: -Ddata.root=./data -Dcommand=[onVista | stocks | eurorates] -DcreateModel=[false | true]");

        }
        System.out.println("Done - leaving program");
    }
}
