package com.msg.ppap.approval.domain;

/**
 * Actions on an approval case. Transition actions change the status (and create a timeline entry); edit actions
 * ({@link #EDIT}, {@link #EDIT_SUBMISSION}, {@link #REVIEW}) keep it. Inbound actions are triggered
 * by messages of the partner, never by users.
 */
public enum CaseAction {
    CREATE(false),
    CAPTURE_MANUAL(false),
    RECEIVE_REQUEST(true),
    ASSIGN_PART(false),
    EDIT(false),
    SEND(false),
    CANCEL(false),
    RECEIVE_WITHDRAWAL(true),
    START_WORK(false),
    EDIT_SUBMISSION(false),
    SUBMIT(false),
    RECEIVE_SUBMISSION(true),
    RECEIVE_STATUS_UPDATE(true),
    START_REVIEW(false),
    REVIEW(false),
    DECIDE(false),
    RECEIVE_DECISION(true),
    NEW_REVISION(false);

    private final boolean inbound;

    CaseAction(boolean inbound) {
        this.inbound = inbound;
    }

    /** Triggered by a partner message. */
    public boolean inbound() {
        return inbound;
    }
}
