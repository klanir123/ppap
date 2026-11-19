package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.application.FormTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Excel form templates (database content). */
@RestController
@RequestMapping("/api/v1/form-templates")
@Tag(name = "form-templates")
public class FormTemplateController {

    private final FormTemplateService templates;

    public FormTemplateController(FormTemplateService templates) {
        this.templates = templates;
    }

    @GetMapping
    @Operation(operationId = "listFormTemplates", summary = "List Excel form templates")
    public List<FormTemplateDto> list() {
        return templates.list().stream().map(ApprovalDtos::template).toList();
    }
}
