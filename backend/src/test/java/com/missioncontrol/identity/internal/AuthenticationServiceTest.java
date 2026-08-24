package com.missioncontrol.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.missioncontrol.platform.AuthenticatedUser;
import com.missioncontrol.platform.IssuedToken;
import com.missioncontrol.platform.TokenIssuer;
import com.missioncontrol.shared.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/** The login rules, including the ones that exist to stop the endpoint leaking information. */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T09:00:00Z");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String HASH = "$2a$10$storedhashforarealuser0000000000000000000000000000";

    @Mock private UserRepository users;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenIssuer tokenIssuer;

    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new AuthenticationService(users, passwordEncoder, tokenIssuer,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static OrganisationEntity organisation() {
        return OrganisationEntity.builder()
                .id(ORG_ID).name("Orbital Dynamics").createdAt(NOW).build();
    }

    private static UserEntity user(UserStatus status) {
        return UserEntity.builder()
                .id(USER_ID)
                .organisation(organisation())
                .email("Vera.Lindholm@orbitaldynamics.example")
                .passwordHash(HASH)
                .fullName("Vera Lindholm")
                .role(UserRole.DIRECTOR)
                .status(status)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    @Test
    void issuesATokenForCorrectCredentials() {
        when(users.findByEmailIgnoringCase("vera@x.example"))
                .thenReturn(Optional.of(user(UserStatus.ACTIVE)));
        when(passwordEncoder.matches("Password123!", HASH)).thenReturn(true);
        when(tokenIssuer.issue(any())).thenReturn(
                new IssuedToken("the-token", NOW, NOW.plusSeconds(28800)));

        LoginResponse response = service.login("vera@x.example", "Password123!");

        assertThat(response.token()).isEqualTo("the-token");
        assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(28800));
        assertThat(response.user().id()).isEqualTo(USER_ID);
        assertThat(response.user().role()).isEqualTo(UserRole.DIRECTOR);
        assertThat(response.user().organisationId()).isEqualTo(ORG_ID);
        assertThat(response.user().organisationName()).isEqualTo("Orbital Dynamics");
    }

    @Test
    void tokenCarriesTheUsersOwnOrganisationAndRole() {
        when(users.findByEmailIgnoringCase(anyString()))
                .thenReturn(Optional.of(user(UserStatus.ACTIVE)));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(tokenIssuer.issue(any())).thenReturn(new IssuedToken("t", NOW, NOW));

        service.login("vera@x.example", "Password123!");

        ArgumentCaptor<AuthenticatedUser> captor =
                ArgumentCaptor.forClass(AuthenticatedUser.class);
        verify(tokenIssuer).issue(captor.capture());

        assertThat(captor.getValue())
                .isEqualTo(new AuthenticatedUser(USER_ID, ORG_ID, UserRole.DIRECTOR));
    }

    @Test
    void wrongPasswordIsRejected() {
        when(users.findByEmailIgnoringCase(anyString()))
                .thenReturn(Optional.of(user(UserStatus.ACTIVE)));
        when(passwordEncoder.matches(anyString(), eq(HASH))).thenReturn(false);

        assertThatThrownBy(() -> service.login("vera@x.example", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    void unknownEmailStillRunsAPasswordComparison() {
        // NFR-2, asserted deterministically rather than with a stopwatch. If the service short
        // circuits on a missing user, an unknown email answers in microseconds while a known one
        // spends ~100ms in BCrypt - a gap an attacker can measure to enumerate accounts.
        when(users.findByEmailIgnoringCase(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.login("nobody@x.example", "Password123!"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoder, times(1)).matches(eq("Password123!"), anyString());
    }

    @Test
    void unknownEmailAndWrongPasswordAreIndistinguishable() {
        when(users.findByEmailIgnoringCase("nobody@x.example")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoringCase("vera@x.example"))
                .thenReturn(Optional.of(user(UserStatus.ACTIVE)));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        Throwable unknown = catchThrowable(() -> service.login("nobody@x.example", "pw"));
        Throwable wrong = catchThrowable(() -> service.login("vera@x.example", "pw"));

        assertThat(unknown).isInstanceOf(InvalidCredentialsException.class);
        assertThat(wrong).isInstanceOf(InvalidCredentialsException.class);
        assertThat(((InvalidCredentialsException) unknown).toProblemDetail())
                .isEqualTo(((InvalidCredentialsException) wrong).toProblemDetail());
    }

    @Test
    void theFallbackHashDoesNotMatchAGuessablePassword() {
        // If the dummy hash were a hash of a known password, submitting that password against a
        // non-existent address would sail through the comparison.
        when(users.findByEmailIgnoringCase(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.login("nobody@x.example", "Password123!"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void disabledUserWithTheRightPasswordIsForbidden() {
        when(users.findByEmailIgnoringCase(anyString()))
                .thenReturn(Optional.of(user(UserStatus.DISABLED)));
        when(passwordEncoder.matches(anyString(), eq(HASH))).thenReturn(true);

        assertThatThrownBy(() -> service.login("oona@x.example", "Password123!"))
                .isInstanceOf(AccountDisabledException.class);

        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    void disabledUserWithTheWrongPasswordLooksLikeAnyOtherBadLogin() {
        // The status check comes after the password check on purpose: the other order would let
        // anyone discover which addresses belong to disabled accounts without a password.
        when(users.findByEmailIgnoringCase(anyString()))
                .thenReturn(Optional.of(user(UserStatus.DISABLED)));
        when(passwordEncoder.matches(anyString(), eq(HASH))).thenReturn(false);

        assertThatThrownBy(() -> service.login("oona@x.example", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void logoutStampsTokensValidFromAndUpdatedAt() {
        UserEntity user = user(UserStatus.ACTIVE);
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));

        service.logout(USER_ID);

        assertThat(user.getTokensValidFrom()).isEqualTo(NOW);
        assertThat(user.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void logoutForAnUnknownUserIsSilent() {
        when(users.findById(USER_ID)).thenReturn(Optional.empty());

        service.logout(USER_ID);
    }

    @Test
    void currentUserReturnsTheCallersOwnRecord() {
        when(users.findByIdWithOrganisation(USER_ID))
                .thenReturn(Optional.of(user(UserStatus.ACTIVE)));

        CurrentUserResponse response = service.currentUser(USER_ID);

        assertThat(response.id()).isEqualTo(USER_ID);
        assertThat(response.fullName()).isEqualTo("Vera Lindholm");
        assertThat(response.email()).isEqualTo("Vera.Lindholm@orbitaldynamics.example");
        assertThat(response.role()).isEqualTo(UserRole.DIRECTOR);
        assertThat(response.organisationName()).isEqualTo("Orbital Dynamics");
    }
}
