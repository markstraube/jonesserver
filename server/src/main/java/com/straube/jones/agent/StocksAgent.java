package com.straube.jones.agent;


import java.util.ArrayList;
import java.util.List;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.straube.jones.model.StockFundamentals;
import com.straube.jones.service.FundamentalsService;

public class StocksAgent
{
    private final OpenAIClient openai = OpenAIOkHttpClient.fromEnv();

    /**
     * 
     * @param task
     * @return
     */
    public StockFundamentals execute(String isin)
    {
        String systemPrompt = """
                           You are an autonomous AI research agent with access to the internet.

                           TASK:
                           The user provides an ISIN. Identify the corresponding publicly traded company and collect up-to-date, reliable information about it. Explicitly include both German- and English-language financial, analytical, and news sources, with a strong emphasis on English-language sources.

                           SCOPE OF ANALYSIS:
                           Retrieve only the information listed below.

                           1) Identifiers
                            "ISIN" : fill in the provided ISIN
                            "WKN" : retrieve the WKN (Wertpapierkennnummer) for the company
                            "SYMBOL" : fill in the stock ticker symbol
                            "SYMBOL.YAHOO" : fill in the Yahoo Finance ticker symbol
                            "SYMBOL.GOOGLE" : fill in the Google Finance ticker symbol

                           2) Company Basics
                           - Company name
                           - Company headquarters (city, country)
                           - Core business: how the company primarily generates revenue

                           3) Stock Data
                           - Current market capitalization (including currency)
                           - Qualitative assessment of the stock price performance over the last 6 months
                           (e.g. clearly rising, volatile sideways movement, clearly declining, including a short justification)
                           - Short-term 5-day stock price outlook
                           (qualitative forecast with reasoning based on recent data, news, and technical signals)
                           - Current analyst opinions, not older than 3 months
                           (summary of the prevailing sentiment, e.g. Buy / Hold / Sell, including overall tendency)

                           TECHNICAL REQUIREMENTS:
                           - No explanatory text, no introductions, no disclaimers
                           - Do not return any data outside the defined structure
                           - Use `null` if a data point cannot be determined reliably
                           - Information should be as current as possible
                        """;

        String userPrompt = """
                            Analyze the company and its stock based on the following ISIN.

                            ISIN: %s

                            Process exactly one ISIN and return only valid JSON.
                        """.formatted(isin);

        StructuredResponseCreateParams<StockFundamentals> params = ResponseCreateParams.builder()
                                                                                       .model(ChatModel.of("gpt-5-2025-08-07"))
                                                                                       .instructions(systemPrompt)
                                                                                       .text(StockFundamentals.class)
                                                                                       .input(userPrompt)
                                                                                       .maxOutputTokens(5000)                                                                                       
                                                                                       .build();

        StructuredResponse<StockFundamentals> response = openai.responses().create(params);

        List<StockFundamentals> resultList = new ArrayList<>();
        response.output()
                .stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .forEach(resultList::add);

        if (resultList.isEmpty())
        { return new StockFundamentals(); }

        return resultList.get(0);
    }


    /**
     * Main method to demonstrate the StocksAgent functionality
     * 
     * @param args Command line arguments. Expects an ISIN as the first argument.
     */
    public static void main(String[] args)
    {
        String isin = "US5951121038"; 
        if (args.length > 0)
        {
            isin = args[0];
        }
        System.out.println("Analyzing stock with ISIN: " + isin);
        System.out.println("Please wait, fetching data...");

        try
        {
            StocksAgent agent = new StocksAgent();
            StockFundamentals result = agent.execute(isin);
            FundamentalsService fundamentalsService = new FundamentalsService();
            if (fundamentalsService.exists(isin))
            {
                fundamentalsService.update(isin, result);
                System.out.println("Updated existing fundamentals in the database.");
            }
            else
            {
                fundamentalsService.create(result);
                System.out.println("Created new fundamentals in the database.");
            }
        }
        catch (Exception e)
        {
            System.err.println("Error occurred while analyzing stock: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
