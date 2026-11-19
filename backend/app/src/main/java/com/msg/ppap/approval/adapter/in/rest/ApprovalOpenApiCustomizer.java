package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.platform.web.OpenApiConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

/**
 * Contract details springdoc cannot derive: structured element values are any JSON value, and state-dependent
 * operations answer {@code 422} for violated business rules (incompleteness, decision rules, guards).
 */
@Component
class ApprovalOpenApiCustomizer implements OpenApiCustomizer {

    static final List<String> ANY_VALUE_SCHEMAS =
            List.of("ElementEvidenceDto", "ElementEvidenceRequest", "FormElementDto");

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() != null && openApi.getComponents().getSchemas() != null) {
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            for (String name : ANY_VALUE_SCHEMAS) {
                Schema<?> schema = schemas.get(name);
                if (schema != null
                        && schema.getProperties() != null
                        && schema.getProperties().containsKey("values")) {
                    String description = schema.getProperties().get("values").getDescription();
                    schema.getProperties().put("values", new JsonSchema().description(description));
                }
            }
        }
        nullableEnum(openApi, "ElementEvidenceRequest", "selfAssessment");
        if (openApi.getPaths() == null) {
            return;
        }
        openApi.getPaths().forEach((path, item) -> {
            if (!path.startsWith("/api/v1/approval-cases/{id}")) {
                return;
            }
            item.readOperationsMap().forEach((method, operation) -> {
                if (method == PathItem.HttpMethod.POST || method == PathItem.HttpMethod.PUT) {
                    unprocessable(operation);
                }
            });
        });
    }

    /** {@code type: [string, null]} needs {@code null} in the enum as well (JSON Schema 2020-12). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void nullableEnum(OpenAPI openApi, String schemaName, String property) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        Schema<?> schema = openApi.getComponents().getSchemas().get(schemaName);
        if (schema == null || schema.getProperties() == null) {
            return;
        }
        Schema prop = schema.getProperties().get(property);
        if (prop != null && prop.getEnum() != null && !prop.getEnum().contains(null)) {
            prop.getEnum().add(null);
        }
    }

    private static void unprocessable(Operation operation) {
        if (operation.getResponses() == null || operation.getResponses().containsKey("422")) {
            return;
        }
        operation
                .getResponses()
                .addApiResponse(
                        "422",
                        new ApiResponse()
                                .description("Business rule violated (e.g. INCOMPLETE_SUBMISSION, "
                                        + "REJECTION_REASON_REQUIRED, PARTNER_NOT_EXCHANGE_READY)")
                                .content(new Content()
                                        .addMediaType(
                                                "application/problem+json",
                                                new MediaType()
                                                        .schema(new Schema<>()
                                                                .$ref("#/components/schemas/"
                                                                        + OpenApiConfiguration.PROBLEM_SCHEMA)))));
    }
}
