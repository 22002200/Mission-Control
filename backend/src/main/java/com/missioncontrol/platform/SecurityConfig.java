package com.missioncontrol.platform;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Who may call what.
 *
 * <p>Every {@code /api/**} endpoint except login requires a valid bearer token. The token is the
 * only source of the caller's organisation - no endpoint accepts an organisation id as a
 * parameter - which is what makes tenant isolation enforceable rather than merely intended.
 *
 * <p>Role checks are per-endpoint via {@code @PreAuthorize}, enabled by
 * {@link EnableMethodSecurity}, rather than being listed here. Keeping the rule next to the method
 * it guards means adding an endpoint cannot silently inherit the wrong policy from a URL pattern.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<CorsConfigurationSource> corsConfigurationSource,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            ProblemAuthenticationEntryPoint authenticationEntryPoint,
            ProblemAccessDeniedHandler accessDeniedHandler)
            throws Exception {

        http
                // Safe to disable: this is a token API with no cookie-based session to forge
                // against. If auth ever moves to session cookies, CSRF must come back on.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .cors(cors -> corsConfigurationSource.ifAvailable(cors::configurationSource))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // The container's internal forward to /error is a fresh dispatch and would
                        // otherwise be evaluated as an unauthenticated request, turning a 500 into
                        // a baffling 401.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()

                        .anyRequest().authenticated())

                // Both hooks are required. oauth2ResourceServer installs its own
                // BearerTokenAuthenticationEntryPoint for bearer-token failures and does not
                // consult .exceptionHandling(), so setting only one of the two leaves half the
                // failures returning a body that is not problem+json.
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));

        return http.build();
    }
}
