package com.missioncontrol.skill.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reading an organisation's skill catalogue.
 *
 * <p>Both endpoints are open to every authenticated role - BR-4 - because mission leads need the
 * catalogue to write requirements and crew need it to read their own profile. The write endpoints,
 * which are director-only, come later.
 *
 * <p>Internal to the module: HTTP is a delivery detail of whoever owns the data.
 *
 * <p>Method names matter. springdoc derives {@code operationId} from them and that becomes the
 * function name in the committed TypeScript client, so renaming one renames part of the frontend.
 */
@RestController
@RequestMapping("/api/skills")
@Validated
@Tag(name = "Skills", description = "The organisation's controlled vocabulary of crew skills.")
class SkillController {

    /**
     * Big enough that the frontend can ask for a whole catalogue in one call - the largest seeded
     * one has eight entries - and small enough that a hostile caller cannot ask for the table.
     */
    private static final String DEFAULT_PAGE_SIZE = "50";

    private final SkillService skills;

    SkillController(SkillService skills) {
        this.skills = skills;
    }

    @GetMapping
    @Operation(
            summary = "List skills",
            description = "The caller's organisation's catalogue, sorted by name, "
                    + "case-insensitively. Both filters are optional and omitting one leaves it "
                    + "off.")
    @ApiResponse(responseCode = "200", description = "One page of the catalogue")
    @ApiResponse(responseCode = "400", description = "A parameter is out of range",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    SkillPage listSkills(

            @Parameter(description = "Return only active, or only retired, skills.")
            @RequestParam(required = false)
            Boolean active,

            @Parameter(description = "Case-insensitive substring of the name.")
            @RequestParam(required = false) @Size(max = 100)
            String search,

            @Parameter(description = "Zero-based page index.")
            @RequestParam(defaultValue = "0") @Min(0)
            int page,

            @Parameter(description = "Entries per page.")
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(200)
            int size) {

        return skills.list(active, search, page, size);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get one skill",
            description = "A skill belonging to another organisation is reported as absent, "
                    + "not as forbidden.")
    @ApiResponse(responseCode = "200", description = "The skill")
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such skill in the caller's organisation",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    SkillResponse getSkill(@PathVariable UUID id) {
        return skills.get(id);
    }
}
