package com.msg.ppap.platform.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata. The generated document ({@code api/openapi.json}) is the contract for the frontend client
 * (orval), so titles, scheme names and DTO names must stay stable.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    /** Name of the bearer token security scheme. */
    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI ppapOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("msg CX PPAP Connector API")
                        .version("v1")
                        .description("REST API of the msg CX PPAP Connector backend."))
                // Relative server URL keeps the exported document independent of host and port.
                .servers(List.of(new Server().url("/")))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
