package com.msg.ppap.approval.application;

import com.msg.ppap.approval.DocumentTransferStatus;
import com.msg.ppap.approval.domain.ApprovalCase;
import com.msg.ppap.approval.domain.Completeness;
import com.msg.ppap.approval.domain.Submission;
import java.util.Map;

/**
 * A revision with its completeness and the case (version for ETag).
 *
 * @param transfers transfer state of partner documents by SHA-256 (phase 5)
 */
public record SubmissionView(
        ApprovalCase approvalCase,
        Submission submission,
        Completeness completeness,
        Map<String, DocumentTransferStatus.Transfer> transfers) {

    public SubmissionView {
        transfers = transfers == null ? Map.of() : Map.copyOf(transfers);
    }

    public SubmissionView(ApprovalCase approvalCase, Submission submission, Completeness completeness) {
        this(approvalCase, submission, completeness, Map.of());
    }
}
