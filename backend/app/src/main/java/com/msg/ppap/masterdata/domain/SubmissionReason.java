package com.msg.ppap.masterdata.domain;

import com.msg.ppap.platform.domain.Ids;
import java.util.UUID;

/** Reason for a submission (e.g. new part, product change, change of supplier). */
public final class SubmissionReason extends MasterdataAggregate<SubmissionReasonValues> {

    private SubmissionReason(UUID id, SubmissionReasonValues values, boolean active, Metadata metadata) {
        super(id, values, active, metadata);
    }

    public static SubmissionReason create(SubmissionReasonValues values, boolean active) {
        return new SubmissionReason(Ids.newId(), values, active, null);
    }

    public static SubmissionReason restore(UUID id, SubmissionReasonValues values, boolean active, Metadata metadata) {
        return new SubmissionReason(id, values, active, metadata);
    }

    @Override
    public MasterdataType type() {
        return MasterdataType.SUBMISSION_REASON;
    }

    @Override
    public String businessKey() {
        return values().code().value();
    }
}
