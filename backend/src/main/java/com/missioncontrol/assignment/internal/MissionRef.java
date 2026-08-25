package com.missioncontrol.assignment.internal;

import com.missioncontrol.mission.api.MissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Enough of a mission for a crew member to recognise what they are being asked to join.
 *
 * <p>None of this is this module's data - it comes from {@code mission.api.MissionWindows}, in one
 * bulk lookup for the whole page. It is folded into the response rather than left as a bare
 * {@code missionId} because an id tells a crew member nothing, and making the client fetch each
 * mission separately would be the N+1 NFR-4 forbids, moved to the browser.
 *
 * <p>The dates are what {@code timeframe} filters on, and they are the mission's rather than the
 * assignment's: an offer made months ago for a flight next year is upcoming, not past.
 *
 * @param id       the mission
 * @param name     as it is displayed
 * @param status   where it is in its lifecycle
 * @param startsAt inclusive start, UTC
 * @param endsAt   end, UTC
 */
@Schema(description = "The mission an assignment is on.")
record MissionRef(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Perihelion Watch")
        String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        MissionStatus status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant startsAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant endsAt) {
}
