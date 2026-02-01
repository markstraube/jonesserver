package com.straube.jones.config;


import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig
{

    @Bean
    public OpenAPI customOpenAPI()
    {
        return new OpenAPI().addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                            .components(new Components().addSecuritySchemes("bearerAuth",
                                                                            new SecurityScheme().name("bearerAuth")
                                                                                                .type(SecurityScheme.Type.HTTP)
                                                                                                .scheme("bearer")
                                                                                                .bearerFormat("JWT")))
                            .info(new Info().title("Stocks Server API")
                                            .version("1.1.0-SNAPSHOT")
                                            .description("API für den Stocks Server mit Aktieninformationen und Datenanalyse")
                                            .contact(new Contact().name("Mark Straube")
                                                                  .email("mark@straube.com")));
    }
}
