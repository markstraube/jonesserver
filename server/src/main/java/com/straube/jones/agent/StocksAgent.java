package com.straube.jones.agent;


import java.util.ArrayList;
import java.util.List;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.models.responses.Tool;
import com.openai.models.responses.WebSearchTool;
import com.openai.models.responses.WebSearchTool.Type;
import com.straube.jones.model.StockFundamentals;
import com.straube.jones.service.FundamentalsService;

public class StocksAgent
{
    private static final OpenAIClient openai = OpenAIOkHttpClient.fromEnv();

    public static StockFundamentals execute(String isin)
    {
        String systemPrompt = """
                                You are an autonomous financial research agent.

                                You have access to web search and may use it to verify information.
                                Use web search whenever necessary to ensure correctness.

                                TASK
                                You will be given exactly one ISIN.
                                Identify the corresponding publicly traded company and collect
                                current, reliably verifiable information about it.

                                STRICT PRINCIPLES (MUST FOLLOW)
                                - Data correctness has higher priority than completeness.
                                - Never approximate, infer, or guess values.
                                - Never rely on memory alone for identifiers or tickers.
                                - Use web search to verify each identifier.
                                - If a value cannot be verified with high confidence, return null.
                                - Do not fabricate sources, prices, identifiers, or company details.

                                ALLOWED SOURCES (NON-EXHAUSTIVE)
                                - Official company website
                                - Stock exchange websites
                                - Yahoo Finance
                                - Google Finance
                                - Reputable financial data providers
                                - Regulatory filings

                                DATA TO RETURN

                                1) IDENTIFIERS
                                - isin: string (use the provided ISIN exactly)
                                - wkn: string | null
                                - symbol: string | null
                                - symbolYahoo: string | null
                                - symbolGoogle: string | null

                                2) COMPANY BASICS
                                - companyName: string | null
                                - headquartersCity: string | null
                                - headquartersCountry: string | null
                                - coreBusiness: string | null
                                (One concise sentence describing how the company primarily generates revenue. Not more than 40 words.)

                                OUTPUT RULES (CRITICAL)
                                - Return data strictly matching the provided response schema.
                                - Do not include explanations, comments, markdown, or free text.
                                - Populate only fields defined in the schema.
                                - Use null for every field that cannot be verified reliably.
                                - Do not add or remove fields.
                                - Do not return JSON text — return a structured object conforming to the schema.

                                INPUT
                                ISIN:
                                %s
                        """.formatted(isin);

        Tool webTool = Tool.ofWebSearch(WebSearchTool.builder().type(Type.WEB_SEARCH).searchContextSize(WebSearchTool.SearchContextSize.MEDIUM).build());
        StructuredResponseCreateParams<StockFundamentals> params = ResponseCreateParams.builder()
                                                                                       .model(ChatModel.of("gpt-5-mini"))
                                                                                       .input(systemPrompt)
                                                                                       .text(StockFundamentals.class)
                                                                                       .tools(List.of(webTool))
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
            StockFundamentals result = StocksAgent.execute(isin);
            FundamentalsService fundamentalsService = new FundamentalsService();
            fundamentalsService.upsert(result);
            System.out.println("Analysis complete. Resulting Stock Fundamentals:");
            System.out.println(result);
        }
        catch (Exception e)
        {
            System.err.println("Error occurred while analyzing stock: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
