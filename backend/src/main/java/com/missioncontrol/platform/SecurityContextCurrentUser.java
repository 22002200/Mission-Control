package com.missioncontrol.platform;

import com.missioncontrol.shared.UserRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Reads the caller off the validated JWT in the security context.
 *
 * <p>The single place in the application where a claim name is turned into a value. Everything
 * downstream works with {@link AuthenticatedUser}.
 */
@Component
class SecurityContextCurrentUser implements CurrentUser {

    @Override
    public AuthenticatedUser require() {
        return find().orElseThrow(() -> new IllegalStateException(
                "No authenticated user on the current request."));
    }

    @Override
    public Optional<AuthenticatedUser> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken token) || !token.isAuthenticated()) {
            return Optional.empty();
        }

        Jwt jwt = token.getToken();
        return Optional.of(new AuthenticatedUser(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaimAsString(JwtClaims.ORGANISATION_ID)),
                UserRole.valueOf(jwt.getClaimAsString(JwtClaims.ROLE))));
    }
}
