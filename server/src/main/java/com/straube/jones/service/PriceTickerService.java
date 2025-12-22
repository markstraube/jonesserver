package com.straube.jones.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.straube.jones.dto.PriceEntry;
import com.straube.jones.dto.PriceTickerResponse;
import com.straube.jones.model.StockFundamentals;

/**
 * Service for retrieving stock price information from Yahoo Finance.
 * Scrapes Yahoo Finance HTML pages to extract current, pre-market, and after-market prices.
 * Uses stable data-testid attributes instead of volatile CSS classes for robustness.
 */
public class PriceTickerService
{
    private final FundamentalsService fundamentalsService;
    private static final String YAHOO_FINANCE_URL = "https://finance.yahoo.com/quote/";
    private static final int TIMEOUT_MS = 10000;
    
    // Date time patterns for various Yahoo Finance timestamp formats
    private static final Pattern TIME_PATTERN_1 = Pattern.compile("(\\d{1,2}):(\\d{2})([AP]M) (\\w+)");
    private static final Pattern TIME_PATTERN_2 = Pattern.compile("([A-Z][a-z]{2,8}) (\\d{1,2}), (\\d{4}) at (\\d{1,2}):(\\d{2}) ([AP]M) (\\w+)");
    
    public PriceTickerService()
    {
        this.fundamentalsService = new FundamentalsService();
    }
    
    public PriceTickerService(FundamentalsService fundamentalsService)
    {
        this.fundamentalsService = fundamentalsService;
    }
    
    /**
     * Retrieves current price ticker information for a stock by ISIN.
     * 
     * @param isin The ISIN of the stock
     * @return PriceTickerResponse containing all available price information
     * @throws IllegalArgumentException if ISIN is invalid or cannot be resolved
     * @throws IOException if Yahoo Finance cannot be reached or parsed
     */
    public PriceTickerResponse getPriceByIsin(String isin) throws IOException
    {
        if (isin == null || isin.trim().isEmpty())
        {
            throw new IllegalArgumentException("ISIN cannot be null or empty");
        }
        
        // Resolve ISIN to Yahoo symbol using FundamentalsService
        Optional<StockFundamentals> fundamentalsOpt = fundamentalsService.findByIsin(isin);
        if (!fundamentalsOpt.isPresent())
        {
            throw new IllegalArgumentException("ISIN not found: " + isin);
        }
        
        StockFundamentals fundamentals = fundamentalsOpt.get();
        String symbolYahoo = fundamentals.getSymbolYahoo();
        
        if (symbolYahoo == null || symbolYahoo.trim().isEmpty())
        {
            throw new IllegalArgumentException("Yahoo symbol not available for ISIN: " + isin);
        }
        
        // Fetch and parse Yahoo Finance page
        return fetchPriceFromYahoo(isin, symbolYahoo);
    }
    
