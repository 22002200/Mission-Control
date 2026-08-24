package com.missioncontrol.matching.internal;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The three terms a score is made of, with the counts that produced two of them.
 *
 * <p>The counts are here rather than only the figures because a bonus of {@code +0.200} means
 * nothing on its own, and {@code +0.200 from 2 completed missions} means something a mission lead
 * can argue with. FR-4 asks for an explanation, and a number without its input is not one.
 *
 * @param skillScore        the weighted average across required skills, 0 to 1
 * @param experienceBonus   added for completed missions, capped
 * @param completedMissions accepted assignments on missions closed as completed
 * @param loadPenalty       subtracted for recent and upcoming work, capped, as a positive magnitude
 * @param recentAssignments what produced the penalty
 */
@Schema(description = "How a candidate's score was arrived at.")
public record CandidateBreakdown(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The weighted average across every required skill, 0 to 1.",
                example = "1.0")
        double skillScore,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0.0")
        double experienceBonus,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Accepted assignments on missions closed as completed.",
                example = "0")
        int completedMissions,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "A positive magnitude. It is subtracted from the score.",
                example = "0.0")
        double loadPenalty,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Accepted assignments starting inside the organisation's recency "
                        + "window, or in the future.", example = "0")
        int recentAssignments) {
}
