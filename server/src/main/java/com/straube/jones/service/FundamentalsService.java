package com.straube.jones.service;

import java.io.IOException;
import java.util.Optional;

import com.straube.jones.model.StockFundamentals;
import com.straube.jones.repository.FundamentalsRepository;

/**
 * Service-Schicht für Fundamentaldaten von Aktien.
 * Enthält Geschäftslogik und Validierung.
 */
public class FundamentalsService
{
    private final FundamentalsRepository repository;

    /**
     * Erstellt einen neuen Service mit dem Standard-Repository.
     */
    public FundamentalsService()
    {
        this.repository = new FundamentalsRepository();
    }

    /**
     * Erstellt einen neuen Service mit einem benutzerdefinierten Repository.
     * 
     * @param repository Das zu verwendende Repository
     */
    public FundamentalsService(FundamentalsRepository repository)
    {
        this.repository = repository;
    }

    /**
     * Erstellt neue Fundamentaldaten für eine Aktie.
     * 
     * @param fundamentals Die zu erstellenden Fundamentaldaten
     * @return Die erstellten Fundamentaldaten
     * @throws IllegalArgumentException wenn ISIN null/leer ist oder Daten bereits existieren
     * @throws IOException bei Dateisystem-Fehlern
     */
    public StockFundamentals create(StockFundamentals fundamentals) throws IOException
    {
        validateFundamentals(fundamentals);

        String isin = fundamentals.getIsin();
        
        if (repository.exists(isin))
        {
            throw new IllegalArgumentException("Fundamentaldaten für ISIN " + isin + " existieren bereits");
        }

        repository.save(fundamentals);
        return fundamentals;
    }

    /**
     * Aktualisiert existierende Fundamentaldaten für eine Aktie.
     * Führt ein Merge durch: Nur nicht-leere Felder aus fundamentals überschreiben existierende Werte.
     * 
     * @param isin Die ISIN der Aktie
     * @param fundamentals Die zu mergenden Fundamentaldaten
     * @return Die aktualisierten Fundamentaldaten nach dem Merge
     * @throws IllegalArgumentException wenn ISIN nicht übereinstimmt oder Daten nicht existieren
     * @throws IOException bei Dateisystem-Fehlern
     */
    public StockFundamentals update(String isin, StockFundamentals fundamentals) throws IOException
    {
        if (isin == null || isin.trim().isEmpty())
        {
            throw new IllegalArgumentException("ISIN darf nicht null oder leer sein");
        }

        if (fundamentals.getIsin() != null && !isin.equals(fundamentals.getIsin()))
        {
            throw new IllegalArgumentException("ISIN im Pfad (" + isin + ") stimmt nicht mit ISIN in Daten (" + fundamentals.getIsin() + ") überein");
        }

        // Lade existierende Daten
        Optional<StockFundamentals> existingOpt = repository.findByIsin(isin);
        if (!existingOpt.isPresent())
        {
            throw new IllegalArgumentException("Fundamentaldaten für ISIN " + isin + " existieren nicht");
        }

        // Merge neue Daten mit existierenden Daten
        StockFundamentals existing = existingOpt.get();
        StockFundamentals merged = mergeFundamentals(existing, fundamentals);
        
        repository.save(merged);
        return merged;
    }

    /**
     * Findet Fundamentaldaten für eine bestimmte ISIN.
     * 
     * @param isin Die ISIN der Aktie
     * @return Optional mit den Fundamentaldaten oder empty wenn nicht gefunden
     * @throws IOException bei Dateisystem-Fehlern
     */
    public Optional<StockFundamentals> findByIsin(String isin) throws IOException
    {
        if (isin == null || isin.trim().isEmpty())
        {
            return Optional.empty();
        }

        return repository.findByIsin(isin);
    }

    /**
     * Löscht Fundamentaldaten für eine bestimmte ISIN.
     * 
     * @param isin Die ISIN der Aktie
     * @return true wenn Daten gelöscht wurden, false wenn keine Daten existierten
     * @throws IOException bei Dateisystem-Fehlern
     */
    public boolean delete(String isin) throws IOException
    {
        if (isin == null || isin.trim().isEmpty())
        {
            return false;
        }

        return repository.deleteByIsin(isin);
    }

    /**
     * Prüft, ob Fundamentaldaten für eine ISIN existieren.
     * 
     * @param isin Die ISIN der Aktie
     * @return true wenn Daten existieren, sonst false
     */
    public boolean exists(String isin)
    {
        return repository.exists(isin);
    }

