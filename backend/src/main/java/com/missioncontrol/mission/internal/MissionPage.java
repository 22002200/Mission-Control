package com.missioncontrol.mission.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * One page of missions.
 *
 * <p>A hand-written record rather than Spring Data's {@code Page}, for the same reason
 * {@code SkillPage} is one: serialising {@code Page} emits its internal structure, which is not
 * part of any contract, so a Spring Data upgrade could reshape the committed TypeScript client.
 *
 * <p>The duplication with {@code SkillPage} is deliberate. A shared page type would be an entry in
 * the shared kernel, and nothing has earned that yet - two records of five fields are cheaper than
 * a premature abstraction between two modules that are otherwise unrelated.
 */
@Schema(description = "One page of missions.")
public record MissionPage(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<MissionSummaryResponse> content,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Zero-based page index.",
                example = "0")
        int page,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Requested page size.",
                example = "20")
        int size,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Total matching missions across every page.", example = "7")
        long totalElements,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        int totalPages) {

    static MissionPage from(Page<MissionSummaryResponse> page) {
        return new MissionPage(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
