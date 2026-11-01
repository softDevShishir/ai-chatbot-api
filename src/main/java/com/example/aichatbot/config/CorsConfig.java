package com.example.aichatbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS for a separately hosted UI. {@code CORS_ALLOWED_ORIGINS} is a
 * comma-separated list of exact origins; empty disables CORS entirely.
 * Wildcards are rejected at startup — this is a Bearer-token API, so a
 * wildcard origin combined with credentials would be unsafe.
 */
@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins:}") String originsCsv) {
        List<String> origins = originsCsv == null || originsCsv.isBlank()
                ? List.of()
                : java.util.Arrays.stream(originsCsv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();

        for (String origin : origins) {
            if (origin.contains("*")) {
                throw new IllegalStateException(
                        "CORS_ALLOWED_ORIGINS must not contain wildcards, got: " + origin);
            }
        }
        this.allowedOrigins = origins;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (!allowedOrigins.isEmpty()) {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOrigins(allowedOrigins);
            configuration.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
            configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
            source.registerCorsConfiguration("/api/**", configuration);
        }
        return source;
    }
}
