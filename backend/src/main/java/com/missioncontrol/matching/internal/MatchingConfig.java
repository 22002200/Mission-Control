package com.missioncontrol.matching.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the module's scoring constants.
 *
 * <p>{@code EnableConfigurationProperties} here rather than a scan at the application root, which
 * is how {@code SecurityConfig} registers {@code JwtProperties}: a module declares the
 * configuration it owns, so nothing outside it has to know the property namespace exists.
 */
@Configuration
@EnableConfigurationProperties(MatchingProperties.class)
class MatchingConfig {
}
