package com.missioncontrol.mission.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * An optional note recorded with a director's approval.
 *
 * <p>A separate type from {@link RejectMissionRequest} even though the shape matches, because the
 * validation genuinely differs and the generated TypeScript should say so: {@code comment?: string}
 * here, {@code comment: string} there. One shared record would make the required half a runtime
 * surprise.
 */
@Schema(description = "An optional note recorded with an approval.")
public record ApproveMissionRequest(

        @Schema(example = "Approved. Confirm the launch window with range control.")
        @Size(max = 1000, message = "must be at most 1000 characters")
        String comment) {
}
