package com.missioncontrol.platform;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The walking skeleton's one endpoint.
 *
 * <p>It exists to prove the whole vertical slice works: Postgres accepts a connection, Liquibase
 * runs, Spring boots, springdoc documents this operation, {@code openapi-ts} turns it into a typed
 * client, and React renders the result. Delete it once real modules make it redundant.
 */
@RestController
@RequestMapping("/api/system")
@Tag(name = "System", description = "Diagnostics about the running instance.")
public class SystemInfoController {

    private final Environment environment;
    private final String name;
    private final String version;

    public SystemInfoController(
            Environment environment,
            @Value("${spring.application.name}") String name,
            @Value("${missioncontrol.version}") String version) {
        this.environment = environment;
        this.name = name;
        this.version = version;
    }

    @GetMapping("/info")
    @Operation(
            summary = "Get system information",
            description = "Returns the application name, build version, active Spring profiles "
                    + "and current server time.")
    public SystemInfo getSystemInfo() {
        return new SystemInfo(
                name, version, List.of(environment.getActiveProfiles()), OffsetDateTime.now());
    }
}
