package com.example.floodmonitor.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI floodMonitorOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Flood Monitor REST API")
                        .description("Hochwasser-Frühwarnsystem REST-Schnittstelle mit Simulation, Content Negotiation (JSON/XML) und Warnstufen.")
                        .version("1.0.0")
                        .contact(new Contact().name("Hochwasser-Frühwarnsystem Team")));
    }
}

