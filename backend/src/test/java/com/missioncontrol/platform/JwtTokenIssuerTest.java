package com.missioncontrol.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.missioncontrol.shared.UserRole;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** What goes onto a token, and how long it lasts. */
class JwtTokenIssuerTest {

    private static final String SECRET = "unit-test-signing-secret-of-sufficient-length";
    private static final Instant NOW = Instant.parse("2026-03-01T09:00:00Z");

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private JwtProperties properties;
    private JwtDecoder decoder;

    private static SecretKey key() {
        return new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @BeforeEach
    void setUp() {
        properties = new JwtProperties(
                "mission-control", "mission-control-web", Duration.ofHours(8), SECRET);
        NimbusJwtDecoder nimbus =
                NimbusJwtDecoder.withSecretKey(key()).macAlgorithm(MacAlgorithm.HS256).build();
        // These tests mint at a fixed instant and assert what is on the token, so the default
        // timestamp validator would only ever complain that a deliberately-dated token is not
        // current. Signature verification is untouched, which is what
        // isSignedWithTheConfiguredSecret relies on. Expiry enforcement is JwtSecurityConfig's
        // job and is covered by TokenExpiryIT.
        nimbus.setJwtValidator(candidate -> OAuth2TokenValidatorResult.success());
        decoder = nimbus;
    }

    private JwtTokenIssuer issuerAt(Instant instant) {
        return new JwtTokenIssuer(
                new NimbusJwtEncoder(new ImmutableSecret<>(key())),
                properties,
                Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void carriesTheUserOrganisationAndRole() {
        IssuedToken issued = issuerAt(NOW).issue(
                new AuthenticatedUser(USER_ID, ORG_ID, UserRole.DIRECTOR));

        Jwt jwt = decoder.decode(issued.value());

        assertThat(jwt.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(jwt.getClaimAsString(JwtClaims.ORGANISATION_ID)).isEqualTo(ORG_ID.toString());
        assertThat(jwt.getClaimAsString(JwtClaims.ROLE)).isEqualTo("DIRECTOR");
        assertThat(jwt.getClaimAsString(JwtClaimNames.ISS)).isEqualTo("mission-control");
        assertThat(jwt.getClaimAsStringList(JwtClaimNames.AUD))
                .isEqualTo(List.of("mission-control-web"));
    }

    @Test
    void expiresEightHoursAfterIssue() {
        IssuedToken issued = issuerAt(NOW).issue(
                new AuthenticatedUser(USER_ID, ORG_ID, UserRole.CREW_MEMBER));

        assertThat(issued.issuedAt()).isEqualTo(NOW);
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(8)));

        Jwt jwt = decoder.decode(issued.value());
        assertThat(jwt.getExpiresAt()).isEqualTo(issued.expiresAt());
    }

    @Test
    void carriesMillisecondPrecisionIssueTime() {
        // The standard iat claim is whole seconds. Revocation cannot be correct against that, so
        // the issuer adds iat_ms - see JwtClaims.
        Instant offSecond = Instant.parse("2026-03-01T09:00:00.640Z");

        Jwt jwt = decoder.decode(issuerAt(offSecond)
                .issue(new AuthenticatedUser(USER_ID, ORG_ID, UserRole.DIRECTOR)).value());

        Number issuedAtMillis = (Number) jwt.getClaims().get(JwtClaims.ISSUED_AT_MILLIS);
        assertThat(issuedAtMillis.longValue()).isEqualTo(offSecond.toEpochMilli());
        // And the standard claim really has lost the milliseconds, which is the whole point.
        assertThat(jwt.getIssuedAt()).isEqualTo(Instant.parse("2026-03-01T09:00:00Z"));
    }

    @Test
    void twoTokensMintedInTheSameSecondDiffer() {
        // Acceptance criterion 9 needs two distinguishable tokens for one user. Without
        // millisecond precision both logins in a single second would produce identical strings.
        AuthenticatedUser user = new AuthenticatedUser(USER_ID, ORG_ID, UserRole.DIRECTOR);

        String first = issuerAt(Instant.parse("2026-03-01T09:00:00.100Z")).issue(user).value();
        String second = issuerAt(Instant.parse("2026-03-01T09:00:00.900Z")).issue(user).value();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void isSignedWithTheConfiguredSecret() {
        String token = issuerAt(NOW)
                .issue(new AuthenticatedUser(USER_ID, ORG_ID, UserRole.DIRECTOR)).value();

        SecretKey other = new SecretKeySpec(
                "a-completely-different-signing-secret-value".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        NimbusJwtDecoder foreignNimbus =
                NimbusJwtDecoder.withSecretKey(other).macAlgorithm(MacAlgorithm.HS256).build();
        foreignNimbus.setJwtValidator(candidate -> OAuth2TokenValidatorResult.success());
        JwtDecoder foreign = foreignNimbus;

        assertThat(decoder.decode(token)).isNotNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> foreign.decode(token))
                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    }
}
