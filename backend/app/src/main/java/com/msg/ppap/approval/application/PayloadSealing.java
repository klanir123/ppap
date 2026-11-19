package com.msg.ppap.approval.application;

import com.msg.ppap.approval.domain.ApprovalCase;
import com.msg.ppap.approval.domain.Decision;
import com.msg.ppap.approval.domain.Seal;
import com.msg.ppap.approval.domain.SignatureInfo;
import com.msg.ppap.approval.domain.SignatureStatus;
import com.msg.ppap.approval.domain.Submission;
import com.msg.ppap.identity.PayloadSignature;
import com.msg.ppap.identity.PayloadSigner;
import com.msg.ppap.identity.SignatureVerifier;
import com.msg.ppap.semantics.api.EncodedPayload;
import com.msg.ppap.semantics.api.ExchangeCodec;
import org.springframework.stereotype.Component;

/**
 * Seals revisions and decisions (ADR-0008): exchange format → JSON Schema validation → RFC 8785 canonical bytes →
 * SHA-256 → detached JWS (ES256) with the tenant key. Verifies stored or received seals.
 */
@Component
public class PayloadSealing {

    private final ExchangeCodec codec;
    private final ExchangeMessageMapper mapper;
    private final PayloadSigner signer;
    private final SignatureVerifier verifier;

    public PayloadSealing(
            ExchangeCodec codec, ExchangeMessageMapper mapper, PayloadSigner signer, SignatureVerifier verifier) {
        this.codec = codec;
        this.mapper = mapper;
        this.signer = signer;
        this.verifier = verifier;
    }

    /** Signed seal of a revision. */
    public Seal sealSubmission(ApprovalCase c, Submission s) {
        return signed(codec.encodeSubmission(mapper.submission(c, s)));
    }

    /** Signed seal of a decision. */
    public Seal sealDecision(ApprovalCase c, Submission s, Decision d) {
        return signed(codec.encodeDecision(mapper.decision(c, s, d)));
    }

    /** Unsigned seal of a revision (legacy import). */
    public Seal unsignedSubmission(ApprovalCase c, Submission s) {
        EncodedPayload payload = codec.encodeSubmission(mapper.submission(c, s));
        return new Seal(payload.text(), payload.hash(), SignatureInfo.NOT_SIGNED);
    }

    /** Unsigned seal of a decision (legacy import). */
    public Seal unsignedDecision(ApprovalCase c, Submission s, Decision d) {
        EncodedPayload payload = codec.encodeDecision(mapper.decision(c, s, d));
        return new Seal(payload.text(), payload.hash(), SignatureInfo.NOT_SIGNED);
    }

    /**
     * Seal of a payload received from a partner with the verification result of its signature against the
     * partner's keys.
     */
    public Seal received(EncodedPayload payload, String jws, String signerBpnl) {
        if (jws == null || jws.isBlank()) {
            return new Seal(payload.text(), payload.hash(), SignatureInfo.NOT_SIGNED);
        }
        SignatureStatus status = status(verifier.verify(payload.canonical(), jws, signerBpnl));
        return new Seal(payload.text(), payload.hash(), new SignatureInfo("ES256", verifier.keyId(jws), jws, status));
    }

    /** Verifies the signature of an own stored seal. */
    public SignatureStatus verify(Seal seal) {
        return verify(seal, null);
    }

    /**
     * Verifies the signature of a stored seal.
     *
     * @param signerBpnl BPNL of the partner who signed, {@code null} for own seals
     */
    public SignatureStatus verify(Seal seal, String signerBpnl) {
        if (seal.signature().jws() == null) {
            return SignatureStatus.NOT_SIGNED;
        }
        return status(verifier.verify(seal.payloadBytes(), seal.signature().jws(), signerBpnl));
    }

    private Seal signed(EncodedPayload payload) {
        PayloadSignature signature = signer.sign(payload.canonical());
        return new Seal(
                payload.text(),
                payload.hash(),
                new SignatureInfo(signature.alg(), signature.kid(), signature.jws(), SignatureStatus.VALID));
    }

    private static SignatureStatus status(SignatureVerifier.Result result) {
        return switch (result) {
            case VALID -> SignatureStatus.VALID;
            case INVALID -> SignatureStatus.INVALID;
            case UNKNOWN_KEY -> SignatureStatus.UNVERIFIED;
        };
    }
}
