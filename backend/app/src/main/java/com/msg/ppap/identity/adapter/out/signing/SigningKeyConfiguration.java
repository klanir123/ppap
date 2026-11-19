package com.msg.ppap.identity.adapter.out.signing;

import com.msg.ppap.identity.application.port.out.SigningKeyProvider;
import com.msg.ppap.identity.config.SigningProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** Selects the {@link SigningKeyProvider} by {@code ppap.signing.provider}. */
@Configuration(proxyBeanMethods = false)
class SigningKeyConfiguration {

    @Bean
    SigningKeyProvider signingKeyProvider(SigningProperties properties, JsonMapper jsonMapper) {
        return switch (properties.provider()) {
            case VAULT -> new VaultSigningKeyProvider(properties.vault(), jsonMapper);
            case FILE -> new FileSigningKeyProvider(properties.keyFile());
            case GENERATED -> new GeneratedSigningKeyProvider();
        };
    }
}
