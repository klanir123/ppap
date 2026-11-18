package com.msg.ppap.identity.config;

import com.msg.ppap.identity.ActorProvider;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Fills {@code created_by}/{@code updated_by} of JPA entities with the current actor. */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "actorAuditorAware")
class JpaAuditingConfiguration {

    @Bean
    AuditorAware<String> actorAuditorAware(ActorProvider actors) {
        return () -> Optional.of(actors.currentActor());
    }
}
