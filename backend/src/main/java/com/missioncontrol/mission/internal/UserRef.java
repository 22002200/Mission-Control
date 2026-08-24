package com.missioncontrol.mission.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * A person named on a mission.
 *
 * <p>Its own type rather than a flat {@code missionLeadId} plus {@code missionLeadName}, so the
 * generated TypeScript gets an object the UI can pass around whole, and so feature 05 can reuse
 * the shape for the director who decided an approval.
 */
@Schema(description = "A user referenced from a mission.")
public record UserRef(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Marcus Reyes")
        String fullName) {
}
