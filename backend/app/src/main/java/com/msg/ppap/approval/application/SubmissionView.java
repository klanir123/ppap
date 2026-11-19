package com.msg.ppap.approval.application;

import com.msg.ppap.approval.domain.ApprovalCase;
import com.msg.ppap.approval.domain.Completeness;
import com.msg.ppap.approval.domain.Submission;

/** A revision with its completeness and the case (version for ETag). */
public record SubmissionView(ApprovalCase approvalCase, Submission submission, Completeness completeness) {}
