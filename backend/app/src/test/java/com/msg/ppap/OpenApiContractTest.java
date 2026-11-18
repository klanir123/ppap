package com.msg.ppap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.msg.ppap.support.AbstractIntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Checks that documented responses follow the API conventions. */
class OpenApiContractTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void documentsConventionalResponses() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/parts'].post.responses['201'].headers.Location")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/parts'].post.responses['200']")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/parts/by-number/{partNumber}'].put.responses['201']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/parts/by-number/{partNumber}'].put.responses['200']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/parts/{id}'].delete.responses['204']")
                        .exists())
                .andExpect(jsonPath(
                                "$.paths['/api/v1/parts/{id}'].put.responses['412'].content['application/problem+json']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/parts/{id}'].put.responses['409']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/parts/{id}'].put.responses['403']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/imports/{entity}/execute'].post.responses['422']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/imports/{entity}/execute'].post.responses['413']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/config'].get.responses['401']")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.errors")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.AuditEventDto.properties.businessKey")
                        .exists())
                .andExpect(jsonPath(
                        "$.components.schemas.AuditEventDto.properties.objectType.enum",
                        Matchers.hasItem("PART_MAPPING")))
                .andExpect(jsonPath(
                        "$.components.schemas.ImportPreviewResponse.properties.suggestedMapping.additionalProperties.type",
                        Matchers.hasItem("null")));
    }
}