    /**
     * Validiert Fundamentaldaten.
     * 
     * @param fundamentals Die zu validierenden Daten
     * @throws IllegalArgumentException bei ungültigen Daten
     */
    private void validateFundamentals(StockFundamentals fundamentals)
    {
        if (fundamentals == null)
        {
            throw new IllegalArgumentException("Fundamentaldaten dürfen nicht null sein");
        }

        if (fundamentals.getIsin() == null || fundamentals.getIsin().trim().isEmpty())
        {
            throw new IllegalArgumentException("ISIN darf nicht null oder leer sein");
        }

        // Weitere Validierungen können hier hinzugefügt werden
    }

    /**
     * Merged neue Fundamentaldaten mit existierenden Daten.
     * Nur nicht-null und nicht-leere Felder aus newData überschreiben existing.
     * 
     * @param existing Die existierenden Daten
     * @param newData Die neuen Daten
     * @return Die gemergten Daten
     */
    private StockFundamentals mergeFundamentals(StockFundamentals existing, StockFundamentals newData)
    {
        StockFundamentals merged = new StockFundamentals();
        
        // ISIN bleibt immer gleich
        merged.setIsin(existing.getIsin());
        
        // Merge WKN
        merged.setWkn(isNotEmpty(newData.getWkn()) ? newData.getWkn() : existing.getWkn());
        
        // Merge SYMBOL
        merged.setSymbol(isNotEmpty(newData.getSymbol()) ? newData.getSymbol() : existing.getSymbol());
        
        // Merge SYMBOL.YAHOO
        merged.setSymbolYahoo(isNotEmpty(newData.getSymbolYahoo()) ? newData.getSymbolYahoo() : existing.getSymbolYahoo());
        
        // Merge SYMBOL.GOOGLE
        merged.setSymbolGoogle(isNotEmpty(newData.getSymbolGoogle()) ? newData.getSymbolGoogle() : existing.getSymbolGoogle());
        
        // Merge CompanyBasics
        merged.setCompanyBasics(mergeCompanyBasics(existing.getCompanyBasics(), newData.getCompanyBasics()));
        
        // Merge StockData
        merged.setStockData(mergeStockData(existing.getStockData(), newData.getStockData()));
        
        return merged;
    }

    /**
     * Merged CompanyBasics Daten.
     * 
     * @param existing Existierende CompanyBasics (kann null sein)
     * @param newData Neue CompanyBasics (kann null sein)
     * @return Gemergte CompanyBasics
     */
    private com.straube.jones.model.CompanyBasics mergeCompanyBasics(
            com.straube.jones.model.CompanyBasics existing, 
            com.straube.jones.model.CompanyBasics newData)
    {
        if (newData == null)
        {
            return existing;
        }
        
        if (existing == null)
        {
            return newData;
        }
        
        com.straube.jones.model.CompanyBasics merged = new com.straube.jones.model.CompanyBasics();
        
        merged.setCompanyName(isNotEmpty(newData.getCompanyName()) ? 
                newData.getCompanyName() : existing.getCompanyName());
        
        merged.setHeadquarters(isNotEmpty(newData.getHeadquarters()) ? 
                newData.getHeadquarters() : existing.getHeadquarters());
        
        merged.setBusinessModel(isNotEmpty(newData.getBusinessModel()) ? 
                newData.getBusinessModel() : existing.getBusinessModel());
        
        return merged;
    }

    /**
     * Merged StockData Daten.
     * 
     * @param existing Existierende StockData (kann null sein)
     * @param newData Neue StockData (kann null sein)
     * @return Gemergte StockData
     */
    private com.straube.jones.model.StockData mergeStockData(
            com.straube.jones.model.StockData existing, 
            com.straube.jones.model.StockData newData)
    {
        if (newData == null)
        {
            return existing;
        }
        
        if (existing == null)
        {
            return newData;
        }
        
        com.straube.jones.model.StockData merged = new com.straube.jones.model.StockData();
        
        merged.setMarketCapitalization(isNotEmpty(newData.getMarketCapitalization()) ? 
                newData.getMarketCapitalization() : existing.getMarketCapitalization());
        
        merged.setPricePerformance6Months(isNotEmpty(newData.getPricePerformance6Months()) ? 
                newData.getPricePerformance6Months() : existing.getPricePerformance6Months());
        
        merged.setPriceOutlook5Days(isNotEmpty(newData.getPriceOutlook5Days()) ? 
                newData.getPriceOutlook5Days() : existing.getPriceOutlook5Days());
        
        merged.setAnalystOpinions(isNotEmpty(newData.getAnalystOpinions()) ? 
                newData.getAnalystOpinions() : existing.getAnalystOpinions());
        
        return merged;
    }

    /**
     * Prüft, ob ein String nicht null und nicht leer ist.
     * 
     * @param value Der zu prüfende String
     * @return true wenn nicht null und nicht leer
     */
    private boolean isNotEmpty(String value)
    {
        return value != null && !value.trim().isEmpty();
    }
}
