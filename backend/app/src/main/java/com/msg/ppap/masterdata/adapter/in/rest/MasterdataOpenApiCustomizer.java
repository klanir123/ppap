package com.msg.ppap.masterdata.adapter.in.rest;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.LinkedHashSet;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

/** Contract details springdoc cannot derive: unmapped import columns map to {@code null}. */
@Component
class MasterdataOpenApiCustomizer implements OpenApiCustomizer {

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        Schema<?> preview = openApi.getComponents().getSchemas().get("ImportPreviewResponse");
        if (preview == null || preview.getProperties() == null) {
            return;
        }
        Schema<?> mapping = preview.getProperties().get("suggestedMapping");
        if (mapping != null) {
            mapping.setAdditionalProperties(new JsonSchema().types(new LinkedHashSet<>(List.of("string", "null"))));
        }
    }
}
