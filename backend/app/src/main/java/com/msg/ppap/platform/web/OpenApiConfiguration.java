package com.msg.ppap.platform.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
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

    /** Name of the RFC 9457 problem schema. */
    public static final String PROBLEM_SCHEMA = "ProblemDetail";

    /** Name of the field violation schema used in problems. */
    public static final String FIELD_ERROR_SCHEMA = "FieldError";

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
                                        .bearerFormat("JWT"))
                        .addSchemas(FIELD_ERROR_SCHEMA, fieldErrorSchema())
                        .addSchemas(PROBLEM_SCHEMA, problemSchema()))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    /** RFC 9457 problem as produced by {@code GlobalExceptionHandler}. */
    static Schema<?> problemSchema() {
        return new ObjectSchema()
                .description("RFC 9457 problem details")
                .addProperty("type", new StringSchema().format("uri"))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema().format("int32"))
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema().format("uri"))
                .addProperty("code", new StringSchema().description("Stable error code, e.g. VALIDATION_FAILED"))
                .addProperty("correlationId", new StringSchema().description("X-Correlation-Id of the request"))
                .addProperty(
                        "errors",
                        new ArraySchema()
                                .description("Field violations (validation problems only)")
                                .items(new Schema<>().$ref("#/components/schemas/" + FIELD_ERROR_SCHEMA)))
                .required(List.of("status", "code"));
    }

    static Schema<?> fieldErrorSchema() {
        return new ObjectSchema()
                .addProperty("field", new StringSchema())
                .addProperty("code", new StringSchema().description("e.g. REQUIRED, INVALID_FORMAT, UNKNOWN_CODE"))
                .addProperty("message", new StringSchema())
                .required(List.of("field", "code", "message"));
    }
}
