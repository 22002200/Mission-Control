package com.missioncontrol.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.missioncontrol.shared.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Turning a validated token back into the three facts the application scopes everything by. */
class SecurityContextCurrentUserTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final SecurityContextCurrentUser currentUser = new SecurityContextCurrentUser();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UUID userId, UUID organisationId, UserRole role) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim(JwtClaims.ORGANISATION_ID, organisationId.toString())
                .claim(JwtClaims.ROLE, role.name())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of()));
    }

    @Test
    void readsUserOrganisationAndRoleFromTheToken() {
        authenticateAs(USER_ID, ORG_ID, UserRole.MISSION_LEAD);

        AuthenticatedUser caller = currentUser.require();

        assertThat(caller.userId()).isEqualTo(USER_ID);
        assertThat(caller.organisationId()).isEqualTo(ORG_ID);
        assertThat(caller.role()).isEqualTo(UserRole.MISSION_LEAD);
    }

    @Test
    void convenienceAccessorsAgreeWithTheRecord() {
        authenticateAs(USER_ID, ORG_ID, UserRole.DIRECTOR);

        assertThat(currentUser.userId()).isEqualTo(USER_ID);
        assertThat(currentUser.organisationId()).isEqualTo(ORG_ID);
        assertThat(currentUser.role()).isEqualTo(UserRole.DIRECTOR);
    }

    @Test
    void isEmptyWhenThereIsNoAuthentication() {
        assertThat(currentUser.find()).isEmpty();
        assertThatThrownBy(currentUser::require).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void isEmptyForAnAnonymousRequest() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(currentUser.find()).isEmpty();
    }

    @Test
    void organisationComesOnlyFromTheToken() {
        // The point of FR-8: there is no setter, no override, and no request parameter that can
        // change the answer. Two different tokens give two different tenants and nothing else can.
        UUID otherOrg = UUID.fromString("33333333-3333-3333-3333-333333333333");

        authenticateAs(USER_ID, ORG_ID, UserRole.DIRECTOR);
        assertThat(currentUser.organisationId()).isEqualTo(ORG_ID);

        authenticateAs(USER_ID, otherOrg, UserRole.DIRECTOR);
        assertThat(currentUser.organisationId()).isEqualTo(otherOrg);
    }

    @Test
    void unauthenticatedJwtTokenIsNotTreatedAsAuthenticated() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(USER_ID.toString())
                .claims(claims -> claims.putAll(Map.of(
                        JwtClaims.ORGANISATION_ID, ORG_ID.toString(),
                        JwtClaims.ROLE, "DIRECTOR")))
                .build();

        JwtAuthenticationToken token = new JwtAuthenticationToken(jwt);
        token.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(token);

        assertThat(currentUser.find()).isEmpty();
    }
}
