package com.msg.ppap.exchange.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Exchange records of a case with their steps as JSON-compatible values (audit export, UC-11). Implemented by the
 * exchange module; used by the audit module.
 */
public interface CaseExchangeRecords {

    /** Records of the case, oldest first, without payloads. */
    List<Map<String, Object>> recordsOfCase(UUID caseId);
}
