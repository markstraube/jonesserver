package com.straube.jones.repository;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.straube.jones.agent.StocksAgent;
import com.straube.jones.model.StockFundamentals;
import com.straube.jones.service.FundamentalsService;

/**
 * Repository für Fundamentaldaten von Aktien.
 * Speichert die Daten als JSON-Dateien, organisiert nach ISIN.
 */
public class FundamentalsRepository
{
    private static final String DATA_ROOT_FOLDER = System.getProperty("data.root",
                                                                      "/home/mark/Software/data");

    private static final String FUNDAMENTALS_ROOT_FOLDER = DATA_ROOT_FOLDER + "/fundamentals";
    static
    {
        new File(FUNDAMENTALS_ROOT_FOLDER).mkdirs();
    }
    private final String dataDirectory;
    private final ObjectMapper objectMapper;
    private final Map<String, StockFundamentals> cache;

    /**
     * Erstellt ein neues Repository mit dem Standard-Datenverzeichnis.
     */
    public FundamentalsRepository()
    {
        this(FUNDAMENTALS_ROOT_FOLDER);
    }


    /**
     * Erstellt ein neues Repository mit einem benutzerdefinierten Datenverzeichnis.
     * 
     * @param dataDirectory Pfad zum Datenverzeichnis
     */
    public FundamentalsRepository(String dataDirectory)
    {
        this.dataDirectory = dataDirectory;
        this.objectMapper = new ObjectMapper();
        this.cache = new HashMap<>();

        // Stelle sicher, dass das Datenverzeichnis existiert
        try
        {
            Files.createDirectories(Paths.get(dataDirectory));
        }
        catch (IOException e)
        {
            throw new RuntimeException("Konnte Datenverzeichnis nicht erstellen: " + dataDirectory, e);
        }
    }


    /**
     * Speichert oder aktualisiert Fundamentaldaten für eine Aktie.
     * 
     * @param fundamentals Die zu speichernden Fundamentaldaten
     * @throws IllegalArgumentException wenn ISIN null oder leer ist
     * @throws IOException bei Dateisystem-Fehlern
     */
    public void save(StockFundamentals fundamentals)
        throws IOException
    {
        if (fundamentals.getIsin() == null || fundamentals.getIsin().trim().isEmpty())
        { throw new IllegalArgumentException("ISIN darf nicht null oder leer sein"); }

        String isin = fundamentals.getIsin();
        Path filePath = getFilePath(isin);

        // Schreibe Daten in Datei
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), fundamentals);

        // Aktualisiere Cache
        cache.put(isin, fundamentals);
    }


    /**
     * Lädt Fundamentaldaten für eine bestimmte ISIN.
     * 
     * @param isin Die ISIN der Aktie
     * @return Optional mit den Fundamentaldaten oder empty wenn nicht gefunden
     * @throws IOException bei Dateisystem-Fehlern
     */
    public Optional<StockFundamentals> findByIsin(String isin)
        throws IOException
    {
        if (isin == null || isin.trim().isEmpty())
        { return Optional.empty(); }

        // Prüfe Cache zuerst
        if (cache.containsKey(isin))
        { return Optional.of(cache.get(isin)); }

        // Lade von Datei
        Path filePath = getFilePath(isin);
        File file = filePath.toFile();

        StockFundamentals fundamentals = null;
        if (!file.exists())
        {
                return Optional.empty();
        }
        else
        {
            fundamentals = objectMapper.readValue(file, StockFundamentals.class);
        }
        cache.put(isin, fundamentals);
        return Optional.of(fundamentals);
    }


    /**
     * Löscht Fundamentaldaten für eine bestimmte ISIN.
     * 
     * @param isin Die ISIN der Aktie
     * @return true wenn Daten gelöscht wurden, false wenn keine Daten existierten
     * @throws IOException bei Dateisystem-Fehlern
     */
    public boolean deleteByIsin(String isin)
        throws IOException
    {
        if (isin == null || isin.trim().isEmpty())
        { return false; }

        Path filePath = getFilePath(isin);
        File file = filePath.toFile();

        // Entferne aus Cache
        cache.remove(isin);

        // Lösche Datei
        if (file.exists())
        { return Files.deleteIfExists(filePath); }

        return false;
    }


    /**
     * Prüft, ob Fundamentaldaten für eine ISIN existieren.
     * 
     * @param isin Die ISIN der Aktie
     * @return true wenn Daten existieren, sonst false
     */
    public boolean exists(String isin)
    {
        if (isin == null || isin.trim().isEmpty())
        { return false; }

        return cache.containsKey(isin) || getFilePath(isin).toFile().exists();
    }


    /**
     * Gibt den Dateipfad für eine bestimmte ISIN zurück.
     * 
     * @param isin Die ISIN der Aktie
     * @return Pfad zur JSON-Datei
     */
    private Path getFilePath(String isin)
    {
        String fileName = isin + ".json";
        return Paths.get(dataDirectory, fileName);
    }


    /**
     * Leert den internen Cache.
     */
    public void clearCache()
    {
        cache.clear();
    }
}
