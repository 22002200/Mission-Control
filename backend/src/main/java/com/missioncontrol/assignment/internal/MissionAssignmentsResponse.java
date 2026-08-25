package com.missioncontrol.assignment.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * A mission's crew, requirement by requirement - FR-2.
 *
 * <p>Wrapped in an object rather than returned as a bare array. An array cannot grow a field, and
 * this response is the one feature 08's mission-lead dashboard is most likely to want a summary
 * line on.
 *
 * @param missionId    the mission asked about
 * @param requirements every staffing line on it, in the order the mission presents them
 */
@Schema(description = "Every assignment on a mission, grouped by the requirement it fills.")
record MissionAssignmentsResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID missionId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<RequirementAssignmentsResponse> requirements) {
}
