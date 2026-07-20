package com.trading.marketdata.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps scraper-specific runtime environment variables to Spring datasource properties.
 *
 * <p>The scraper and stocksserver are deployed into the same external Tomcat but use
 * different database users. Using the global SPRING_DATASOURCE_* environment variables
 * would therefore affect both applications. MARKETDATA_DB_* keeps the scraper datasource
 * configuration isolated while still keeping credentials out of source control.</p>
 */
public class MarketDataDatasourceEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "marketDataDatasourceEnvironment";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> properties = new HashMap<>();

        map(environment, properties, "MARKETDATA_DB_URL", "spring.datasource.url");
        map(environment, properties, "MARKETDATA_DB_USERNAME", "spring.datasource.username");
        map(environment, properties, "MARKETDATA_DB_PASSWORD", "spring.datasource.password");

        if (!properties.isEmpty()) {
            // Highest precedence is intentional: application.properties contains safe/empty defaults,
            // while deployment-specific credentials must win at runtime.
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
        }
    }

    private static void map(ConfigurableEnvironment environment,
                            Map<String, Object> target,
                            String environmentVariable,
                            String springProperty) {
        String value = environment.getProperty(environmentVariable);
        if (value != null && !value.isBlank()) {
            target.put(springProperty, value);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
