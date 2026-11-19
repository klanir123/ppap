package com.msg.ppap.approval.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msg.ppap.approval.config.DecisionProperties;
import com.msg.ppap.approval.domain.Actor;
import com.msg.ppap.approval.domain.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecentAuthenticationTest {

    static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");

    static ReviewService service(Long maxAge) {
        var context = new ApprovalContext(
                null, null, null, null, new DecisionProperties(false, maxAge), Clock.fixed(NOW, ZoneOffset.UTC));
        return new ReviewService(context, null, null, new DecisionProperties(false, maxAge));
    }

    static Actor user(Instant authTime) {
        return new Actor("oem.quality", Set.of(UserRole.CUSTOMER_QUALITY_ENGINEER), false, authTime);
    }

    @Test
    void checkIsOffWithoutConfiguration() {
        assertThatCode(() -> service(null).checkRecentAuthentication(user(null)))
                .doesNotThrowAnyException();
    }

    @Test
    void requiresRecentInteractiveLogin() {
        ReviewService service = service(300L);
        assertThatCode(() -> service.checkRecentAuthentication(user(NOW.minusSeconds(299))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> service.checkRecentAuthentication(user(NOW.minusSeconds(301))))
                .hasFieldOrPropertyWithValue("code", "RECENT_AUTH_REQUIRED")
                .isInstanceOfSatisfying(
                        RecentAuthRequiredException.class,
                        ex -> org.assertj.core.api.Assertions.assertThat(ex.properties())
                                .containsEntry("maxAuthAgeSeconds", 300L));
        assertThatThrownBy(() -> service.checkRecentAuthentication(user(null)))
                .hasFieldOrPropertyWithValue("code", "RECENT_AUTH_REQUIRED");
        assertThatCode(() -> service.checkRecentAuthentication(
                        new Actor("client:erp", Set.of(UserRole.SYSTEM_INTEGRATION), true, null)))
                .doesNotThrowAnyException();
    }
}
