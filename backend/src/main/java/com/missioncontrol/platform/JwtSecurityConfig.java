package com.missioncontrol.platform;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * The cryptographic half of authentication: minting, verifying and password hashing.
 *
 * <p>Both the encoder and the decoder live here so the signing secret is read in exactly one place.
 * A domain module asks {@link TokenIssuer} for a token and never handles the key.
 */
@Configuration
public class JwtSecurityConfig {

    private SecretKey hmacKey(JwtProperties properties) {
        return new SecretKeySpec(
                properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(hmacKey(properties)));
    }

    /**
     * Verifies signature, expiry, issuer and audience - and then whatever else the application has
     * to say about a token.
     *
     * <p>The {@link ObjectProvider} is the seam that lets the {@code identity} module reject tokens
     * predating a user's {@code tokensValidFrom} without {@code platform} ever referring to
     * {@code identity}. The contract is a Spring Security type both modules already know, so no
     * bespoke interface has to exist, and a failure surfaces through the framework's own path as a
     * 401. Same shape as the {@code CorsConfigurationSource} lookup in {@link SecurityConfig}.
     */
    @Bean
    public JwtDecoder jwtDecoder(
            JwtProperties properties, ObjectProvider<OAuth2TokenValidator<Jwt>> contributed) {

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(hmacKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>(List.of(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(properties.issuer()),
                new JwtClaimValidator<List<String>>(
                        JwtClaimNames.AUD,
                        audience -> audience != null && audience.contains(properties.audience()))));

        contributed.orderedStream().forEach(validators::add);

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    /**
     * Maps the {@code role} claim to the single authority Spring Security checks.
     *
     * <p>A user has exactly one role (invariant I2), so authorisation is one comparison rather than
     * a set intersection.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtClaims.ROLE);
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Cost 10, matching the seeded hashes in db/changelog/modules/identity.
        return new BCryptPasswordEncoder();
    }

    /** Injected wherever 'now' is needed, so tests can move time without waiting for it. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
