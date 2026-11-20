package com.msg.ppap.usage.adapter.in.rest;

import com.msg.ppap.usage.application.UsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Usage counters (UC-12, CONNECTOR_ADMIN): monthly figures, CSV export and recomputation from the raw data. */
@RestController
@RequestMapping("/api/v1/usage")
@Tag(name = "usage")
@PreAuthorize("hasRole('CONNECTOR_ADMIN')")
public class UsageController {

    private final UsageService usage;

    public UsageController(UsageService usage) {
        this.usage = usage;
    }

    /** Usage of one month. */
    public record MonthUsageDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-11")
            String month,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Approval cases = submitted + received revision 1")
            long approvalCases,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Further revisions (submitted + received)")
            long revisions,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "APPROVAL_CASES_SUBMITTED, REVISIONS_SUBMITTED, APPROVAL_CASES_RECEIVED,"
                            + " REVISIONS_RECEIVED")
            Map<String, Long> details) {}

    /** Result of a recomputation. */
    public record RecomputationDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Instant recomputedAt,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Counted revisions")
            int events,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Event-based counters equal the recomputation")
            boolean consistent,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<DifferenceDto> differences) {}

    /** A corrected counter. */
    public record DifferenceDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String month,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String metric,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long before,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long after) {}

    @GetMapping
    @Operation(
            operationId = "getUsage",
            summary = "Usage per month (default: the last 12 months); legacy cases are not counted")
    public List<MonthUsageDto> report(
            @Parameter(example = "2026-01") @RequestParam(required = false) String from,
            @Parameter(example = "2026-12") @RequestParam(required = false) String to) {
        return usage.report(from, to).stream()
                .map(m -> new MonthUsageDto(m.month(), m.approvalCases(), m.revisions(), m.details()))
                .toList();
    }

    @GetMapping(path = "/export.csv", produces = "text/csv")
    @Operation(
            operationId = "exportUsageCsv",
            summary = "Usage per month as CSV (semicolon separated)",
            responses =
                    @ApiResponse(
                            responseCode = "200",
                            description = "CSV",
                            content = @Content(mediaType = "text/csv", schema = @Schema(type = "string"))))
    public ResponseEntity<byte[]> csv(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"usage.csv\"")
                .body(usage.csv(from, to).getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/recompute")
    @Operation(
            operationId = "recomputeUsage",
            summary = "Recompute all counters from the revisions (reproducibility); reports corrected counters")
    public RecomputationDto recompute() {
        UsageService.Recomputation r = usage.recompute();
        return new RecomputationDto(
                r.recomputedAt(),
                r.events(),
                r.consistent(),
                r.differences().stream()
                        .map(d -> new DifferenceDto(d.month(), d.metric(), d.before(), d.after()))
                        .toList());
    }
}
