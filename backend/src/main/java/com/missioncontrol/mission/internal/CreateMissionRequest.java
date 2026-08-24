package com.missioncontrol.mission.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * What is needed to plan a mission.
 *
 * <p>No status: a new mission is always in {@code PLAN}. No mission lead either - the owner is the
 * caller, which is what makes invariant M2 structural rather than a rule someone has to remember
 * to check. Accepting either field would be handing a client control over something the token has
 * already decided.
 *
 * <p>That {@code endsAt} must follow {@code startsAt} is not expressible with a field annotation,
 * so it is checked in the service and reported as a validation failure - see M1.
 */
@Schema(description = "A new mission.")
public record CreateMissionRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Aurora Survey")
        @NotBlank(message = "must not be blank")
        @Size(max = 200, message = "must be at most 200 characters")
        String name,

        @Schema(example = "Mapping auroral activity from low polar orbit.")
        @Size(max = 2000, message = "must be at most 2000 characters")
        String description,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-09-01T08:00:00Z")
        @NotNull(message = "must not be null")
        Instant startsAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-09-14T17:00:00Z")
        @NotNull(message = "must not be null")
        Instant endsAt) {
}
