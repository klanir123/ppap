package com.msg.ppap.approval.application.port.in;

/**
 * Customer side: a signed {@code PpapSubmission} of a supplier arrives (UC-06). Validates schema, sender, exchange id
 * and signature; the revision is stored with its canonical payload and the case becomes SUBMITTED. Idempotent by
 * revision and payload hash.
 */
public interface ReceiveSubmissionUseCase {

    InboundResult receiveSubmission(InboundMessage message);
}
