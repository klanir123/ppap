package com.msg.ppap.approval.application.port.in;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What the own participant shares with a partner via the dataspace (phase 5, docs/api/pull-api.md) and what it
 * expects from partners. Used by the exchange module (internal EDC endpoints, pull jobs, document transfer) and the
 * twin module (twin registration). All queries are scoped to the current tenant; drafts are never shared.
 */
public interface PartnerSharingUseCase {

    /**
     * Supplier side: a submitted revision of the case with the given exchange id, if the case belongs to the given
     * partner (customer). Drafts, other partners and unknown cases yield empty.
     */
    Optional<SharedSubmission> sharedSubmission(UUID exchangeId, int revision, String partnerBpnl);

    /**
     * Local document with the given SHA-256 that is shared with the partner: evidence of a submitted revision of a
     * supplier case of that partner, or an attachment of a sent request (not a draft) of a customer case of that
     * partner.
     */
    Optional<UUID> sharedDocument(String sha256, String partnerBpnl);

    /**
     * Supplier side: data of the digital twin of a dataspace-linked case with its submitted revisions that are
     * provided by pull (exchange mode ≠ PUSH).
     */
    Optional<TwinSource> twinSource(UUID exchangeId);

    /** Customer side: own requests (source LOCAL) that wait for a (new) submission – REQUESTED or IN_PROGRESS. */
    List<AwaitedSubmission> awaitedSubmissions();

    /**
     * Partner documents referenced by the case that are not available locally: evidence of received revisions
     * (customer) or attachments of a received request (supplier).
     */
    List<PartnerDocument> partnerDocuments(UUID caseId);

    /** Own case of the exchange id in the given role ({@code CUSTOMER} or {@code SUPPLIER}). */
    Optional<CaseRef> findCase(UUID exchangeId, String ownRole);

    /** Short reference of a case. */
    record CaseRef(UUID caseId, String caseNumber, String partnerBpnl) {}

    /**
     * A submitted revision.
     *
     * @param payload canonical {@code PpapSubmission} bytes
     * @param jws detached JWS over the payload
     */
    record SharedSubmission(UUID caseId, int revision, byte[] payload, String payloadHash, String jws) {

        public SharedSubmission {
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }

    /**
     * Twin data of a supplier case.
     *
     * @param customerPartNumber customer part number ({@code customerPartId})
     * @param supplierPartNumber own part number ({@code manufacturerPartId}), {@code null} if unmapped
     * @param revisions submitted revisions provided by pull
     */
    record TwinSource(
            UUID caseId,
            String caseNumber,
            UUID exchangeId,
            String customerBpnl,
            String customerPartNumber,
            String supplierPartNumber,
            List<Integer> revisions) {

        public TwinSource {
            revisions = List.copyOf(revisions);
        }
    }

    /**
     * Customer case waiting for a submission.
     *
     * @param lastRevision latest received revision, {@code 0} if none
     */
    record AwaitedSubmission(UUID caseId, String caseNumber, UUID exchangeId, String supplierBpnl, int lastRevision) {}

    /**
     * Document of the partner referenced by the case.
     *
     * @param kind {@code EVIDENCE} or {@code ATTACHMENT}
     */
    record PartnerDocument(
            UUID caseId,
            String caseNumber,
            UUID exchangeId,
            String partnerBpnl,
            String sha256,
            String fileName,
            String mediaType,
            long size,
            String kind) {}
}
