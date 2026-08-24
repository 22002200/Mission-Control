package com.missioncontrol.matching.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * One suggested crew member.
 *
 * <p>Only eligible people appear. Someone excluded by a hard filter is absent rather than ranked
 * last with a reason, because explaining every person in the organisation who cannot do the job
 * would make the payload mostly noise and bury the three names the lead actually has to choose
 * between.
 *
 * <p>{@code shortfalls} is a subset of {@code skills} rather than a second source of truth - the
 * preferred skills held below their minimum, pulled out because that is the one thing a lead
 * scanning a list wants without expanding anything.
 *
 * @param crewMemberId the candidate's crew profile, which feature 07 will offer a place to
 * @param fullName     as the person is displayed
 * @param score        to three decimals, the figure the ordering is by
 * @param breakdown    the three terms behind it
 * @param skills       every required skill and what it was worth
 * @param shortfalls   the preferred skills this candidate falls short on
 */
@Schema(description = "A crew member who could fill the requirement, with the reasoning.")
public record CandidateResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID crewMemberId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Ada Kowalski")
        String fullName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Skill fit plus experience less load, to three decimals.",
                example = "1.0")
        double score,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        CandidateBreakdown breakdown,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Every skill the requirement asked for, whether or not it was met.")
        List<CandidateSkillResponse> skills,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Preferred skills held below the minimum, or not held at all. "
                        + "Mandatory skills never appear here - falling short of one is an "
                        + "exclusion, not a shortfall.")
        List<CandidateSkillResponse> shortfalls) {
}
