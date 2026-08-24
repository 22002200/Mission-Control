package com.missioncontrol.identity.internal;

import com.missioncontrol.platform.AuthenticatedUser;
import com.missioncontrol.platform.IssuedToken;
import com.missioncontrol.platform.TokenIssuer;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logging in, logging out, and reporting who the caller is.
 *
 * <p>No {@code AuthenticationManager} and no {@code UserDetailsService}. Spring Security's
 * authentication machinery exists to drive a login form or an HTTP Basic challenge; here it would
 * only add a layer of exception translation to fight with, given the exact problem types this API
 * has to return. A password check and a token mint are two lines.
 */
@Service
class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    /**
     * A real BCrypt hash, of a random string nobody holds, at the same cost factor as the seeded
     * hashes.
     *
     * <p>Verified against whenever the email matches no user, so that the request spends the same
     * ~100 ms in BCrypt either way. Without it an unknown email returns in microseconds and a known
     * one does not, and the difference is comfortably measurable over the network - which is
     * precisely the account-enumeration oracle NFR-2 rules out. The hash must not be of any real or
     * guessable password, or a caller could make this branch succeed.
     */
    private static final String DUMMY_HASH =
            "$2a$10$Ux5Qm1s7kZ9bT2vN4wYhOeK8jL3pR6cA1dF0gH5iJ7kM2nP4qS6tW";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final Clock clock;

    AuthenticationService(UserRepository users, PasswordEncoder passwordEncoder,
                          TokenIssuer tokenIssuer, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    LoginResponse login(String email, String rawPassword) {
        Optional<UserEntity> found = users.findByEmailIgnoringCase(email);

        // Always runs, matched or not - see DUMMY_HASH.
        String hash = found.map(UserEntity::getPasswordHash).orElse(DUMMY_HASH);
        boolean passwordMatches = passwordEncoder.matches(rawPassword, hash);

        if (found.isEmpty() || !passwordMatches) {
            // No email, no reason, nothing that distinguishes the two cases - in the log or the
            // response.
            log.info("Login failed");
            throw new InvalidCredentialsException();
        }

        UserEntity user = found.get();

        // Checked after the password, not before. The other order would let anyone confirm that a
        // given address belongs to a disabled account without knowing its password.
        if (!user.isActive()) {
            log.info("Login rejected for disabled user {}", user.getId());
            throw new AccountDisabledException();
        }

        IssuedToken token = tokenIssuer.issue(new AuthenticatedUser(
                user.getId(), user.getOrganisation().getId(), user.getRole()));

        log.info("Login succeeded for user {}", user.getId());
        return new LoginResponse(token.value(), token.expiresAt(), toResponse(user));
    }

    /**
     * Revokes every token issued to this user before now.
     *
     * <p>One write, on an endpoint called once per session - the only database write authentication
     * adds outside login.
     */
    @Transactional
    void logout(UUID userId) {
        users.findById(userId).ifPresent(user -> {
            user.revokeTokensIssuedBefore(clock.instant());
            log.info("Logout for user {}", userId);
        });
    }

    @Transactional(readOnly = true)
    CurrentUserResponse currentUser(UUID userId) {
        return users.findByIdWithOrganisation(userId)
                .map(AuthenticationService::toResponse)
                // The token was valid, so the user existed a moment ago. Treat a miss as
                // unauthenticated rather than inventing a 404 for the caller's own record.
                .orElseThrow(InvalidCredentialsException::new);
    }

    private static CurrentUserResponse toResponse(UserEntity user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getOrganisation().getId(),
                user.getOrganisation().getName());
    }
}
