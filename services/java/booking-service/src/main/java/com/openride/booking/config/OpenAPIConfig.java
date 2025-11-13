package com.openride.booking.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for API documentation
 */
@Configuration
public class OpenAPIConfig {

    @Value("${server.port}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("OpenRide Booking Service API")
                .description("""
                    Booking and Seat Inventory Management Service
                    
                    Features:
                    - Create and manage bookings with seat holds
                    - Distributed locking for concurrency safety
                    - State machine-based booking lifecycle
                    - Payment integration and webhooks
                    - Cancellation with refund policies
                    
                    Performance:
                    - < 150ms mean latency
                    - > 100 concurrent bookings/second
                    - ACID guarantees for seat inventory
                    """)
                .version("1.0.0")
                .contact(new Contact()
                    .name("OpenRide Platform Team")
                    .email("api@openride.com"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
            .servers(List.of(
                new Server()
                    .url("http://localhost:" + serverPort + "/api")
                    .description("Local Development Server"),
                new Server()
                    .url("https://api.openride.com")
                    .description("Production Server")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT token from auth service")));
    }
}
