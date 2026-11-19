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
                .andExpect(jsonPath("$.paths['/api/v1/approval-cases'].post.responses['201'].headers.Location")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/approval-cases/{id}/decision'].post.responses['422']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/approval-cases/{id}/send'].post.responses['412']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/documents'].post.responses['415']")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.ElementEvidenceDto.properties.values.type")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.ApprovalCaseDto.properties.allowedActions.items.enum",
                        Matchers.hasItems("EDIT", "REVIEW", "NEW_REVISION")))
                .andExpect(jsonPath(
                        "$.components.schemas.AuditEventDto.properties.objectType.enum",
                        Matchers.hasItems("APPROVAL_CASE", "DOCUMENT")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/audit-events'].get.parameters[?(@.name == 'objectType')].schema.enum[*]",
                        Matchers.hasItems("APPROVAL_CASE", "DOCUMENT")))
                .andExpect(jsonPath("$.components.schemas.DocumentRefDto.required", Matchers.hasItem("id")))
                .andExpect(jsonPath("$.components.schemas.CasePartDto.required", Matchers.hasItem("id")))
                .andExpect(jsonPath("$.components.schemas.DecisionRequest.properties.result.enum")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.ElementFindingRequest.properties.result.enum")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.RequestedElementInput.properties.requirement.enum")
                        .exists())
                .andExpect(jsonPath(
                        "$.components.schemas.ElementEvidenceRequest.properties.selfAssessment.type",
                        Matchers.hasItem("null")))
                .andExpect(jsonPath(
                        "$.components.schemas.ImportPreviewResponse.properties.suggestedMapping.additionalProperties.type",
                        Matchers.hasItem("null")));
    }
}
