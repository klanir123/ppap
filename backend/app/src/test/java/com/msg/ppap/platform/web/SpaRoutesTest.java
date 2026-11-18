package com.msg.ppap.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SpaRoutesTest {

    @ParameterizedTest
    @ValueSource(strings = {"/cases", "/cases/123", "/masterdata/parts/abc-1", "/settings/"})
    void clientRoutesAreSpaRoutes(String path) {
        assertThat(SpaRoutes.isSpaPath(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/",
                "/index.html",
                "/assets/index-abc.js",
                "/favicon.ico",
                "/api/v1/me",
                "/api",
                "/actuator/health",
                "/v3/api-docs",
                "/swagger-ui/index.html",
                "/internal/edc/callback",
                "/error"
            })
    void backendPathsAndFilesAreNotSpaRoutes(String path) {
        assertThat(SpaRoutes.isSpaPath(path)).isFalse();
    }
}
