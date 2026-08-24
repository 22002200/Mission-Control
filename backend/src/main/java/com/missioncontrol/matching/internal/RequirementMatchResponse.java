package com.missioncontrol.matching.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Ranked candidates for one requirement, with enough context to know what is left.
 *
 * <p>{@code remainingCount} is the field worth explaining. Without it a short list is ambiguous -
 * the caller cannot tell whether the pool is exhausted or whether something went wrong - and a
 * rematch control has nothing to disable itself on. It is the one thing a client genuinely cannot
 * work out for itself, since it never learns how many people passed the hard filters.
 *
 * @param requirementId  the staffing line
 * @param title          for example Flight Engineer
 * @param requiredCount  seats in total
 * @param acceptedCount  how many have accepted
 * @param offeredCount   how many hold an outstanding offer
 * @param openSeats      still to fill: required, less accepted, less offered
 * @param remainingCount eligible candidates neither excluded nor returned
 * @param candidates     best first
 */
@Schema(description = "Ranked candidates for one crew requirement.")
public record RequirementMatchResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID requirementId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Flight Engineer")
        String title,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
        int requiredCount,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
        int acceptedCount,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
        int offeredCount,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Required, less accepted, less offered. Never negative.",
                example = "2")
        int openSeats,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Eligible candidates neither excluded nor returned. Zero means a "
                        + "rematch has nothing further to offer.", example = "4")
        int remainingCount,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Best first. Empty is a valid answer, not an error.")
        List<CandidateResponse> candidates) {
}
