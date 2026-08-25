package com.missioncontrol.assignment.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One assignment, as the mission's own staffing view and every command answer it.
 *
 * <p>The same shape comes back from offering, accepting, declining and withdrawing, so a client
 * can replace the row it just acted on rather than reasoning about which fields a particular verb
 * might have changed.
 *
 * @param id                the assignment
 * @param crewRequirementId the staffing line it counts against
 * @param crewMember        who holds it
 * @param status            where the offer has got to
 * @param offeredAt         when it was made, UTC
 * @param respondedAt       when it was settled, UTC; null while still {@code OFFERED}
 */
@Schema(description = "One offer of a place on a mission.")
record AssignmentResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID crewRequirementId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        CrewMemberRef crewMember,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        AssignmentStatus status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant offeredAt,

        @Schema(description = "Null while the offer is still open. Set by an acceptance, a "
                + "decline or a withdrawal alike.")
        Instant respondedAt) {

    static AssignmentResponse from(AssignmentEntity assignment, CrewMemberRef crewMember) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getCrewRequirementId(),
                crewMember,
                assignment.getStatus(),
                assignment.getOfferedAt(),
                assignment.getRespondedAt());
    }
}
