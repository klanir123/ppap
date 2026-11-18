package com.msg.ppap.masterdata.domain;

import com.msg.ppap.platform.domain.Ids;
import java.util.UUID;

/** Submission level (e.g. VDA level 0–4, AIAG level 1–5) with its element requirement matrix. */
public final class SubmissionLevel extends MasterdataAggregate<SubmissionLevelValues> {

    private SubmissionLevel(UUID id, SubmissionLevelValues values, boolean active, Metadata metadata) {
        super(id, values, active, metadata);
    }

    public static SubmissionLevel create(SubmissionLevelValues values, boolean active) {
        return new SubmissionLevel(Ids.newId(), values, active, null);
    }

    public static SubmissionLevel restore(UUID id, SubmissionLevelValues values, boolean active, Metadata metadata) {
        return new SubmissionLevel(id, values, active, metadata);
    }

    /** Whether the matrix references the given element code. */
    public boolean references(CatalogCode elementCode) {
        return values().requirements().containsKey(elementCode);
    }

    @Override
    public MasterdataType type() {
        return MasterdataType.SUBMISSION_LEVEL;
    }

    @Override
    public String businessKey() {
        return values().code().value();
    }
}
