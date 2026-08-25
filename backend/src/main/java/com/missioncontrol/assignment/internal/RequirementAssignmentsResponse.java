package com.missioncontrol.assignment.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * One staffing line and everyone who has been offered a place on it - FR-2.
 *
 * <p>Grouped by requirement rather than returned as a flat list, because the question a lead brings
 * to this screen is per line: is this one filled, and if not, by how much. A flat list would make
 * the client do that grouping, and every client would do it slightly differently.
 *
 * <p>A requirement with nobody on it still appears, with an empty list. That is the line most worth
 * seeing, and omitting it would make an unstaffed mission look like a short one.
 *
 * @param requirementId the staffing line
 * @param title         for example Flight Engineer
 * @param requiredCount seats in total
 * @param acceptedCount how many have accepted - what invariant M11 measures before a mission starts
 * @param offeredCount  how many hold an outstanding offer; with the above, what A2 caps
 * @param assignments   every non-terminal and terminal assignment on the line, oldest offer first
 */
@Schema(description = "One requirement, with the crew offered places on it.")
record RequirementAssignmentsResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID requirementId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Flight Engineer")
        String title,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
        int requiredCount,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        int acceptedCount,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        int offeredCount,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<AssignmentResponse> assignments) {
}
