package com.missioncontrol.mission.internal;

import com.missioncontrol.mission.api.MissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A mission and everything needed to plan it.
 *
 * <p>No organisation id. Every response is already scoped to the organisation on the token, so
 * returning it would only echo back what the caller supplied - and a field a client can read is a
 * field a client eventually tries to send.
 *
 * <p>{@code closeReason} appears only on a closed mission, and with
 * {@code default-property-inclusion: non_null} it is absent from the body rather than null
 * otherwise. That is invariant M4 visible in the wire format.
 */
@Schema(description = "A mission with its crew requirements.")
public record MissionResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Aurora Survey")
        String name,

        @Schema(example = "Mapping auroral activity from low polar orbit.")
        String description,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "PLAN")
        MissionStatus status,

        @Schema(description = "Set only once the mission is CLOSED.", example = "COMPLETED")
        MissionCloseReason closeReason,

        @Schema(description = "The note recorded when the mission was closed, if any.")
        String closeComment,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "UTC instant the mission begins.", example = "2026-09-01T08:00:00Z")
        Instant startsAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "UTC instant the mission ends.", example = "2026-09-14T17:00:00Z")
        Instant endsAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UserRef missionLead,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "True when every requirement has as many accepted assignments as it "
                        + "asks for. A mission with no requirements is not fully staffed.",
                example = "false")
        boolean fullyStaffed,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<CrewRequirementResponse> requirements) {
}
