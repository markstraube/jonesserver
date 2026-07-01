package com.trading.marketdata.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Market Data Scraper API",
                version = "1.0",
                description = "Intraday market data aggregator for active trading. " +
                              "Scrapes quote, options, short interest and news data from " +
                              "Yahoo Finance, Finviz, Barchart and MarketChameleon.",
                contact = @Contact(name = "Trading API", email = "trading@example.com")
        )
)
public class SwaggerConfig {
}
