package com.straube.jones.service;


import java.io.IOException;
import java.util.Optional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.straube.jones.agent.StocksAgent;
import com.straube.jones.dto.StockSnapshot;
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
    public StockFundamentals create(StockFundamentals fundamentals)
        throws IOException
    {
        validateFundamentals(fundamentals);

        String isin = fundamentals.getIsin();

        if (repository.exists(isin))
        { throw new IllegalArgumentException("Fundamentaldaten für ISIN " + isin + " existieren bereits"); }

        repository.save(fundamentals);
        return fundamentals;
    }


    /**
     * Erzeugt oder aktualisiert existierende Fundamentaldaten für eine Aktie.
     * Führt ein Merge durch: Nur nicht-leere Felder aus fundamentals überschreiben existierende Werte.
     * 
     * @param fundamentals Die zu mergenden Fundamentaldaten
     * @return Die aktualisierten Fundamentaldaten nach dem Merge
     * @throws IllegalArgumentException wenn ISIN nicht übereinstimmt oder Daten nicht existieren
     * @throws IOException bei Dateisystem-Fehlern
     */
    public StockFundamentals upsert(StockFundamentals fundamentals)
        throws IOException
    {
        String isin = fundamentals.getIsin();
        if (isin == null || isin.trim().isEmpty())
        { throw new IllegalArgumentException("ISIN darf nicht null oder leer sein"); }

        // Lade existierende Daten
        Optional<StockFundamentals> existingOpt = repository.findByIsin(isin);
        if (!existingOpt.isPresent())
        {
            repository.save(fundamentals);
            return fundamentals;
        }
        else
        {
            // Merge neue Daten mit existierenden Daten
            StockFundamentals existing = existingOpt.get();
            StockFundamentals merged = mergeFundamentals(existing, fundamentals);

            repository.save(merged);
            return merged;
        }
    }


    /**
     * Findet Fundamentaldaten für eine bestimmte ISIN.
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

        Optional<StockFundamentals> existing = repository.findByIsin(isin);
        if (!existing.isPresent())
        {
            StockFundamentals fundamentals = StocksAgent.execute(isin);
            if (fundamentals != null)
            {
                create(fundamentals);
                return Optional.of(fundamentals);
            }
            else
            {
                return Optional.empty();
            }
        }
        else
        {
            return existing;
        }
    }


    /**
     * Löscht Fundamentaldaten für eine bestimmte ISIN.
     * 
     * @param isin Die ISIN der Aktie
     * @return true wenn Daten gelöscht wurden, false wenn keine Daten existierten
     * @throws IOException bei Dateisystem-Fehlern
     */
    public boolean delete(String isin)
        throws IOException
    {
        if (isin == null || isin.trim().isEmpty())
        { return false; }

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
        { throw new IllegalArgumentException("Fundamentaldaten dürfen nicht null sein"); }

        if (fundamentals.getIsin() == null || fundamentals.getIsin().trim().isEmpty())
        { throw new IllegalArgumentException("ISIN darf nicht null oder leer sein"); }

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
        merged.setSymbolYahoo(isNotEmpty(newData.getSymbolYahoo()) ? newData.getSymbolYahoo()
                        : existing.getSymbolYahoo());

        // Merge SYMBOL.GOOGLE
        merged.setSymbolGoogle(isNotEmpty(newData.getSymbolGoogle()) ? newData.getSymbolGoogle()
                        : existing.getSymbolGoogle());

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
    private com.straube.jones.model.CompanyBasics mergeCompanyBasics(com.straube.jones.model.CompanyBasics existing,
                                                                     com.straube.jones.model.CompanyBasics newData)
    {
        if (newData == null)
        { return existing; }

        if (existing == null)
        { return newData; }

        com.straube.jones.model.CompanyBasics merged = new com.straube.jones.model.CompanyBasics();

        merged.setCompanyName(isNotEmpty(newData.getCompanyName()) ? newData.getCompanyName()
                        : existing.getCompanyName());

        merged.setHeadquarters(isNotEmpty(newData.getHeadquarters()) ? newData.getHeadquarters()
                        : existing.getHeadquarters());

        merged.setBusinessModel(isNotEmpty(newData.getBusinessModel()) ? newData.getBusinessModel()
                        : existing.getBusinessModel());

        return merged;
    }


    /**
     * Merged StockData Daten.
     * 
     * @param existing Existierende StockData (kann null sein)
     * @param newData Neue StockData (kann null sein)
     * @return Gemergte StockData
     */
    private com.straube.jones.model.StockData mergeStockData(com.straube.jones.model.StockData existing,
                                                             com.straube.jones.model.StockData newData)
    {
        if (newData == null)
        { return existing; }

        if (existing == null)
        { return newData; }

        com.straube.jones.model.StockData merged = new com.straube.jones.model.StockData();

        merged.setMarketCapitalization(isNotEmpty(newData.getMarketCapitalization())
                        ? newData.getMarketCapitalization()
                        : existing.getMarketCapitalization());

        merged.setPricePerformance6Months(isNotEmpty(newData.getPricePerformance6Months())
                        ? newData.getPricePerformance6Months()
                        : existing.getPricePerformance6Months());

        merged.setPriceOutlook5Days(isNotEmpty(newData.getPriceOutlook5Days())
                        ? newData.getPriceOutlook5Days()
                        : existing.getPriceOutlook5Days());

        merged.setAnalystOpinions(isNotEmpty(newData.getAnalystOpinions()) ? newData.getAnalystOpinions()
                        : existing.getAnalystOpinions());

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


    /**
     * Fetches stock data from Yahoo Finance.
     * 
     * @param symbolYahoo The Yahoo symbol of the stock
     * @return StockSnapshot containing the fetched data
     */
    public StockSnapshot getStockDataFromYahoo(String symbolYahoo)
    {
        StockSnapshot snapshot = new StockSnapshot();
        String url = "https://finance.yahoo.com/quote/" + symbolYahoo;

        try
        {
            Document doc = Jsoup.connect(url)
                                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                                .timeout(10000)
                                .get();

            Elements listItems = doc.select("#main-content-wrapper > section.quote-statistics-container > div > div > div > ul > li");

            StockSnapshot.Price price = new StockSnapshot.Price();
            StockSnapshot.Ranges ranges = new StockSnapshot.Ranges();
            StockSnapshot.Volume volume = new StockSnapshot.Volume();
            StockSnapshot.Market market = new StockSnapshot.Market();
            StockSnapshot.Earnings earnings = new StockSnapshot.Earnings();
            StockSnapshot.AnalystEstimates analystEstimates = new StockSnapshot.AnalystEstimates();

            for (Element li : listItems)
            {
                Elements spans = li.select("span");
                if (spans.size() < 2)
                    continue;

                String label = spans.get(0).text().trim();
                String value = spans.get(1).text().trim();

                if (value.equals("--") || value.equals("N/A"))
                    continue;

                switch (label)
                {
                case "Previous Close":
                    price.setPreviousClose(parseBigDecimal(value));
                    break;
                case "Open":
                    price.setOpen(parseBigDecimal(value));
                    break;
                case "Bid":
                    price.setBid(parseBidAsk(value));
                    break;
                case "Ask":
                    price.setAsk(parseBidAsk(value));
                    break;
                case "Day's Range":
                    ranges.setDay(parseRange(value));
                    break;
                case "52 Week Range":
                    ranges.setWeek52(parseRange(value));
                    break;
                case "Volume":
                    volume.setCurrent(parseLong(value));
                    break;
                case "Avg. Volume":
                    volume.setAverage(parseLong(value));
                    break;
                case "Market Cap (intraday)":
                    market.setMarketCapIntraday(parseLargeNumber(value));
                    break;
                case "Beta (5Y Monthly)":
                    market.setBeta5YMonthly(parseBigDecimal(value));
                    break;
                case "PE Ratio (TTM)":
                    market.setPeRatioTTM(parseBigDecimal(value));
                    break;
                case "EPS (TTM)":
                    market.setEpsTTM(parseBigDecimal(value));
                    break;
                case "Earnings Date":
                    earnings.setEarningsDate(parseDate(value));
                    break;
                case "1y Target Est":
                    analystEstimates.setOneYearTargetEstimate(parseBigDecimal(value));
                    break;
                }
            }

            snapshot.setPrice(price);
            snapshot.setRanges(ranges);
            snapshot.setVolume(volume);
            snapshot.setMarket(market);
            snapshot.setEarnings(earnings);
            snapshot.setAnalystEstimates(analystEstimates);

        }
        catch (Exception e)
        {
            // Log error or handle it. Returning empty/partial object as requested.
            e.printStackTrace();
        }

        return snapshot;
    }


    private BigDecimal parseBigDecimal(String value)
    {
        try
        {
            return new BigDecimal(value.replace(",", ""));
        }
        catch (Exception e)
        {
            return null;
        }
    }


    private Long parseLong(String value)
    {
        try
        {
            return Long.parseLong(value.replace(",", ""));
        }
        catch (Exception e)
        {
            return null;
        }
    }


    private StockSnapshot.BidAsk parseBidAsk(String value)
    {
        try
        {
            String[] parts = value.split(" x ");
            if (parts.length == 2)
            {
                return new StockSnapshot.BidAsk(parseBigDecimal(parts[0]),
                                                Integer.parseInt(parts[1].replace(",", "")));
            }
        }
        catch (Exception e)
        {
            // ignore
        }
        return null;
    }


    private StockSnapshot.Range parseRange(String value)
    {
        try
        {
            String[] parts = value.split(" - ");
            if (parts.length == 2)
            { return new StockSnapshot.Range(parseBigDecimal(parts[0]), parseBigDecimal(parts[1])); }
        }
        catch (Exception e)
        {
            // ignore
        }
        return null;
    }


    private BigDecimal parseLargeNumber(String value)
    {
        try
        {
            char suffix = value.charAt(value.length() - 1);
            BigDecimal multiplier = BigDecimal.ONE;
            String numberPart = value;

            if (Character.isLetter(suffix))
            {
                numberPart = value.substring(0, value.length() - 1);
                switch (suffix)
                {
                case 'T':
                    multiplier = new BigDecimal("1000000000000");
                    break;
                case 'B':
                    multiplier = new BigDecimal("1000000000");
                    break;
                case 'M':
                    multiplier = new BigDecimal("1000000");
                    break;
                case 'K':
                    multiplier = new BigDecimal("1000");
                    break;
                }
            }
            return new BigDecimal(numberPart.replace(",", "")).multiply(multiplier);
        }
        catch (Exception e)
        {
            return null;
        }
    }


    private String parseDate(String value)
    {
        // Example: Aug 01, 2024 or Aug 01, 2024 - Aug 05, 2024
        try
        {
            // Take the first date if it's a range
            String dateStr = value.split(" - ")[0];
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US);
            LocalDate date = LocalDate.parse(dateStr, formatter);
            return date.toString(); // ISO-8601
        }
        catch (Exception e)
        {
            return null;
        }
    }


    public static void main(String[] args)
    {
        String symbol = "LRCX";
        if (args.length > 0)
        {
            symbol = args[0];
        }
        System.out.println("Analyzing stock with symbol: " + symbol);
        System.out.println("Please wait, fetching data...");

        try
        {
           FundamentalsService fundamentalsService = new FundamentalsService();
            StockSnapshot snapshot = fundamentalsService.getStockDataFromYahoo(symbol);
            System.out.println("Yahoo Analysis complete. Resulting Stock Snapshot:");
            System.out.println(snapshot.toString());
        }
        catch (Exception e)
        {
            System.err.println("Error occurred while analyzing stock: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
