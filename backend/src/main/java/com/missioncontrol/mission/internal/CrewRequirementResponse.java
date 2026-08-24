package com.missioncontrol.mission.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * One staffing line, with how far it has been filled.
 *
 * <p>{@code acceptedCount} is derived on read from the assignment module and never stored - NFR-2.
 * Until feature 07 supplies a real read model it is zero for everything.
 */
@Schema(description = "A staffing line on a mission.")
public record CrewRequirementResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Flight Engineer")
        String title,

        @Schema(example = "Systems monitoring and in-flight repair.")
        String description,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "How many crew this line calls for.", example = "2")
        int requiredCount,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "How many have accepted so far. Derived from assignments, never "
                        + "stored.", example = "0")
        int acceptedCount,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<RequiredSkillResponse> skills) {
}
