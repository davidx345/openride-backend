package com.openride.auth.config;

import com.openride.commons.config.OpenApiConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger) documentation configuration for Auth Service.
 */
@Configuration
public class OpenApiConfiguration {

    /**
     * Creates OpenAPI documentation bean.
     *
     * @return configured OpenAPI instance
     */
    @Bean
    public io.swagger.v3.oas.models.OpenAPI customOpenAPI() {
        return OpenApiConfig.createOpenAPI(
            "OpenRide Auth Service API",
            "Authentication and authorization service for OpenRide platform",
            "1.0.0"
        );
    }
}
