package com.straube.jones.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Stocks Server API")
                        .version("1.1.0-SNAPSHOT")
                        .description("API für den Stocks Server mit Aktieninformationen und Datenanalyse")
                        .contact(new Contact()
                                .name("Mark Straube")
                                .email("mark@straube.com")));
    }
}