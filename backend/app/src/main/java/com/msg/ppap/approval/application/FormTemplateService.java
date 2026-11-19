package com.msg.ppap.approval.application;

import com.msg.ppap.approval.application.port.out.FormTemplateRepository;
import com.msg.ppap.approval.domain.FormTemplate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Excel form templates (database content, seeded from {@code form-templates.json}). */
@Service
@Transactional
public class FormTemplateService {

    private final FormTemplateRepository repository;

    public FormTemplateService(FormTemplateRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<FormTemplate> list() {
        return repository.findAll();
    }

    public FormTemplateRepository.UpsertOutcome upsert(FormTemplate template) {
        return repository.upsert(template);
    }
}
