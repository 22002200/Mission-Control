package com.missioncontrol.mission.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Why a plan is being sent back - BR-6.
 *
 * <p>The comment is required, because a rejected plan that does not say why is not actionable: the
 * mission lead's only options become guessing or asking. {@code NotBlank} rather than
 * {@code NotNull} plus a minimum length, so {@code {"comment": "   "}} is refused too - whitespace
 * satisfies the letter of 'a comment was supplied' and none of its purpose.
 *
 * <p>The database states the same rule as a check constraint, so it holds for any writer.
 */
@Schema(description = "The reason a plan was sent back.")
public record RejectMissionRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                example = "The EVA line needs a second qualified operator before this can fly.")
        @NotBlank(message = "must not be blank")
        @Size(max = 1000, message = "must be at most 1000 characters")
        String comment) {
}
