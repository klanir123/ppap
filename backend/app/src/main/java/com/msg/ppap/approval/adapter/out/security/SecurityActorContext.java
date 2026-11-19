package com.msg.ppap.approval.adapter.out.security;

import com.msg.ppap.approval.application.port.out.ActorContext;
import com.msg.ppap.approval.domain.Actor;
import com.msg.ppap.approval.domain.UserRole;
import com.msg.ppap.identity.ActorProvider;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Current actor from Spring Security: name via {@link ActorProvider}, roles from the realm role authorities
 * ({@code ROLE_…}), technical flag for client credentials and system actors, {@code auth_time} claim for the recent
 * authentication check of decisions. The internal role {@code EXCHANGE} is never derived from tokens.
 */
@Component
class SecurityActorContext implements ActorContext {

    private static final String PREFIX = "ROLE_";

    private final ActorProvider actors;

    SecurityActorContext(ActorProvider actors) {
        this.actors = actors;
    }

    @Override
    public Actor currentActor() {
        String name = actors.currentActor();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Set<UserRole> roles = EnumSet.noneOf(UserRole.class);
        Instant authTime = null;
        if (authentication != null && authentication.isAuthenticated()) {
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                String value = authority.getAuthority();
                if (value != null && value.startsWith(PREFIX)) {
                    role(value.substring(PREFIX.length()), roles);
                }
            }
            if (authentication instanceof JwtAuthenticationToken token) {
                authTime = authTime(token.getToken().getClaims().get("auth_time"));
            }
        }
        boolean technical = name.startsWith("client:") || name.startsWith("system");
        return new Actor(name, roles, technical, authTime);
    }

    private static void role(String name, Set<UserRole> roles) {
        if (UserRole.EXCHANGE.name().equals(name)) {
            return;
        }
        for (UserRole role : UserRole.values()) {
            if (role.name().equals(name)) {
                roles.add(role);
            }
        }
    }

    static Instant authTime(Object claim) {
        return switch (claim) {
            case Instant instant -> instant;
            case Number seconds -> Instant.ofEpochSecond(seconds.longValue());
            case String text when text.matches("\\d+") -> Instant.ofEpochSecond(Long.parseLong(text));
            case null, default -> null;
        };
    }
}
