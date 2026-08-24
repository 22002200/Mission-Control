package com.missioncontrol.mission.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A crew requirement, skills included.
 *
 * <p>Serves both create and update. An update replaces the requirement wholesale rather than
 * merging: the skills are a set, and a partial update of a set has no obvious meaning - there
 * would be no way to say 'remove this one' without inventing a second syntax for it.
 *
 * <p>{@code Valid} on the list is what makes the constraints on each skill run. Without it the
 * annotations inside {@link RequiredSkillRequest} are silently ignored, which is the kind of gap
 * that only shows up as bad data much later.
 */
@Schema(description = "A staffing line, with the skills it calls for.")
public record CrewRequirementRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Flight Engineer")
        @NotBlank(message = "must not be blank")
        @Size(max = 200, message = "must be at most 200 characters")
        String title,

        @Schema(example = "Systems monitoring and in-flight repair.")
        @Size(max = 1000, message = "must be at most 1000 characters")
        String description,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "How many crew this line calls for.", example = "2")
        @NotNull(message = "must not be null")
        @Min(value = 1, message = "must be at least 1")
        Integer requiredCount,

        @Schema(description = "May be empty, though a requirement with no skills matches anyone.")
        @Valid
        @Size(max = 20, message = "must list at most 20 skills")
        List<RequiredSkillRequest> skills) {

    /** Null and absent mean the same thing here: no skills. */
    List<RequiredSkillRequest> skillsOrEmpty() {
        return skills == null ? List.of() : skills;
    }
}
