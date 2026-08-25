package com.missioncontrol.mission.internal;

import com.missioncontrol.mission.api.MissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A mission as a list entry.
 *
 * <p>Lighter than {@link MissionResponse} on purpose: no requirement detail and no skill names, so
 * a page of missions costs a fixed number of queries rather than one per row. The two totals are
 * the aggregate a card shows as 3 of 4.
 */
@Schema(description = "A mission as it appears in a list.")
public record MissionSummaryResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Aurora Survey")
        String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "PLAN")
        MissionStatus status,

        @Schema(description = "Set only once the mission is CLOSED.", example = "COMPLETED")
        MissionCloseReason closeReason,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-09-01T08:00:00Z")
        Instant startsAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-09-14T17:00:00Z")
        Instant endsAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UserRef missionLead,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Accepted assignments across every requirement.", example = "0")
        int acceptedCount,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Crew called for across every requirement.", example = "4")
        int requiredCount,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
        boolean fullyStaffed) {
}
