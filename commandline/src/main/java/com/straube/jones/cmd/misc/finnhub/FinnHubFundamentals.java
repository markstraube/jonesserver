package com.straube.jones.cmd.misc.finnhub;


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

public class FinnHubFundamentals
{
    private static final Logger LOGGER = Logger.getLogger(FinnHubFundamentals.class.getName());
    private static final String FINNHUB_API_KEY = System.getenv("FINHUB_API_KEY");

    private File rootFolder;

    public FinnHubFundamentals(String rootFolder)
    {
        this.rootFolder = new File(rootFolder, "yahoo");
        this.rootFolder.mkdirs();

        if (FINNHUB_API_KEY == null || FINNHUB_API_KEY.isEmpty())
        {
            LOGGER.log(Level.WARNING, "FINHUB_API_KEY environment variable not set!");
        }
    }


    /**
     * Lädt Fundamental-Daten für ein Symbol von FinnHub API
     * 
     * @param symbol Aktiensymbol (z.B. "AAPL")
     * @return JSONObject mit den Daten von FinnHub (Company Profile + Metrics)
     */
    public JSONObject downloadFundamentalData(String symbol)
        throws IOException
    {
        if (FINNHUB_API_KEY == null || FINNHUB_API_KEY.isEmpty())
        { throw new IOException("FINHUB_API_KEY environment variable not set"); }

        JSONObject combinedData = new JSONObject();

        try
        {
            // 1. Company Profile (Name, Industry, Sector, etc.)
            String profileUrl = String.format("https://finnhub.io/api/v1/stock/profile2?symbol=%s&token=%s",
                                              URLEncoder.encode(symbol, StandardCharsets.UTF_8),
                                              FINNHUB_API_KEY);

            JSONObject profile = fetchFinnhubData(profileUrl, "Company Profile");
            if (profile != null && profile.length() > 0)
            {
                combinedData.put("profile", profile);
            }

            // Kleine Pause zwischen API-Aufrufen (Rate Limiting)
            Thread.sleep(350);

            // 2. Basic Financials (Metrics wie PE, Market Cap, etc.)
            String metricsUrl = String.format("https://finnhub.io/api/v1/stock/metric?symbol=%s&metric=all&token=%s",
                                              URLEncoder.encode(symbol, StandardCharsets.UTF_8),
                                              FINNHUB_API_KEY);

            JSONObject metrics = fetchFinnhubData(metricsUrl, "Metrics");
            if (metrics != null && metrics.length() > 0)
            {
                combinedData.put("metrics", metrics);
            }

            // Kleine Pause zwischen API-Aufrufen (Rate Limiting)
            Thread.sleep(350);

            // 3. Quote Data (Aktueller Preis, etc.)
            String quoteUrl = String.format("https://finnhub.io/api/v1/quote?symbol=%s&token=%s",
                                            URLEncoder.encode(symbol, StandardCharsets.UTF_8),
                                            FINNHUB_API_KEY);

            JSONObject quote = fetchFinnhubData(quoteUrl, "Quote");
            if (quote != null && quote.length() > 0)
            {
                combinedData.put("quote", quote);
            }

            combinedData.put("symbol", symbol);
            combinedData.put("source", "finnhub");

            return combinedData;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
    }


    /**
     * Hilfsmethode zum Abrufen von Daten von FinnHub API
     */
    private JSONObject fetchFinnhubData(String urlString, String dataType)
        throws IOException
    {
        LOGGER.log(Level.FINE, () -> dataType + " URL: " + urlString);

        URL url = new URL(urlString);
        HttpURLConnection conn = null;

        try
        {
            conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent",
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Connection", "close");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200)
            {
                String errorMsg;
                InputStream errorStream = conn.getErrorStream();
                if (errorStream != null)
                {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream,
                                                                                          StandardCharsets.UTF_8)))
                    {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null)
                        {
                            response.append(line);
                        }
                        errorMsg = response.toString();
                    }
                }
                else
                {
                    errorMsg = "No error details available";
                }
                throw new IOException(dataType + " - HTTP Error Code: "
                                + responseCode
                                + " - Details: "
                                + errorMsg);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(),
                                                                                  StandardCharsets.UTF_8)))
            {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null)
                {
                    response.append(line);
                }

                String responseStr = response.toString();
                if (responseStr == null || responseStr.trim().isEmpty())
                {
                    LOGGER.log(Level.WARNING, () -> dataType + " returned empty response");
                    return new JSONObject();
                }

                return new JSONObject(responseStr);
            }
        }
        catch (IOException e)
        {
            LOGGER.log(Level.WARNING, () -> dataType + " API call failed: " + e.getMessage());
            throw e;
        }
        finally
        {
            if (conn != null)
            {
                try
                {
                    conn.disconnect();
                }
                catch (Exception e)
                {
                    // Ignore disconnect errors
                }
            }
        }
    }


    /**
     * Extrahiert wichtige Fundamental-Kennzahlen aus den FinnHub Daten
     * 
     * @param fundamentalData JSON-Objekt von downloadFundamentalData() (FinnHub API Response)
     * @return Strukturiertes JSONObject mit den wichtigsten Kennzahlen
     */
    public static JSONObject extractKeyMetrics(JSONObject fundamentalData)
    {
        JSONObject metrics = new JSONObject();

        try
        {
            // Hole Symbol und Source
            if (fundamentalData.has("symbol"))
            {
                metrics.put("symbol", fundamentalData.getString("symbol"));
            }
            if (fundamentalData.has("source"))
            {
                metrics.put("source", fundamentalData.getString("source"));
            }

            // Company Profile (Name, Industry, Sector, etc.)
            if (fundamentalData.has("profile"))
            {
                JSONObject profile = fundamentalData.getJSONObject("profile");

                if (profile.has("name"))
                    metrics.put("name", profile.getString("name"));
                if (profile.has("ticker"))
                    metrics.put("ticker", profile.getString("ticker"));
                if (profile.has("exchange"))
                    metrics.put("exchange", profile.getString("exchange"));
                if (profile.has("country"))
                    metrics.put("country", profile.getString("country"));
                if (profile.has("currency"))
                    metrics.put("currency", profile.getString("currency"));
                if (profile.has("finnhubIndustry"))
                    metrics.put("industry", profile.getString("finnhubIndustry"));
                if (profile.has("weburl"))
                    metrics.put("website", profile.getString("weburl"));
                if (profile.has("ipo"))
                    metrics.put("ipoDate", profile.getString("ipo"));
                if (profile.has("marketCapitalization"))
                    metrics.put("marketCap", profile.getDouble("marketCapitalization") * 1_000_000); // FinnHub
                                                                                                     // gibt
                                                                                                     // in
                                                                                                     // Millionen
                if (profile.has("shareOutstanding"))
                    metrics.put("sharesOutstanding", profile.getDouble("shareOutstanding") * 1_000_000); // FinnHub
                                                                                                         // gibt
                                                                                                         // in
                                                                                                         // Millionen
                if (profile.has("phone"))
                    metrics.put("phone", profile.getString("phone"));
            }

            // Metrics (PE, Beta, Margins, etc.)
            if (fundamentalData.has("metrics") && fundamentalData.getJSONObject("metrics").has("metric"))
            {
                JSONObject metric = fundamentalData.getJSONObject("metrics").getJSONObject("metric");

                // P/E Ratios
                if (metric.has("peNormalizedAnnual"))
                    metrics.put("trailingPE", metric.getDouble("peNormalizedAnnual"));
                if (metric.has("peBasicExclExtraTTM"))
                    metrics.put("peBasic", metric.getDouble("peBasicExclExtraTTM"));

                // Beta
                if (metric.has("beta"))
                    metrics.put("beta", metric.getDouble("beta"));

                // 52 Week High/Low
                if (metric.has("52WeekHigh"))
                    metrics.put("fiftyTwoWeekHigh", metric.getDouble("52WeekHigh"));
                if (metric.has("52WeekLow"))
                    metrics.put("fiftyTwoWeekLow", metric.getDouble("52WeekLow"));

                // Dividends
                if (metric.has("dividendYieldIndicatedAnnual"))
                    metrics.put("dividendYield", metric.getDouble("dividendYieldIndicatedAnnual"));
                if (metric.has("dividendPerShareAnnual"))
                    metrics.put("dividendRate", metric.getDouble("dividendPerShareAnnual"));
                if (metric.has("payoutRatioAnnual"))
                    metrics.put("payoutRatio", metric.getDouble("payoutRatioAnnual"));

                // Book Value
                if (metric.has("bookValuePerShareAnnual"))
                    metrics.put("bookValue", metric.getDouble("bookValuePerShareAnnual"));
                if (metric.has("pbAnnual"))
                    metrics.put("priceToBook", metric.getDouble("pbAnnual"));

                // Margins
                if (metric.has("netProfitMarginAnnual"))
                    metrics.put("profitMargins", metric.getDouble("netProfitMarginAnnual"));
                if (metric.has("grossMarginAnnual"))
                    metrics.put("grossMargins", metric.getDouble("grossMarginAnnual"));
                if (metric.has("operatingMarginAnnual"))
                    metrics.put("operatingMargins", metric.getDouble("operatingMarginAnnual"));

                // Return Metrics
                if (metric.has("roaRfy"))
                    metrics.put("returnOnAssets", metric.getDouble("roaRfy"));
                if (metric.has("roeRfy"))
                    metrics.put("returnOnEquity", metric.getDouble("roeRfy"));
                if (metric.has("roiAnnual"))
                    metrics.put("returnOnInvestment", metric.getDouble("roiAnnual"));

                // Debt
                if (metric.has("totalDebt/totalEquityAnnual"))
                    metrics.put("debtToEquity", metric.getDouble("totalDebt/totalEquityAnnual"));
                if (metric.has("currentRatioAnnual"))
                    metrics.put("currentRatio", metric.getDouble("currentRatioAnnual"));
                if (metric.has("quickRatioAnnual"))
                    metrics.put("quickRatio", metric.getDouble("quickRatioAnnual"));

                // EPS
                if (metric.has("epsBasicExclExtraItemsAnnual"))
                    metrics.put("trailingEps", metric.getDouble("epsBasicExclExtraItemsAnnual"));

                // Revenue
                if (metric.has("revenuePerShareAnnual"))
                    metrics.put("revenuePerShare", metric.getDouble("revenuePerShareAnnual"));
            }

            // Quote (Current Price, Volume, Change, etc.)
            if (fundamentalData.has("quote"))
            {
                JSONObject quote = fundamentalData.getJSONObject("quote");

                if (quote.has("c"))
                    metrics.put("currentPrice", quote.getDouble("c"));
                if (quote.has("h"))
                    metrics.put("dayHigh", quote.getDouble("h"));
                if (quote.has("l"))
                    metrics.put("dayLow", quote.getDouble("l"));
                if (quote.has("o"))
                    metrics.put("openPrice", quote.getDouble("o"));
                if (quote.has("pc"))
                    metrics.put("previousClose", quote.getDouble("pc"));
                if (quote.has("dp"))
                    metrics.put("changePercent", quote.getDouble("dp"));
                if (quote.has("d"))
                    metrics.put("change", quote.getDouble("d"));
                if (quote.has("t"))
                    metrics.put("timestamp", quote.getLong("t"));
            }
        }
        catch (Exception e)
        {
            LOGGER.log(Level.WARNING, "Error extracting key metrics from FinnHub data", e);
        }

        return metrics;
    }


    /**
     * Lädt Fundamental-Daten für alle Aktien aus StocksCode.json assetProfile - Firmenprofil, Branche,
     * Mitarbeiter, Website summaryProfile - Zusammenfassung summaryDetail - Marktkapitalisierung, P/E Ratio,
     * Dividende, Beta financialData - Finanzkennzahlen, Gewinnmargen, ROE, Verschuldung defaultKeyStatistics
     * - Key Statistics, Shares Outstanding, Float calendarEvents - Dividenden-Termine, Earnings-Termine
     * recommendationTrend - Analystenbewertungen upgradeDowngradeHistory - Upgrade/Downgrade Historie
     * earnings - Gewinndaten earningsHistory - Gewinn-Historie earningsTrend - Gewinn-Prognosen
     * incomeStatementHistory - Gewinn- und Verlustrechnung balanceSheetHistory - Bilanz
     * cashflowStatementHistory - Cashflow price - Aktueller Preis und Marktdaten
     * 
     * @return true wenn erfolgreich
     */
    public boolean fetchFundamentalData(boolean skipExisting, boolean reportMissing)
    {
        try
        {
            LOGGER.log(Level.INFO, "Loading StocksCode.json from classpath");

            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("StocksCode.json");
            if (inputStream == null)
            {
                LOGGER.log(Level.SEVERE, "StocksCode.json not found in classpath");
                return false;
            }

            String jsonString = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            JSONObject stocksData = new JSONObject(jsonString);

            LOGGER.log(Level.INFO, () -> stocksData.length() + " stocks loaded");

            // Erstelle fundamental Unterordner
            File fundamentalFolder = new File(rootFolder, "fundamental");
            fundamentalFolder.mkdirs();

            int count = 0;
            int errors = 0;

            // Iteriere über alle ISIN-Einträge
            for (String isin : stocksData.keySet())
            {
                JSONObject stockInfo = stocksData.getJSONObject(isin);
                String code = stockInfo.getString("code");

                // Erstelle Dateinamen: <ISIN>_<code>_fundamental.json
                File outputFile = new File(fundamentalFolder, isin + "_" + code + "_fundamental.json");

                if (outputFile.exists() && skipExisting)
                {
                    if (!reportMissing)
                    {
                        LOGGER.log(Level.INFO,
                                   () -> "File already exists, skipping: " + outputFile.getAbsolutePath());
                    }
                    continue;
                }
                else if (reportMissing)
                {
                    LOGGER.log(Level.WARNING,
                               () -> "nothing found for code: " + code + " " + stockInfo.toString());
                    continue;
                }
                try
                {
                    LOGGER.log(Level.INFO,
                               () -> "Fetching fundamental data for " + code + " (ISIN: " + isin + ")");

                    // Lade die Fundamental-Daten (Chart-API mit Meta-Informationen)
                    JSONObject fundamentalData = downloadFundamentalData(code);

                    // Extrahiere wichtige Kennzahlen
                    JSONObject keyMetrics = extractKeyMetrics(fundamentalData);

                    // Erstelle Ausgabe-JSON mit Symbol, ISIN und Kennzahlen
                    JSONObject output = new JSONObject();
                    output.put("symbol", code);
                    output.put("isin", isin);
                    output.put("fetchDate", LocalDate.now().format(DateTimeFormatter.ISO_DATE));
                    output.put("metrics", keyMetrics);
                    output.put("rawData", fundamentalData);

                    // Speichere in Datei
                    Files.write(outputFile.toPath(), output.toString(2).getBytes(StandardCharsets.UTF_8));

                    count++ ;
                    if (count % 10 == 0)
                    {
                        final int currentCount = count;
                        final int totalStocks = stocksData.length();
                        LOGGER.log(Level.INFO, () -> currentCount + " of " + totalStocks + " stocks fetched");
                    }

                    // Rate limiting - FinnHub: 60 calls/min, 3 calls/stock = max 20 stocks/min
                    // Warte 2 Sekunden zwischen Aktien (30 Aktien/min = sicher unter dem Limit)
                    Thread.sleep(2000);

                }
                catch (Exception e)
                {
                    errors++ ;
                    LOGGER.log(Level.WARNING,
                               () -> "Error fetching fundamental data for " + code
                                               + " (ISIN: "
                                               + isin
                                               + "): "
                                               + e.getMessage());
                }
            }

            final int finalCount = count;
            final int finalErrors = errors;
            LOGGER.log(Level.INFO,
                       () -> "Done! " + finalCount
                                       + " stocks saved to "
                                       + fundamentalFolder.getAbsolutePath()
                                       + " ("
                                       + finalErrors
                                       + " errors)");

            return true;

        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, "Error fetching fundamental data", e);
            return false;
        }
    }


    /**
     * Beispiel-Verwendung
     */
    public static void main(String[] args)
    {
        String rootFolder = args.length > 0 ? args[0] : "./data";
        FinnHubFundamentals downloader = new FinnHubFundamentals(rootFolder);

        // Lade auch Fundamental-Daten
        boolean fundamentalSuccess = downloader.fetchFundamentalData(true, false);
        System.exit(fundamentalSuccess ? 0 : 1);
    }
}
