package com.straube.jones.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web configuration for Swagger UI redirection
 */
@Configuration
public class WebConfig
    implements
    WebMvcConfigurer
{
    private static final String SWAGGER_UI_PATH = "/swagger-ui.html";

    /**
     * Redirect root URL to Swagger UI
     */
    @Override
    public void addViewControllers(@NonNull
    ViewControllerRegistry registry)
    {
        // Redirect root path to Swagger UI
        registry.addRedirectViewController("/", SWAGGER_UI_PATH);

        // Also handle common variations
        registry.addRedirectViewController("/index.html", SWAGGER_UI_PATH);
        registry.addRedirectViewController("/api", SWAGGER_UI_PATH);
        registry.addRedirectViewController("/docs", SWAGGER_UI_PATH);
    }
}
