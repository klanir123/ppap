package com.msg.ppap.masterdata.application.port.out;

import com.msg.ppap.masterdata.domain.CatalogCode;
import com.msg.ppap.masterdata.domain.CatalogElement;
import com.msg.ppap.masterdata.domain.SubmissionLevel;
import com.msg.ppap.masterdata.domain.SubmissionReason;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence of the submission catalog (elements, levels, reasons); scoped to the current tenant. Lists are
 * sorted by sort order and code.
 */
public interface CatalogRepository {

    Optional<CatalogElement> findElementById(UUID id);

    Optional<CatalogElement> findElementByCode(CatalogCode code);

    List<CatalogElement> findElements(Boolean active);

    CatalogElement save(CatalogElement element);

    void delete(CatalogElement element);

    Optional<SubmissionLevel> findLevelById(UUID id);

    Optional<SubmissionLevel> findLevelByCode(CatalogCode code);

    List<SubmissionLevel> findLevels(Boolean active);

    SubmissionLevel save(SubmissionLevel level);

    void delete(SubmissionLevel level);

    Optional<SubmissionReason> findReasonById(UUID id);

    Optional<SubmissionReason> findReasonByCode(CatalogCode code);

    List<SubmissionReason> findReasons(Boolean active);

    SubmissionReason save(SubmissionReason reason);

    void delete(SubmissionReason reason);
}
