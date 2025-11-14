package com.openride.admin.config;

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
 * OpenAPI/Swagger configuration for admin service.
 */
@Configuration
public class OpenApiConfiguration {

    @Value("${server.servlet.context-path:/admin-service}")
    private String contextPath;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("OpenRide Admin Service API")
                        .description("Centralized admin service for OpenRide platform management. " +
                                "Provides comprehensive admin operations including dispute resolution, " +
                                "user suspension, audit logging, and system health monitoring.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("OpenRide Platform Team")
                                .email("admin@openride.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server().url("http://localhost:8086" + contextPath).description("Local Development"),
                        new Server().url("https://api.openride.com" + contextPath).description("Production")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT authentication. Admin role required.")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
