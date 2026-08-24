package com.missioncontrol.skill.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * One page of the catalogue.
 *
 * <p>A hand-written record rather than Spring Data's {@code Page}. Serialising {@code Page}
 * directly emits its internal structure - Boot logs a warning about exactly this - and that
 * structure is not part of any contract, so a Spring Data upgrade could reshape the committed
 * TypeScript client. Five fields the API actually promises are cheaper than that risk.
 */
@Schema(description = "One page of an organisation's skill catalogue.")
public record SkillPage(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<SkillResponse> content,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Zero-based page index.",
                example = "0")
        int page,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Requested page size.",
                example = "50")
        int size,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Total matching skills across every page.", example = "8")
        long totalElements,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        int totalPages) {

    static SkillPage from(Page<SkillResponse> page) {
        return new SkillPage(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
