package com.missioncontrol.matching.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * One line of the why: what the requirement asked for, what the candidate has, and what that was
 * worth.
 *
 * <p>Every field is {@code REQUIRED}, which is not decoration - springdoc marks anything else
 * optional and the committed TypeScript client then types it as possibly undefined, so the frontend
 * has to null-guard a number that can never be absent.
 *
 * @param skillId      the catalogue entry
 * @param skillName    as the organisation spells it
 * @param required     the requirement's minimum
 * @param actual       the candidate's rating; 0 means they hold no rating for this skill
 * @param mandatory    whether this skill was also a hard filter
 * @param weight       how heavily it counted
 * @param contribution the unweighted term, 0 to 1
 */
@Schema(description = "One required skill and how it scored for this candidate.")
public record CandidateSkillResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID skillId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "EVA Operations")
        String skillName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The minimum the requirement asks for.", example = "3")
        int required,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "What the candidate is rated at. Zero means they hold no rating "
                        + "for this skill at all.", example = "3")
        int actual,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "True if falling short of this skill would have excluded them.")
        boolean mandatory,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        int weight,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "0 to 1. For a mandatory skill this falls as the candidate exceeds "
                        + "the minimum; for a preferred one it rises towards it.",
                example = "1.0")
        double contribution) {
}