    /**
     * Fetches and parses price information from Yahoo Finance.
     */
    private PriceTickerResponse fetchPriceFromYahoo(String isin, String symbolYahoo) throws IOException
    {
        String url = YAHOO_FINANCE_URL + symbolYahoo;
        
        try
        {
            Document doc = Jsoup.connect(url)
                               .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                               .timeout(TIMEOUT_MS)
                               .get();
            
            PriceTickerResponse response = new PriceTickerResponse(isin, symbolYahoo, null);
            response.setRetrievedAt(Instant.now().toString());
            
            // Extract currency from the page
            String currency = extractCurrency(doc);
            response.setCurrency(currency);
            
            // Parse price blocks
            List<PriceEntry> prices = parsePriceBlocks(doc);
            response.setPrices(prices);
            
            if (prices.isEmpty())
            {
                throw new IOException("No price information found on Yahoo Finance page");
            }
            
            return response;
        }
        catch (IOException e)
        {
            throw new IOException("Failed to fetch data from Yahoo Finance: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extracts currency from the Yahoo Finance page.
     * Uses multiple fallback strategies for robustness.
     */
    private String extractCurrency(Document doc)
    {
        // Strategy 1: Look for fin-streamer with currency data
        Elements finStreamElements = doc.select("fin-streamer[data-field='regularMarketPrice']");
        if (!finStreamElements.isEmpty())
        {
            String currency = finStreamElements.first().attr("data-currency");
            if (currency != null && !currency.isEmpty())
            {
                return currency.toUpperCase();
            }
        }
        
        // Strategy 2: Check for currency in quote-header section
        Elements currencyElements = doc.select("div[data-testid='quote-header'] fin-streamer");
        for (Element element : currencyElements)
        {
            String currency = element.attr("data-currency");
            if (currency != null && !currency.isEmpty())
            {
                return currency.toUpperCase();
            }
        }
        
        // Strategy 3: Extract from price text
        Elements priceElements = doc.select("[data-testid='qsp-price']");
        if (!priceElements.isEmpty())
        {
            String priceText = priceElements.first().parent().text();
            
            if (priceText.contains("$") || priceText.contains("USD"))
            {
                return "USD";
            }
            else if (priceText.contains("€") || priceText.contains("EUR"))
            {
                return "EUR";
            }
            else if (priceText.contains("£") || priceText.contains("GBP"))
            {
                return "GBP";
            }
            else if (priceText.contains("¥") || priceText.contains("JPY"))
            {
                return "JPY";
            }
            else if (priceText.contains("CHF"))
            {
                return "CHF";
            }
        }
        
        return "USD"; // Default fallback
    }
    
    /**
     * Parses all price blocks from the Yahoo Finance page.
     * Uses data-testid attributes which are more stable than CSS classes.
     */
    private List<PriceEntry> parsePriceBlocks(Document doc)
    {
        List<PriceEntry> prices = new ArrayList<>();
        
        // Parse regular market price (always present)
        PriceEntry regularPrice = parseRegularPrice(doc);
        if (regularPrice != null)
        {
            prices.add(regularPrice);
        }
        
        // Parse pre/after market price (if exists)
        PriceEntry extendedPrice = parseExtendedPrice(doc);
        if (extendedPrice != null)
        {
            prices.add(extendedPrice);
        }
        
        return prices;
    }
    
    /**
     * Parses the regular market price (first price block).
     */
    private PriceEntry parseRegularPrice(Document doc)
    {
        try
        {
            // Extract price
            BigDecimal price = extractDataTestId(doc, "qsp-price");
            if (price == null) return null;
            
            // Extract changes
            BigDecimal changeAbsolute = extractDataTestId(doc, "qsp-price-change");
            BigDecimal changePercent = extractDataTestId(doc, "qsp-price-change-percent");
            
            // Extract timestamp and qualifier
            String[] timestampData = extractTimestamp(doc, "regularMarketTime");
            String timestamp = timestampData[0];
            String qualifier = timestampData[1];
            
            return new PriceEntry(
                PriceEntry.PriceType.REGULAR,
                price,
                changeAbsolute,
                changePercent,
                timestamp,
                qualifier
            );
        }
        catch (Exception e)
        {
            return null;
        }
    }
    
    /**
     * Parses the extended hours price (pre-market or after-market).
     */
    private PriceEntry parseExtendedPrice(Document doc)
    {
        try
        {
            // Extract pre/after market price
            BigDecimal price = extractDataTestId(doc, "qsp-pre-price");
            if (price == null) return null;
            
            // Extract changes
            BigDecimal changeAbsolute = extractDataTestId(doc, "qsp-pre-price-change");
            BigDecimal changePercent = extractDataTestId(doc, "qsp-pre-price-change-percent");
            
            // Extract timestamp and qualifier
            String[] timestampData = extractTimestamp(doc, "preMarketTime");
            String timestamp = timestampData[0];
            String qualifier = timestampData[1];
            
            // Determine if it's pre-market or after-market based on qualifier
            PriceEntry.PriceType type = PriceEntry.PriceType.AFTER_MARKET;
            if (qualifier != null && qualifier.toLowerCase().contains("pre"))
            {
                type = PriceEntry.PriceType.PRE_MARKET;
            }
            
            return new PriceEntry(
                type,
                price,
                changeAbsolute,
                changePercent,
                timestamp,
                qualifier
            );
        }
        catch (Exception e)
        {
            return null;
        }
    }
    
    /**
     * Extracts a numeric value from an element with the given data-testid.
     * Tries to get the raw value from data-value attribute first (contains full precision),
     * then falls back to text content.
     */
    private BigDecimal extractDataTestId(Document doc, String testId)
    {
        Elements elements = doc.select("[data-testid='" + testId + "']");
        if (elements.isEmpty()) return null;
        
        Element element = elements.first();
        
        // Strategy 1: Try to get value from data-value attribute (full precision)
        String dataValue = element.attr("data-value");
        if (dataValue != null && !dataValue.isEmpty())
        {
            BigDecimal value = parseNumericValue(dataValue);
            if (value != null) return value;
        }
        
        // Strategy 2: Look for fin-streamer child with data-value
        Elements finStreamers = element.select("fin-streamer[data-value]");
        if (!finStreamers.isEmpty())
        {
            String finValue = finStreamers.first().attr("data-value");
            if (finValue != null && !finValue.isEmpty())
            {
                BigDecimal value = parseNumericValue(finValue);
                if (value != null) return value;
            }
        }
        
        // Strategy 3: Fall back to text content
        String text = element.text();
        return parseNumericValue(text);
    }
    
    /**
     * Extracts timestamp and qualifier from the page.
     * Returns [timestamp, qualifier]
     * Uses multiple strategies to find timestamp information.
     */
    private String[] extractTimestamp(Document doc, String fieldType)
    {
        String[] result = new String[2];
        
        // Strategy 1: Look for fin-streamer elements with time data
        Elements finStreamers = doc.select("fin-streamer[data-field='" + fieldType + "']");
        if (finStreamers.isEmpty())
        {
            // Try all time-related fields
            finStreamers = doc.select("fin-streamer[data-field='regularMarketTime'], fin-streamer[data-field='preMarketTime'], fin-streamer[data-field='postMarketTime']");
        }
        
        for (Element streamer : finStreamers)
        {
            String timeValue = streamer.attr("data-value");
            if (timeValue != null && !timeValue.isEmpty())
            {
                try
                {
                    long timestamp = Long.parseLong(timeValue);
                    result[0] = Instant.ofEpochSecond(timestamp).toString();
                    
                    // Determine qualifier from field type
                    String field = streamer.attr("data-field");
                    if (field.contains("preMarket"))
                    {
                        result[1] = "Pre-Market";
                    }
                    else if (field.contains("postMarket"))
                    {
                        result[1] = "After hours";
                    }
                    else
                    {
                        result[1] = "At close";
                    }
                    return result;
                }
                catch (NumberFormatException e)
                {
                    // Continue to next strategy
                }
            }
        }
        
        // Strategy 2: Look for text-based timestamps (fallback)
        Elements allElements = doc.select("div[id*='quote'], section[data-testid], div[data-testid]");
        allElements.addAll(doc.select("span, div"));
        
        for (Element element : allElements)
        {
            String text = element.text();
            
            // Check if this element contains a qualifier
            if (text.matches("(?i).*(as of|at close|pre-market|after hours).*"))
            {
                String timestamp = parseTimestamp(text);
                String qualifier = extractQualifier(text);
                
                if (timestamp != null && qualifier != null)
                {
                    result[0] = timestamp;
                    result[1] = qualifier;
                    return result;
                }
            }
        }
        
        // Strategy 3: Default to current time
        result[0] = Instant.now().toString();
        result[1] = "As of";
        
        return result;
    }
    
    /**
     * Extracts the qualifier from a timestamp text.
     */
    private String extractQualifier(String text)
    {
        if (text.toLowerCase().contains("as of"))
        {
            return "As of";
        }
        else if (text.toLowerCase().contains("at close"))
        {
            return "At close";
        }
        else if (text.toLowerCase().contains("pre-market"))
        {
            return "Pre-Market";
        }
        else if (text.toLowerCase().contains("after hours"))
        {
            return "After hours";
        }
        
        return null;
    }
    
    /**
     * Parses a timestamp from text and converts it to ISO-8601 format.
     * Handles various Yahoo Finance timestamp formats.
     */
    private String parseTimestamp(String text)
    {
        if (text == null || text.isEmpty())
        {
            return Instant.now().toString();
        }
        
        try
        {
            // Format: "As of 3:59PM EST" or "At close: 4:00PM EST"
            Matcher matcher1 = TIME_PATTERN_1.matcher(text);
            if (matcher1.find())
            {
                // Extract time components but use current date
                // This is a simplification - proper timezone handling would require more logic
                return Instant.now().toString();
            }
            
            // Format: "Dec 22, 2024 at 3:59 PM EST"
            Matcher matcher2 = TIME_PATTERN_2.matcher(text);
            if (matcher2.find())
            {
                String month = matcher2.group(1);
                String day = matcher2.group(2);
                String year = matcher2.group(3);
                String hour = matcher2.group(4);
                String minute = matcher2.group(5);
                String ampm = matcher2.group(6);
                
                // Convert to 24-hour format
                int hourInt = Integer.parseInt(hour);
                if (ampm.equalsIgnoreCase("PM") && hourInt != 12)
                {
                    hourInt += 12;
                }
                else if (ampm.equalsIgnoreCase("AM") && hourInt == 12)
                {
                    hourInt = 0;
                }
                
                // Build ISO timestamp (simplified - assumes UTC)
                String isoString = String.format("%s-%s-%sT%02d:%s:00Z", 
                    year, 
                    getMonthNumber(month), 
                    String.format("%02d", Integer.parseInt(day)),
                    hourInt,
                    minute);
                
                return Instant.parse(isoString).toString();
            }
            
            return Instant.now().toString();
        }
        catch (Exception e)
        {
            return Instant.now().toString();
        }
    }
    
    /**
     * Converts month name to number.
     */
    private String getMonthNumber(String monthName)
    {
        switch (monthName.substring(0, 3).toLowerCase())
        {
            case "jan": return "01";
            case "feb": return "02";
            case "mar": return "03";
            case "apr": return "04";
            case "may": return "05";
            case "jun": return "06";
            case "jul": return "07";
            case "aug": return "08";
            case "sep": return "09";
            case "oct": return "10";
            case "nov": return "11";
            case "dec": return "12";
            default: return "01";
        }
    }
    
    /**
     * Parses a numeric value from text, removing currency symbols and percent signs.
     * Preserves all decimal places without rounding using BigDecimal.
     */
    private BigDecimal parseNumericValue(String text)
    {
        if (text == null || text.isEmpty()) return null;
        
        try
        {
            // Remove currency symbols, commas, percent signs, parentheses, and whitespace
            // Keep: digits, decimal point, minus, plus
            String cleaned = text.replaceAll("[^0-9.\\-+]", "").trim();
            
            // Handle empty string after cleaning
            if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equals("+"))
            {
                return null;
            }
            
            // Parse with BigDecimal to preserve all decimal places exactly
            return new BigDecimal(cleaned);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}
