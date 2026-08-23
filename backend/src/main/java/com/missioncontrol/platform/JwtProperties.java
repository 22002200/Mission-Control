package com.missioncontrol.platform;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Placeholder configuration for JWT-based authentication.
 *
 * <p>Nothing reads these values yet - see the {@code TODO(auth)} in {@link SecurityConfig}. They
 * exist so the shape of the eventual configuration is settled and visible in
 * {@code application.yml} rather than being invented under time pressure later.
 *
 * @param issuer         the {@code iss} claim this application will mint and accept
 * @param audience       the {@code aud} claim this application will accept
 * @param accessTokenTtl how long a freshly issued access token stays valid
 */
@ConfigurationProperties(prefix = "missioncontrol.security.jwt")
public record JwtProperties(String issuer, String audience, Duration accessTokenTtl) {
}
