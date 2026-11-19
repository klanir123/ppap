package com.msg.ppap.approval.application;

import com.msg.ppap.approval.domain.ApprovalCase;
import com.msg.ppap.approval.domain.CaseAction;
import com.msg.ppap.approval.domain.Completeness;
import java.time.LocalDate;
import java.util.List;

/**
 * A case with the actions allowed for the current actor.
 *
 * @param draftCompleteness completeness of the draft revision, {@code null} without draft
 */
public record CaseView(
        ApprovalCase approvalCase, List<CaseAction> allowedActions, LocalDate today, Completeness draftCompleteness) {}
