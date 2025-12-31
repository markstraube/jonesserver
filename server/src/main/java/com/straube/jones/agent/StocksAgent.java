package com.straube.jones.agent;


import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.models.responses.WebSearchTool;
import com.openai.models.responses.Tool;
import com.openai.models.responses.WebSearchTool.Type;
import com.straube.jones.db.DayCounter;
import com.straube.jones.model.StockFundamentals;
import com.straube.jones.trader.dto.TradingAnalysisResult;
import com.straube.jones.trader.service.MarketDataService;
import com.straube.jones.trader.service.TradingIndicatorService;
import com.straube.jones.trader.service.TradingIndicatorService.Report;

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

        Tool webTool = Tool.ofWebSearch(WebSearchTool.builder()
                                                     .type(Type.WEB_SEARCH)
                                                     .searchContextSize(WebSearchTool.SearchContextSize.MEDIUM)
                                                     .build());
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


    public static TradingAnalysisResult analyzeReport(TradingIndicatorService.Report report)
    {
        String systemPrompt = """
                           You are a professional Swing Trading Analyst AI.

                           Your task is to analyze a structured technical analysis report (JSON)
                           and produce a clear, explainable Swing Trading assessment.

                           You MUST:
                           - strictly follow the provided scoring rules
                           - not invent any data
                           - not change indicator values
                           - produce valid JSON only (no markdown, no commentary)
                           - ensure the output is suitable for display in a trading dashboard
                        """;
        String userPrompt = """
                           You will receive a JSON document at the end of this prompt.

                           It contains:
                           - a stock symbol
                           - multiple technical analyses (short, standard, long term)
                           - indicators such as RSI, MACD, Bollinger Bands, Volume flags
                           - signals like BUY / SELL / HOLD

                           Your task is to:
                           1. Interpret all analyses together
                           2. Resolve conflicts between short-, medium-, and long-term signals
                           3. Compute a Swing Trading Score (0–100) using the exact rules below
                           4. Generate a structured JSON response using the schema defined below
                           5. Make the result understandable for non-technical users

                           SCORING RULES (STRICT)
                           TOTAL SCORE
                           TOTAL_SCORE =
                           0.30 x TrendScore
                           + 0.25 x MomentumScore
                           + 0.20 x VolumeScore
                           + 0.15 x VolatilityScore
                           + 0.10 x RiskRewardScore

                           - TrendScore (0–100)

                               Rules:

                               MACD > Signal -> +30
                               MACD clearly above Signal -> +40
                               Consistency bonus:
                                   all configurations bullish -> +30
                                   2 of 3 bullish -> +20
                                   1 of 3 bullish -> +10
                               Cap at 100

                           - MomentumScore (RSI-based)

                               For EACH configuration:

                               RSI RangePoints
                               40 - 55     35
                               55 - 65     25
                               65 - 70     10
                               >70         0
                               <40         0

                               Final MomentumScore = average of all configurations

                           - VolumeScore

                               Rules:

                               highVolume = true → 100
                               highVolume = false → 40
                               If RSI > 70 AND highVolume = false → subtract 20 (minimum 0)

                           - VolatilityScore (Bollinger Bands)

                               Rules:

                               Price near middle band -> 90
                               Between middle and upper band -> 60
                               Near upper band -> 30
                               Above upper band -> 10

                           - RiskRewardScore (Proxy)

                               Rules:

                               Near upper Bollinger band -> 30
                               Near middle band -> 60
                               Near lower band -> 90


                           HARD CONSTRAINTS:
                               No explanations outside JSON
                               No invented indicators
                               No future price predictions
                               Score must match component logic
                               Language: German

                           THE REPORT TO ANALYZE:

                           %s
                        """.formatted(report.toString());

        StructuredResponseCreateParams<TradingAnalysisResult> params = ResponseCreateParams.builder()
                                                                                           .model(ChatModel.of("gpt-5-mini"))
                                                                                           .instructions(systemPrompt)
                                                                                           .input(userPrompt)
                                                                                           .text(TradingAnalysisResult.class)
                                                                                           .maxOutputTokens(5000)
                                                                                           .build();

        StructuredResponse<TradingAnalysisResult> response = openai.responses().create(params);

        List<TradingAnalysisResult> resultList = new ArrayList<>();
        response.output()
                .stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .forEach(resultList::add);

        if (resultList.isEmpty())
        { return null; }

        return resultList.get(0);
    }


    public static void main(String[] args)
    {
        String symbol = "MU";

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.mariadb.jdbc.Driver");
        dataSource.setUrl("jdbc:mariadb://192.168.178.31:3306/StocksDB");
        dataSource.setUsername("stocksdb");
        dataSource.setPassword("stocksdb");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        MarketDataService marketDataService = new MarketDataService(jdbcTemplate);

        TradingIndicatorService indicatorService = new TradingIndicatorService(marketDataService);
        Report report = indicatorService.getReport(symbol, DayCounter.now());

        TradingAnalysisResult analysisResult = analyzeReport(report);

        System.out.println("Technischer Analyse-Report für " + symbol + ":\n" + analysisResult.toString());
    }
}
