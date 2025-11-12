package com.openride.commons.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;

/**
 * Base OpenAPI configuration for OpenRide services.
 * Each service should extend this and customize as needed.
 */
public class OpenApiConfig {

    /**
     * Creates a base OpenAPI configuration.
     *
     * @param title       The API title
     * @param description The API description
     * @param version     The API version
     * @return Configured OpenAPI object
     */
    public static OpenAPI createOpenAPI(String title, String description, String version) {
        return new OpenAPI()
                .info(new Info()
                        .title(title)
                        .description(description)
                        .version(version)
                        .contact(new Contact()
                                .name("OpenRide Team")
                                .email("support@openride.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://openride.com/license")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter JWT token")));
    }
}
