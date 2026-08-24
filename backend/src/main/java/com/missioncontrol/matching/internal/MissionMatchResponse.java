package com.missioncontrol.matching.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * A draft crew for a whole mission: every requirement, with candidates for its open seats.
 *
 * <p>Requirements are listed in the mission's own order, not the order the allocator considered
 * them in. Most-constrained-first is how the draft is decided, not how it should be read - a
 * mission lead expects the same list they see on the mission page.
 *
 * <p>A requirement with no open seats is present with an empty candidate list rather than omitted,
 * so one response renders the whole mission and a caller never has to work out whether a missing
 * line was fully staffed or simply not reported.
 *
 * <p>Nothing here is an offer. Feature 06 suggests and does not assign, however much the name Match
 * All implies otherwise.
 *
 * @param missionId    the mission drafted for
 * @param requirements every staffing line on it, in mission order
 */
@Schema(description = "A suggested crew for every open seat on a mission. Nothing is offered.")
public record MissionMatchResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID missionId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Every requirement on the mission, including ones with no open "
                        + "seats, in mission order.")
        List<RequirementMatchResponse> requirements) {
}
