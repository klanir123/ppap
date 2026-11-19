package com.msg.ppap.exchange.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Path of the submodel at the EDR endpoint, derived from the descriptor's href. */
class SubmissionPullPathTest {

    static final UUID ID = UUID.fromString("0963e641-70f5-4542-83e8-de75ebcfce9f");

    @Test
    void usesThePartAfterThePublicApi() {
        assertThat(SubmissionPullService.submodelPath("http://tier1-edc-dp:8081/api/public/" + ID + "/2", ID, 2))
                .isEqualTo("/" + ID + "/2");
        assertThat(SubmissionPullService.submodelPath("http://dp/api/public/custom/x", ID, 2))
                .isEqualTo("/custom/x");
        assertThat(SubmissionPullService.submodelPath("http://dp/api/public", ID, 2))
                .isEqualTo("/" + ID + "/2");
        assertThat(SubmissionPullService.submodelPath("https://other/submodel", ID, 1))
                .isEqualTo("/" + ID + "/1");
        assertThat(SubmissionPullService.submodelPath(null, ID, 1)).isEqualTo("/" + ID + "/1");
        assertThat(SubmissionPullService.messageId(ID, 1))
                .isEqualTo(SubmissionPullService.messageId(ID, 1))
                .isNotEqualTo(SubmissionPullService.messageId(ID, 2));
    }
}
