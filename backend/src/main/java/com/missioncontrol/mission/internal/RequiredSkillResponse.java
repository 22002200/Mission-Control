package com.missioncontrol.mission.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * A skill a requirement calls for, with the name resolved.
 *
 * <p>The name comes from the {@code skill} module on read rather than being copied onto the row
 * when the requirement was written. A stored copy would be a second source of truth that goes
 * stale the moment the catalogue is corrected.
 */
@Schema(description = "A skill a crew requirement calls for.")
public record RequiredSkillResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID skillId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "EVA Operations")
        String skillName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Lowest acceptable proficiency, on the same 1-5 scale crew are "
                        + "rated on.", example = "3")
        int minimumProficiency,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "True filters candidates out; false only influences ranking.",
                example = "true")
        boolean mandatory,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Relative importance when ranking candidates.", example = "1")
        int weight) {
}
