package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.ChangeAction;

/** Planned action for an import row (API contract values). */
public enum ImportAction {
    CREATE,
    UPDATE,
    UNCHANGED;

    static ImportAction of(ChangeAction action) {
        if (action == null) {
            return null;
        }
        return switch (action) {
            case CREATED -> CREATE;
            case UPDATED -> UPDATE;
            case UNCHANGED -> UNCHANGED;
        };
    }
}
