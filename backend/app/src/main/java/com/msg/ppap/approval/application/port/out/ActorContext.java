package com.msg.ppap.approval.application.port.out;

import com.msg.ppap.approval.domain.Actor;

/** Current actor (user, technical client or system) with approval-relevant roles. */
public interface ActorContext {

    Actor currentActor();
}
