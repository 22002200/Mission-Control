package com.missioncontrol.mission.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * An edit to a mission. Every field is optional; omitting one leaves it unchanged.
 *
 * <p>Null means 'leave alone' rather than 'set to null', which is the usual PATCH ambiguity. It is
 * resolved by convention instead of by a wrapper type: a blank {@code description} clears it, and
 * no other nullable field on a mission is caller-editable. Adding {@code JsonNullable} to tell the
 * two apart would pull in a dependency and reshape the generated client for one field.
 *
 * <p>Applying any of this to an {@code APPROVED} or {@code ACTIVE} mission sends it back to
 * {@code PLAN} - invariant M5. The approval described a plan that no longer exists.
 */
@Schema(description = "Fields to change on a mission. Omitted fields are left as they are.")
public record UpdateMissionRequest(

        @Schema(example = "Aurora Survey")
        @Size(min = 1, max = 200, message = "must be between 1 and 200 characters")
        String name,

        @Schema(description = "An empty string clears the description.")
        @Size(max = 2000, message = "must be at most 2000 characters")
        String description,

        @Schema(example = "2026-09-01T08:00:00Z")
        Instant startsAt,

        @Schema(example = "2026-09-14T17:00:00Z")
        Instant endsAt
) { }
