package com.missioncontrol.assignment.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Offering one crew member one place on a mission.
 *
 * <p>Two ids and nothing else. The mission comes from the path, the offerer is the caller, and the
 * instant is the server's - none of the three is a field, because a request that can name its own
 * author is a request that can lie about one.
 *
 * @param crewRequirementId which staffing line the place is on
 * @param crewMemberId      the crew profile being offered it - the same id feature 06's
 *                          {@code CandidateResponse} carries, so a suggestion can be offered
 *                          straight from the match board
 */
@Schema(description = "Offer one crew member a place against one of the mission's requirements.")
record OfferAssignmentRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "crewRequirementId is required")
        UUID crewRequirementId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "crewMemberId is required")
        UUID crewMemberId) {
}
