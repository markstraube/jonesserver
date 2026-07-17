package com.trading.marketdata.news.config;
import com.openai.client.OpenAIClient; import com.openai.client.okhttp.OpenAIOkHttpClient; import org.springframework.context.annotation.*;
@Configuration
public class OpenAiNewsConfig {
 @Bean @Conditional(OpenAiNewsEnabledCondition.class) OpenAIClient openAIClient(){return OpenAIOkHttpClient.fromEnv();}
}
