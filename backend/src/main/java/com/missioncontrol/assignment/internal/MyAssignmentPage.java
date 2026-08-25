package com.missioncontrol.assignment.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * One page of the caller's own assignments.
 *
 * <p>A hand-written record rather than Spring Data's {@code Page}, for the same reason
 * {@code MissionPage} and {@code SkillPage} are: serialising {@code Page} emits its internal
 * structure, which is part of no contract, so a Spring Data upgrade could reshape the committed
 * TypeScript client.
 *
 * <p>The duplication with those two is deliberate and is now three deep. A shared page type would
 * be an entry in the shared kernel, and {@code shared} is documented as taking a type only once a
 * second module genuinely needs it - which is not the same as three modules each having a page.
 * Three records of five fields are still cheaper than a shared abstraction between modules that
 * are otherwise unrelated.
 *
 * <p>The paging is applied after the timeframe filter, in memory. That is not a shortcut: FR-9's
 * timeframe is a predicate on mission dates, which belong to another module, so it cannot be a
 * database predicate here without joining a table this module does not own. {@code totalElements}
 * therefore counts what survived the filter, which is what a client showing a page count needs.
 */
@Schema(description = "One page of the caller's assignments.")
record MyAssignmentPage(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<MyAssignmentResponse> content,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Zero-based page index.",
                example = "0")
        int page,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Requested page size.",
                example = "20")
        int size,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Total matching assignments across every page.", example = "3")
        long totalElements,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        int totalPages) {
}
