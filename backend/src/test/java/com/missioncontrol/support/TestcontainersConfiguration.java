package com.missioncontrol.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * A real PostgreSQL for the integration tests.
 *
 * <p>Declared as a {@code @Bean} rather than with {@code @Testcontainers} and {@code @Container}.
 * That extension stops the container in {@code afterAll} of every test class, so an inherited
 * static container would be restarted once per class - the slowest possible arrangement. As a bean
 * its lifecycle belongs to the application context, and Spring's context cache keeps that context
 * alive across every test sharing this configuration: one container, started once, for the whole
 * run.
 *
 * <p>{@link ServiceConnection} points {@code spring.datasource.*} at it, so no property plumbing is
 * needed. Liquibase then builds the schema and loads the seed data exactly as it does in
 * production, which means these tests also verify the migrations and - since
 * {@code ddl-auto: validate} is inherited - that the JPA mappings match the real schema.
 *
 * <p>The image is pinned to the same major version as {@code compose.yaml}. Testing against a
 * different Postgres from the one that gets run is a good way to be surprised later.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:18-alpine");
    }
}
