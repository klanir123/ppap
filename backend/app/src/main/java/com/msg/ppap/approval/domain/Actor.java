package com.msg.ppap.approval.domain;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Acting user or system.
 *
 * @param name user name, {@code client:<id>} or technical actor
 * @param roles approval-relevant roles
 * @param technical technical client (client credentials) or system actor
 * @param authTime time of the last interactive authentication ({@code auth_time}), if known
 */
public record Actor(String name, Set<UserRole> roles, boolean technical, Instant authTime) {

    /** Actor of inbound partner messages. */
    public static final String EXCHANGE_ACTOR = "system:exchange";

    public Actor {
        Objects.requireNonNull(name, "name");
        roles = roles == null || roles.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(roles));
    }

    /** Interactive user. */
    public static Actor user(String name, UserRole... roles) {
        return new Actor(name, Set.of(roles), false, null);
    }

    /** Prefix of the actor of transitions triggered by a partner message ({@code partner:<BPNL>}). */
    public static final String PARTNER_PREFIX = "partner:";

    /** Inbound processing of partner messages (sender unknown). */
    public static Actor exchange() {
        return new Actor(EXCHANGE_ACTOR, Set.of(UserRole.EXCHANGE), true, null);
    }

    /** Remote transition triggered by a message of the partner. */
    public static Actor partner(String bpnl) {
        return new Actor(PARTNER_PREFIX + Objects.requireNonNull(bpnl, "bpnl"), Set.of(UserRole.EXCHANGE), true, null);
    }

    public boolean has(UserRole role) {
        return roles.contains(role);
    }
}
