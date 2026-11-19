package com.msg.ppap.approval.application;

import com.msg.ppap.approval.domain.ApprovalErrors;
import com.msg.ppap.platform.domain.PpapException;

/** The decision requires a fresh interactive login ({@code 401 RECENT_AUTH_REQUIRED}, UI re-login with max_age=0). */
public class RecentAuthRequiredException extends PpapException {

    public RecentAuthRequiredException(long maxAgeSeconds) {
        super(
                Kind.UNAUTHORIZED,
                ApprovalErrors.RECENT_AUTH_REQUIRED,
                "Decision requires a login within the last " + maxAgeSeconds + " seconds");
        withProperty("maxAuthAgeSeconds", maxAgeSeconds);
    }
}
