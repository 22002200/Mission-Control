package com.missioncontrol.platform;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Basic facts about the running application.
 *
 * <p>Every field is marked {@code REQUIRED} so the generated TypeScript type has non-optional
 * properties. Without it springdoc assumes every field is nullable and the frontend ends up
 * littered with optional chaining for values that are always present.
 *
 * @param name           the application name
 * @param version        the build version
 * @param activeProfiles Spring profiles currently active
 * @param serverTime     the server's current time
 */
@Schema(description = "Basic facts about the running Mission Control instance.")
public record SystemInfo(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "mission-control")
        String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0.0.1-SNAPSHOT")
        String version,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> activeProfiles,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime serverTime) {
}
