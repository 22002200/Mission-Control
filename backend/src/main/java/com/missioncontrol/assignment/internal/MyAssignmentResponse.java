package com.missioncontrol.assignment.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One of the caller's own assignments - FR-3.
 *
 * <p>Mission-first, unlike {@link AssignmentResponse}, and that is the whole difference between the
 * two. A mission lead reading their staffing view knows the mission and wants to know who; a crew
 * member reading this knows who they are and wants to know which mission. Naming the crew member
 * in every row of a list of your own assignments would be noise.
 *
 * @param id              the assignment
 * @param status          where the offer has got to
 * @param offeredAt       when it was made, UTC
 * @param respondedAt     when it was settled, UTC; null while still {@code OFFERED}
 * @param mission         what it is a place on
 * @param requirementTitle the staffing line, for example Flight Engineer
 */
@Schema(description = "An assignment as the crew member holding it sees it.")
record MyAssignmentResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        AssignmentStatus status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant offeredAt,

        @Schema(description = "Null while the offer is still open.")
        Instant respondedAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        MissionRef mission,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Flight Engineer")
        String requirementTitle) {
}
