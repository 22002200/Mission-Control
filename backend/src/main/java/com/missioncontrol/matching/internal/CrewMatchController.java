package com.missioncontrol.matching.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Suggesting crew for a mission.
 *
 * <p>Two ways in. Match All drafts a candidate for every open seat on the mission at once; the
 * per-requirement endpoint returns a short ranked list that can be re-run for the next batch. Both
 * are reads and neither offers anybody anything - acting on a suggestion is feature 07.
 *
 * <p>No {@code PreAuthorize}. Who may run a match is the owning mission lead or a director, which
 * is not a question about a role on its own - it needs the mission in hand. {@code MissionPlans}
 * applies exactly the rules the mission endpoints apply, using the same beans, which is also what
 * makes an absent mission and another tenant's indistinguishable.
 *
 * <p>Available in every mission status. Running a match while a mission is still in {@code PLAN} is
 * how a lead finds out whether the plan is staffable before submitting it, and it changes nothing.
 *
 * <p>Method names matter. springdoc derives {@code operationId} from them and that becomes the
 * function name in the committed TypeScript client, so renaming one renames part of the frontend.
 */
@RestController
@Validated
@RequiredArgsConstructor
@Tag(name = "Crew matching", description = "Ranked crew suggestions for a mission's requirements. Read-only.")
@Slf4j
class CrewMatchController {

    /**
     * Default number of matched crew members returned.
     */
    private static final String DEFAULT_LIMIT = "3";

    /**
     * About sixteen rematches at the default limit, which is far past any real session. This is a
     * bound on the query string rather than a product rule - see BR-11.
     */
    private static final int MAX_EXCLUDED = 50;

    private final CrewMatchingService matching;

    @GetMapping("/api/missions/{missionId}/matches")
    @Operation(
            summary = "Draft a crew for the whole mission",
            description = "Returns the highest-ranked candidates for every requirement's open "
                    + "seats. No crew member appears twice: a candidate topping two requirements "
                    + "is drafted onto the one with fewer alternatives. Nothing is offered and "
                    + "nothing is saved."
    )
    @ApiResponse(responseCode = "200", description = "A suggested crew, by requirement")
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Not the owning mission lead or a director",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such mission in the caller's organisation",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    MissionMatchResponse matchAll(@PathVariable final UUID missionId) {
        log.atInfo().setMessage("Request to GET all crew match; missionId={}").addArgument(missionId).log();
        MissionMatchResponse response = matching.matchAll(missionId);
        log.atInfo().setMessage("Request to GET all crew match completed; missionId={}").addArgument(missionId).log();
        return response;
    }

    @GetMapping("/api/missions/{missionId}/requirements/{requirementId}/matches")
    @Operation(
            summary = "Rank candidates for one requirement",
            description = "Candidates failing a mandatory skill, or already committed over the "
                    + "mission's dates, are absent rather than ranked last. Pass the crew members "
                    + "already seen or drafted as exclude to get the next batch."
    )
    @ApiResponse(responseCode = "200", description = "Ranked candidates, best first")
    @ApiResponse(responseCode = "400", description = "A parameter is out of range",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Not the owning mission lead or a director",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such mission, or no such requirement on it",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    RequirementMatchResponse matchRequirement(
            @PathVariable final UUID missionId,
            @PathVariable final UUID requirementId,

            @Parameter(description = "How many candidates to return.")
            @RequestParam(defaultValue = DEFAULT_LIMIT)
            @Min(1) @Max(10) final int limit,

            @Parameter(description = "Crew members to leave out - typically everyone already "
                    + "drafted onto this mission plus everyone already shown for this "
                    + "requirement. Unknown or ineligible ids are ignored, not rejected.")
            @RequestParam(required = false)
            @Size(max = MAX_EXCLUDED) final Set<UUID> exclude
    ) {
        log.atInfo().setMessage("Request to GET crew match; missionId={}, requirementId={}").addArgument(missionId).addArgument(requirementId).log();
        RequirementMatchResponse response = matching.matchRequirement(missionId, requirementId, limit, exclude == null ? Set.of() : exclude);
        log.atInfo().setMessage("Request to GET crew match completed; missionId={}, requirementId={}").addArgument(missionId).addArgument(requirementId).log();
        return response;
    }
}
