package com.missioncontrol.identity.internal;

import com.missioncontrol.platform.JwtClaims;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Rejects a token that predates its user's {@code tokensValidFrom} - requirement FR-5.
 *
 * <p>This is how logout takes effect without a token blacklist: logging out stamps an instant on
 * the user, and every token minted before it stops being accepted. It follows that logout
 * invalidates <em>all</em> of that user's tokens, not just the one presented.
 *
 * <p><strong>Why this shape.</strong> The check needs identity's data, but it runs inside
 * {@code platform}'s decoder, and {@code platform} may not depend on {@code identity} - that is the
 * cycle {@code ModularityTests} exists to catch. Contributing an {@link OAuth2TokenValidator}, a
 * type Spring Security already defines, means the seam needs no bespoke interface: {@code platform}
 * collects whatever validators the context offers and never learns who wrote this one.
 *
 * <p>The comparison uses {@link JwtClaims#ISSUED_AT_MILLIS} rather than the standard {@code iat},
 * which is truncated to whole seconds and would make a token minted just after a logout look older
 * than it.
 *
 * <p>Every unexpected condition fails closed. One indexed read per authenticated request; no write,
 * per NFR-5.
 */
@Component
class TokensValidFromValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED = new OAuth2Error(
            "invalid_token", "The token is no longer valid.", null);

    private final UserRepository users;

    TokensValidFromValidator(UserRepository users) {
        this.users = users;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        UUID userId;
        try {
            userId = UUID.fromString(token.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }

        Long issuedAtMillis = issuedAtMillis(token);
        if (issuedAtMillis == null) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }

        Optional<TokenValidity> validity = users.findTokenValidity(userId);
        if (validity.isEmpty()) {
            // The user has been deleted since the token was minted.
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }

        TokenValidity user = validity.get();

        // Beyond the letter of FR-5, but plainly right: disabling an account should not leave its
        // existing tokens working for up to eight hours. The status is already loaded, so it costs
        // nothing.
        if (user.status() != UserStatus.ACTIVE) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }

        Instant validFrom = user.tokensValidFrom();
        if (validFrom != null && issuedAtMillis < validFrom.toEpochMilli()) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }

        return OAuth2TokenValidatorResult.success();
    }

    private static Long issuedAtMillis(Jwt token) {
        Object claim = token.getClaim(JwtClaims.ISSUED_AT_MILLIS);
        return claim instanceof Number number ? number.longValue() : null;
    }
}
