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

    /**
     * Human-readable form of a recorded actor (timeline, activity feed): {@code partner:<BPNL>} becomes the partner's
     * name (UC-11), all other actors (user names, {@code client:<id>}, {@code system:*}) stay as recorded.
     *
     * @param partnerName name of a partner by BPNL, if known
     */
    public static String display(
            String actor, java.util.function.Function<String, java.util.Optional<String>> partnerName) {
        if (actor == null) {
            return null;
        }
        if (actor.startsWith(PARTNER_PREFIX)) {
            String bpnl = actor.substring(PARTNER_PREFIX.length());
            return partnerName.apply(bpnl).filter(n -> !n.isBlank()).orElse(bpnl);
        }
        return actor;
    }
}
