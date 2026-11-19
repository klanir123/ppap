package com.msg.ppap.masterdata.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Read-only master data queries of the current tenant. Malformed keys simply yield empty results. */
public interface MasterdataQueries {

    Optional<PartInfo> findPart(UUID id);

    Optional<PartInfo> findPartByNumber(String partNumber);

    Optional<PartnerInfo> findPartner(String bpnl);

    Optional<RelationInfo> findRelation(UUID id);

    /** Relation to a partner in the given role ({@code CUSTOMER} or {@code SUPPLIER}). */
    Optional<RelationInfo> findRelation(String partnerBpnl, String partnerRole);

    Optional<PartMappingInfo> findMapping(UUID relationId, UUID partId);

    Optional<PartMappingInfo> findMappingByPartnerPartNumber(UUID relationId, String partnerPartNumber);

    /** All mappings of an own part (over all relations). */
    List<PartMappingInfo> findMappingsOfPart(UUID partId);

    Optional<CatalogElementInfo> findElement(String code);

    /** All catalogue elements (active and inactive), sorted by sort order. */
    List<CatalogElementInfo> elements();

    Optional<LevelInfo> findLevel(String code);

    Optional<ReasonInfo> findReason(String code);

    /**
     * Partners the tenant exchanges PPAP messages with via the dataspace: active, exchange ready (DSP endpoint and
     * DID) and with at least one active relation. Basis of the EDC access group {@code ppap-partners}.
     */
    List<PartnerInfo> exchangePartners();

    /** Own part. */
    record PartInfo(UUID id, String partNumber, String nameDe, String nameEn, boolean active) {}

    /**
     * Partner.
     *
     * @param roles {@code CUSTOMER} and/or {@code SUPPLIER}
     * @param exchangeReady active with DSP endpoint and DID
     * @param dspEndpoint DSP endpoint of the partner's connector, if known
     * @param did decentralized identifier (EDC participant id) of the partner, if known
     */
    record PartnerInfo(
            String bpnl,
            String name,
            Set<String> roles,
            boolean active,
            boolean exchangeReady,
            String dspEndpoint,
            String did) {}

    /**
     * Relation with partner name; {@code partnerRole} is the role of the partner.
     *
     * @param exchangeMode {@code PUSH}, {@code PULL} or {@code PUSH_NOTIFY_PULL_PAYLOAD}
     */
    record RelationInfo(
            UUID id, String partnerBpnl, String partnerName, String partnerRole, boolean active, String exchangeMode) {}

    /** Part mapping. */
    record PartMappingInfo(UUID id, UUID relationId, UUID partId, String partNumber, String partnerPartNumber) {}

    /**
     * Catalogue element.
     *
     * @param evidenceType {@code DOCUMENT}, {@code VALUES} or {@code DOCUMENT_AND_VALUES}
     * @param valueSchema JSON Schema of structured values or {@code null}
     */
    record CatalogElementInfo(
            String code,
            String nameDe,
            String nameEn,
            String evidenceType,
            Map<String, Object> valueSchema,
            int sortOrder,
            boolean active) {}

    /**
     * Submission level.
     *
     * @param requirements element code → {@code REQUIRED} | {@code ON_REQUEST} (missing = not required)
     */
    record LevelInfo(String code, String nameDe, String nameEn, boolean active, Map<String, String> requirements) {}

    /** Submission reason. */
    record ReasonInfo(String code, String nameDe, String nameEn, boolean active) {}
}
