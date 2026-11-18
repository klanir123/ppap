package com.msg.ppap.platform.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * CORS settings, only needed when the SPA is served from another origin (e.g. the Vite dev server).
 *
 * @param allowedOrigins allowed origins ({@code PPAP_CORS_ALLOWED_ORIGINS}, comma separated); empty disables CORS
 */
@ConfigurationProperties("ppap.cors")
public record CorsProperties(@DefaultValue List<String> allowedOrigins) {}
