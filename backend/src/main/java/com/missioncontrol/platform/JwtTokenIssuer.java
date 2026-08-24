package com.missioncontrol.platform;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Mints HS256 tokens carrying the caller's identity, tenant and role.
 *
 * <p>The {@link Clock} is injected rather than reached for statically so that a test can mint a
 * token that expired hours ago without waiting hours - see {@code TokenExpiryIT}.
 */
@Component
public class JwtTokenIssuer implements TokenIssuer {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtTokenIssuer(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public IssuedToken issue(AuthenticatedUser user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.userId().toString())
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(JwtClaims.ORGANISATION_ID, user.organisationId().toString())
                .claim(JwtClaims.ROLE, user.role().name())
                // Millisecond precision, because `iat` above is truncated to whole seconds and
                // revocation cannot be correct against a second-resolution instant. See JwtClaims.
                .claim(JwtClaims.ISSUED_AT_MILLIS, issuedAt.toEpochMilli())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new IssuedToken(value, issuedAt, expiresAt);
    }
}
