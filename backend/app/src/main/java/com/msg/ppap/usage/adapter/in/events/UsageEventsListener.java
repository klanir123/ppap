package com.msg.ppap.usage.adapter.in.events;

import com.msg.ppap.approval.ApprovalCaseChanged;
import com.msg.ppap.approval.SubmissionSubmitted;
import com.msg.ppap.usage.application.UsageService;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Event-based usage counting (UC-12, AK-12-1): a revision submitted by the own tenant ({@code SubmissionSubmitted})
 * or received from a supplier ({@code RECEIVE_SUBMISSION}). Asynchronous after commit with the Spring Modulith event
 * publication registry (retried on restart); counting is idempotent, so redelivery is harmless.
 */
@Component
class UsageEventsListener {

    static final String RECEIVE_SUBMISSION = "RECEIVE_SUBMISSION";

    private final UsageService usage;

    UsageEventsListener(UsageService usage) {
        this.usage = usage;
    }

    @ApplicationModuleListener
    void on(SubmissionSubmitted event) {
        if (event.revision() != null) {
            usage.count(event.caseId(), event.revision());
        }
    }

    @ApplicationModuleListener
    void on(ApprovalCaseChanged event) {
        if (RECEIVE_SUBMISSION.equals(event.action()) && event.revision() != null) {
            usage.count(event.caseId(), event.revision());
        }
    }
}
