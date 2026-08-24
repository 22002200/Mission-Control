package com.missioncontrol.mission.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Why a mission is being closed.
 *
 * <p>Both fields are optional. Omitting the reason takes the sensible default for where the
 * mission is - {@code COMPLETED} from {@code ACTIVE}, {@code ABORTED} from anywhere else - which
 * is BR-11. Stating it explicitly overrides that, except that {@code REJECTED} is only accepted
 * for a mission that really was rejected, since a close reason that contradicts the history is
 * worse than no reason at all.
 */
@Schema(description = "Optional detail for closing a mission.")
public record CloseMissionRequest(

        @Schema(description = "Defaults to COMPLETED from ACTIVE and ABORTED from anywhere else.",
                example = "ABORTED")
        MissionCloseReason closeReason,

        @Schema(example = "Launch window missed; rescheduling next quarter.")
        @Size(max = 1000, message = "must be at most 1000 characters")
        String comment) {
}
