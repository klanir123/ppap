package com.msg.ppap.platform.security;

import com.msg.ppap.platform.web.SpaRoutes;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless OAuth2 resource server security.
 *
 * <p>Tokens are validated against the JWK set of the participant realm. The JWK set is fetched from
 * {@code ppap.oidc.jwk-set-uri} (may be an internal host such as {@code keycloak:8080}) while the {@code iss} claim
 * is validated against {@code ppap.oidc.issuer-uri} (the public URL the browser sees). This avoids the classic
 * issuer/host mismatch inside Docker networks.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfiguration {

    private static final Duration JWKS_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable) // stateless bearer-token API, no cookies
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // public endpoints
                        .requestMatchers(HttpMethod.GET, "/api/v1/config")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers("/error")
                        .permitAll()
                        // TODO(phase-4): protect EDC data plane callbacks with a shared secret (ARCHITECTURE §8);
                        // no handler exists yet, so requests end in 404.
                        .requestMatchers("/internal/edc/**")
                        .permitAll()
                        // API
                        .requestMatchers("/api/**")
                        .authenticated()
                        .requestMatchers("/actuator/**")
                        .hasRole(Roles.CONNECTOR_ADMIN)
                        // bundled SPA: static resources and client-side routes
                        .requestMatchers(
                                HttpMethod.GET,
                                "/",
                                "/index.html",
                                "/assets/**",
                                "/locales/**",
                                "/*.ico",
                                "/*.png",
                                "/*.svg",
                                "/*.webmanifest",
                                "/*.txt")
                        .permitAll()
                        .requestMatchers(SpaRoutes.matcher())
                        .permitAll()
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(OidcProperties oidc) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(JWKS_TIMEOUT);
        requestFactory.setReadTimeout(JWKS_TIMEOUT);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(oidc.jwkSetUri())
                .restOperations(new RestTemplate(requestFactory))
                .build();
        // TODO(phase-2): validate audience once Keycloak clients emit an audience mapper for the connector.
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(oidc.issuerUri()));
        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties cors) {
        var source = new UrlBasedCorsConfigurationSource();
        if (cors.allowedOrigins().isEmpty()) {
            return source;
        }
        var config = new CorsConfiguration();
        config.setAllowedOrigins(cors.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Correlation-Id"));
        config.setExposedHeaders(List.of("X-Correlation-Id", "Location", "Content-Disposition"));
        config.setMaxAge(Duration.ofHours(1));
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    static JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }
}
