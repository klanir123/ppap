package com.msg.ppap.approval.application;

import com.msg.ppap.approval.application.port.out.CaseSummary;
import com.msg.ppap.approval.domain.CaseAction;
import java.time.LocalDate;
import java.util.List;

/** List entry with the actions allowed for the current actor. */
public record SummaryView(CaseSummary summary, List<CaseAction> allowedActions, LocalDate today) {}
