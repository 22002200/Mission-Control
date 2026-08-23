package com.missioncontrol.platform;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Structural security configuration.
 *
 * <p><strong>This does not authenticate anyone.</strong> Every endpoint is currently open. The
 * dependencies, the filter chain and the stateless session policy are wired up so that turning on
 * real authentication is a small, contained change rather than a refactor - but until the
 * {@code TODO(auth)} below is addressed, treat this application as unauthenticated and do not
 * expose it beyond localhost.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, ObjectProvider<CorsConfigurationSource> corsConfigurationSource)
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

                // TODO(auth): replace permitAll() with a real policy once an identity module
                // exists. The intended shape is roughly:
                //
                //   .authorizeHttpRequests(auth -> auth
                //           .requestMatchers("/api/auth/**").permitAll()
                //           .requestMatchers("/actuator/health").permitAll()
                //           .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                //           .anyRequest().authenticated())
                //   .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                //
                // which needs a JwtDecoder bean built from JwtProperties.
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
