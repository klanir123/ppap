package com.msg.ppap.approval.application.port.out;

import com.msg.ppap.approval.domain.FormTemplate;
import java.util.List;
import java.util.Optional;

/** Persistence of Excel form templates; scoped to the current tenant. */
public interface FormTemplateRepository {

    List<FormTemplate> findAll();

    Optional<FormTemplate> findByCode(String code);

    /**
     * Inserts or replaces the template by code.
     *
     * @return whether the template was created, updated or unchanged
     */
    UpsertOutcome upsert(FormTemplate template);

    /** Outcome of an upsert. */
    enum UpsertOutcome {
        CREATED,
        UPDATED,
        UNCHANGED
    }
}
