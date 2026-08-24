package com.missioncontrol.skill.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * A skill as the API returns it.
 *
 * <p>No {@code organisationId}. Every response is already scoped to the caller's organisation, so
 * returning it would be telling the client something it supplied - and a field a client can read
 * is a field a client eventually tries to send.
 *
 * <p>{@code id}, {@code name} and {@code active} are {@code REQUIRED} so the generated TypeScript
 * properties are non-optional. {@code category} and {@code description} are genuinely optional in
 * the data model, and with {@code default-property-inclusion: non_null} they are absent from the
 * body rather than null when unset.
 */
@Schema(description = "An entry in an organisation's skill catalogue.")
public record SkillResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "EVA Operations")
        String name,

        @Schema(example = "Operations")
        String category,

        @Schema(example = "Extravehicular activity: suit handling, tethering, external repair.")
        String description,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
        boolean active) {
}
