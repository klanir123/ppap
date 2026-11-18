package com.msg.ppap.identity;

import java.util.Objects;
import java.util.concurrent.Callable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Determines the actor recorded in audit events and {@code created_by}/{@code updated_by}: the user name for
 * interactive users, {@code client:<clientId>} for technical clients (client credentials) and an explicitly set
 * technical actor (e.g. {@value #SEED_ACTOR}) for background jobs.
 */
@Component
public class ActorProvider {

    /** Actor used by the seed loader. */
    public static final String SEED_ACTOR = "system:seed";

    /** Actor used when no user or technical actor is known. */
    public static final String SYSTEM_ACTOR = "system";

    private static final ScopedValue<String> TECHNICAL_ACTOR = ScopedValue.newInstance();
    private static final String SERVICE_ACCOUNT_PREFIX = "service-account-";

    /** Current actor. */
    public String currentActor() {
        if (TECHNICAL_ACTOR.isBound()) {
            return TECHNICAL_ACTOR.get();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return SYSTEM_ACTOR;
        }
        if (authentication instanceof JwtAuthenticationToken token) {
            var jwt = token.getToken();
            String username = jwt.getClaimAsString("preferred_username");
            String clientId = jwt.hasClaim("azp") ? jwt.getClaimAsString("azp") : jwt.getClaimAsString("client_id");
            if (clientId != null && (username == null || username.startsWith(SERVICE_ACCOUNT_PREFIX))) {
                return "client:" + clientId;
            }
            if (username != null && !username.isBlank()) {
                return username;
            }
            return jwt.getSubject() != null ? jwt.getSubject() : SYSTEM_ACTOR;
        }
        return authentication.getName();
    }

    /** Runs the task with a technical actor, e.g. {@value #SEED_ACTOR}. */
    public <T> T callAs(String actor, Callable<T> task) throws Exception {
        Objects.requireNonNull(actor, "actor");
        return ScopedValue.where(TECHNICAL_ACTOR, actor).call(task::call);
    }

    /** Runs the task with a technical actor. */
    public void runAs(String actor, Runnable task) {
        Objects.requireNonNull(actor, "actor");
        ScopedValue.where(TECHNICAL_ACTOR, actor).run(task);
    }
}
