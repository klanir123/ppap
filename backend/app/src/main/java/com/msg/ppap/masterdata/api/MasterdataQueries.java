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

    /** All relations of the tenant (active and inactive), sorted by partner and role (phase 6: topology, EDC). */
    List<RelationInfo> relations();

    /** All partners of the tenant, sorted by BPNL. */
    List<PartnerInfo> partners();

    /** Policy profile by code (phase 6, docs/api/cascade-transparency-api.md §3). */
    Optional<PolicyProfileInfo> findPolicyProfile(String code);

    /** Policy profile by id. */
    Optional<PolicyProfileInfo> findPolicyProfile(UUID id);

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
     * @param policyProfile code of the policy profile (phase 6)
     */
    record RelationInfo(
            UUID id,
            String partnerBpnl,
            String partnerName,
            String partnerRole,
            boolean active,
            String exchangeMode,
            String policyProfile) {

        public RelationInfo(
                UUID id,
                String partnerBpnl,
                String partnerName,
                String partnerRole,
                boolean active,
                String exchangeMode) {
            this(id, partnerBpnl, partnerName, partnerRole, active, exchangeMode, "default");
        }
    }

    /**
     * Policy profile (CX-0152 2025/9).
     *
     * @param accessType {@code BPN_GROUP} or {@code BPN}
     * @param additionalConstraints further usage constraints
     */
    record PolicyProfileInfo(
            UUID id,
            String code,
            String name,
            String accessType,
            boolean membershipRequired,
            String frameworkAgreement,
            List<String> usagePurposes,
            List<ConstraintInfo> additionalConstraints,
            boolean active,
            long version) {

        public PolicyProfileInfo {
            usagePurposes = List.copyOf(usagePurposes);
            additionalConstraints = List.copyOf(additionalConstraints);
        }
    }

    /**
     * Atomic constraint of a policy profile.
     *
     * @param rightOperand string or list of strings
     */
    record ConstraintInfo(String leftOperand, String operator, Object rightOperand) {}

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
