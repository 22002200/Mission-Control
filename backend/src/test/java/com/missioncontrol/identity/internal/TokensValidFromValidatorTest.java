package com.missioncontrol.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.missioncontrol.platform.JwtClaims;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Revocation: the check that makes logout mean something without a token blacklist.
 *
 * <p>Every case that is not clearly fine must fail closed, so the negative tests here matter more
 * than the positive one.
 */
@ExtendWith(MockitoExtension.class)
class TokensValidFromValidatorTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant LOGOUT = Instant.parse("2026-03-01T09:00:00.500Z");

    @Mock private UserRepository users;
    @InjectMocks private TokensValidFromValidator validator;

    private static Jwt token(String subject, Long issuedAtMillis) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject);
        if (issuedAtMillis != null) {
            builder.claim(JwtClaims.ISSUED_AT_MILLIS, issuedAtMillis);
        } else {
            builder.claim("placeholder", "so the claim set is not empty");
        }
        return builder.build();
    }

    private static Jwt tokenIssuedAt(Instant instant) {
        return token(USER_ID.toString(), instant.toEpochMilli());
    }

    private void userHas(Instant tokensValidFrom, UserStatus status) {
        when(users.findTokenValidity(USER_ID))
                .thenReturn(Optional.of(new TokenValidity(tokensValidFrom, status)));
    }

    private static boolean passed(OAuth2TokenValidatorResult result) {
        return !result.hasErrors();
    }

    @Test
    void aUserWhoHasNeverLoggedOutHasEveryTokenAccepted() {
        userHas(null, UserStatus.ACTIVE);

        assertThat(passed(validator.validate(tokenIssuedAt(LOGOUT.minusSeconds(3600)))))
                .isTrue();
    }

    @Test
    void aTokenIssuedBeforeLogoutIsRejected() {
        userHas(LOGOUT, UserStatus.ACTIVE);

        assertThat(passed(validator.validate(tokenIssuedAt(LOGOUT.minusMillis(1))))).isFalse();
    }

    @Test
    void aTokenIssuedAfterLogoutIsAccepted() {
        userHas(LOGOUT, UserStatus.ACTIVE);

        assertThat(passed(validator.validate(tokenIssuedAt(LOGOUT.plusMillis(1))))).isTrue();
    }

    @Test
    void aTokenIssuedAtExactlyTheLogoutInstantIsAccepted() {
        // The boundary belongs to the newer token: logout stamps the instant it happened, and a
        // token minted at that same millisecond came after it.
        userHas(LOGOUT, UserStatus.ACTIVE);

        assertThat(passed(validator.validate(tokenIssuedAt(LOGOUT)))).isTrue();
    }

    @Test
    void aTokenMintedInTheSameSecondAsTheLogoutButAfterItSurvives() {
        // This is the case that a whole-second `iat` gets wrong. Logout at 09:00:00.500, log
        // straight back in at 09:00:00.600: the new token is genuinely newer, but truncating its
        // issue time to 09:00:00 would make it look 500ms older and reject a fresh login.
        userHas(LOGOUT, UserStatus.ACTIVE);

        Jwt freshLogin = tokenIssuedAt(Instant.parse("2026-03-01T09:00:00.600Z"));

        assertThat(passed(validator.validate(freshLogin))).isTrue();
    }

    @Test
    void aTokenForADisabledUserIsRejected() {
        userHas(null, UserStatus.DISABLED);

        assertThat(passed(validator.validate(tokenIssuedAt(LOGOUT)))).isFalse();
    }

    @Test
    void aTokenForAUserWhoNoLongerExistsIsRejected() {
        when(users.findTokenValidity(USER_ID)).thenReturn(Optional.empty());

        assertThat(passed(validator.validate(tokenIssuedAt(LOGOUT)))).isFalse();
    }

    @Test
    void aTokenWithoutTheMillisecondClaimIsRejected() {
        assertThat(passed(validator.validate(token(USER_ID.toString(), null)))).isFalse();
    }

    @Test
    void aTokenWhoseSubjectIsNotAUuidIsRejected() {
        assertThat(passed(validator.validate(token("not-a-uuid", LOGOUT.toEpochMilli()))))
                .isFalse();
    }

    @Test
    void aTokenWithNoSubjectIsRejected() {
        assertThat(passed(validator.validate(token(null, LOGOUT.toEpochMilli())))).isFalse();
    }
}
