package com.openride.user.config;

import com.openride.commons.config.OpenApiConfig;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger) documentation configuration for User Service.
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
            new Info()
                .title("OpenRide User Service API")
                .version("1.0.0")
                .description("User management and KYC service for OpenRide platform")
                .contact(new Contact()
                    .name("OpenRide Team")
                    .email("api@openride.com"))
        );
    }
}
