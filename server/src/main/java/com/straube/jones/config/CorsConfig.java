package com.straube.jones.config;


import java.util.Arrays;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Central CORS setup so the Angular dev server can call the REST API.
 */
@Configuration
public class CorsConfig
    implements
    WebMvcConfigurer
{

    private static final String[] DEFAULT_ORIGINS = {"http://localhost:4200"};

    private final @NonNull String[] allowedOrigins;

    public CorsConfig(@Value("${cors.allowed-origins:http://localhost:4200}")
    String originsProperty)
    {
        String[] parsedOrigins = Arrays.stream(originsProperty.split(","))
                                       .map(String::trim)
                                       .filter(origin -> !origin.isEmpty())
                                       .toArray(String[]::new);
        this.allowedOrigins = Objects.requireNonNull(parsedOrigins.length == 0 ? DEFAULT_ORIGINS
                        : parsedOrigins);
    }


    @Override
    public void addCorsMappings(@NonNull
    CorsRegistry registry)
    {
        registry.addMapping("/api/**")
                .allowedOrigins(Objects.requireNonNull(allowedOrigins))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
