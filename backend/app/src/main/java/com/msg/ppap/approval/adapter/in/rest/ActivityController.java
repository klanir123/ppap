package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.application.ApprovalCaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Activity feed of the dashboard (UC-11): latest timeline entries of all cases (any authenticated user). */
@RestController
@Tag(name = "approval-cases")
public class ActivityController {

    private final ApprovalCaseService cases;

    public ActivityController(ApprovalCaseService cases) {
        this.cases = cases;
    }

    @GetMapping("/api/v1/activity")
    @Operation(
            operationId = "listActivity",
            summary = "Activity feed: latest timeline entries of all cases, partner names instead of partner:<BPNL>")
    public List<ActivityDto> list(@RequestParam(defaultValue = "30") int limit) {
        return cases.activity(limit).stream().map(ApprovalDtos::activity).toList();
    }
}
