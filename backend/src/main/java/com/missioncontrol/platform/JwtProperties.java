package com.missioncontrol.platform;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the tokens this application mints and accepts.
 *
 * <p>Every field is validated, so a misconfigured deployment fails at startup with a clear message
 * rather than at the first login. That is deliberate for {@code secret} in particular: there is no
 * default value anywhere in the codebase, because a signing key compiled into the build is a
 * signing key published to everyone who can read the build.
 *
 * @param issuer         the {@code iss} claim this application mints and accepts
 * @param audience       the {@code aud} claim this application accepts
 * @param accessTokenTtl how long a freshly issued access token stays valid
 * @param secret         the HMAC signing key, supplied by configuration
 */
@Validated
@ConfigurationProperties(prefix = "missioncontrol.security.jwt")
public record JwtProperties(

        @NotBlank
        String issuer,

        @NotBlank
        String audience,

        @NotNull
        Duration accessTokenTtl,

        // HS256 requires a key of at least 256 bits. Nimbus rejects anything shorter at runtime;
        // catching it here turns a puzzling startup failure into an actionable one.
        @NotBlank(message = "missioncontrol.security.jwt.secret must be set (see .env.example)")
        @Size(min = 32, message = "must be at least 32 characters - HS256 needs a 256-bit key")
        String secret) {
}
