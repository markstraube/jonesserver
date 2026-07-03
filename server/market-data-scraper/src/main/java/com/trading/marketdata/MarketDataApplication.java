package com.trading.marketdata;

import com.trading.marketdata.model.QuoteData;
import com.trading.marketdata.service.QuoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class MarketDataApplication {

    private static final Logger log = LoggerFactory.getLogger(MarketDataApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(MarketDataApplication.class, args);
    }

    @Bean
    CommandLineRunner startupValidation(QuoteService quoteService) {
        return args -> {
            log.info("=== Market Data Scraper startup validation ===");
            log.info("Performing test scrape for AAPL...");
            try {
                QuoteData aapl = quoteService.getQuote("AAPL");
                if (aapl.dataAvailable()) {
                    log.info("Startup validation OK — AAPL price: {} | change: {}% | volume: {}",
                            aapl.price(), aapl.changePct(), aapl.volume());
                } else {
                    log.warn("Startup validation WARNING — AAPL data not available: {}", aapl.sourceError());
                }
            } catch (Exception e) {
                log.warn("Startup validation failed (non-fatal): {}", e.getMessage());
            }
            log.info("=== Swagger UI: http://localhost:8080/swagger-ui.html ===");
        };
    }
}
