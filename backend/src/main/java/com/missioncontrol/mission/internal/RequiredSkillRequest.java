package com.missioncontrol.mission.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * One skill on a crew requirement, as supplied by the caller.
 *
 * <p>Sent inline as part of the requirement rather than through endpoints of its own - FR-8. A
 * requirement and its skills are one editable thing; splitting them would let a caller leave a
 * requirement half-described between two calls.
 *
 * <p>{@code weight} is boxed so that omitting it can default to 1, which a primitive would report
 * as an explicit zero and then reject.
 */
@Schema(description = "A skill a crew requirement calls for.")
public record RequiredSkillRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Must name an active skill in the caller's own catalogue.")
        @NotNull(message = "must not be null")
        UUID skillId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
        @NotNull(message = "must not be null")
        @Min(value = 1, message = "must be at least 1")
        @Max(value = 5, message = "must be at most 5")
        Integer minimumProficiency,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "True makes this a hard filter as well as a scored term.",
                example = "true")
        @NotNull(message = "must not be null")
        Boolean mandatory,

        @Schema(description = "Ranking weight. Defaults to 1.", example = "1")
        @Min(value = 1, message = "must be at least 1")
        @Max(value = 10, message = "must be at most 10")
        Integer weight) {

    /** The default the column also carries, applied here so the service never sees a null. */
    int weightOrDefault() {
        return weight == null ? 1 : weight;
    }
}
