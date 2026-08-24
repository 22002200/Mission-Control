package com.missioncontrol.mission.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Planning missions and driving them through the lifecycle steps that need no director.
 *
 * <p>Only create carries a role check. Every other rule here is 'the owning lead, or a director',
 * which no role expression can decide without the mission in hand, so those live in
 * {@link MissionAccess} where the mission is available. Putting half the rule in an annotation and
 * half in the service would be worse than putting all of it in one place.
 *
 * <p>Internal to the module: HTTP is a delivery detail of whoever owns the data.
 *
 * <p>Method names matter. springdoc derives {@code operationId} from them and that becomes the
 * function name in the committed TypeScript client, so renaming one renames part of the frontend.
 */
@RestController
@RequestMapping("/api/missions")
@Validated
@Tag(name = "Missions", description = "Planning missions and the crew they call for.")
class MissionController {

    /**
     * A page big enough for one lifecycle section of the mission board without paging, and small
     * enough that a hostile caller cannot ask for the table.
     */
    private static final String DEFAULT_PAGE_SIZE = "20";

    private final MissionService missions;

    MissionController(MissionService missions) {
        this.missions = missions;
    }

    @GetMapping
    @Operation(
            summary = "List missions",
            description = "Scoped by role: a mission lead sees the missions they own, a director "
                    + "sees every mission in the organisation, and a crew member sees only the "
                    + "ones they hold an assignment on. Sorted by start date.")
    @ApiResponse(responseCode = "200", description = "One page of missions")
    @ApiResponse(responseCode = "400", description = "A parameter is out of range",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    MissionPage listMissions(

            @Parameter(description = "Repeatable. Omitting it returns every status.")
            @RequestParam(required = false)
            List<MissionStatus> status,

            @Parameter(description = "Case-insensitive substring of the mission name.")
            @RequestParam(required = false) @Size(max = 200)
            String search,

            @Parameter(description = "Zero-based page index.")
            @RequestParam(defaultValue = "0") @Min(0)
            int page,

            @Parameter(description = "Entries per page.")
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(100)
            int size) {

        return missions.list(status, search, page, size);
    }

    @PostMapping
    @PreAuthorize("hasRole('MISSION_LEAD')")
    @Operation(
            summary = "Create a mission",
            description = "The new mission starts in PLAN and is owned by the caller. Directors "
                    + "cannot create missions, so a mission always has a mission lead as its "
                    + "owner.")
    @ApiResponse(responseCode = "201", description = "The new mission")
    @ApiResponse(responseCode = "400", description = "A field is invalid, or endsAt is not after "
            + "startsAt",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "The caller is not a mission lead",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ResponseStatus(HttpStatus.CREATED)
    MissionResponse createMission(@Valid @RequestBody CreateMissionRequest request) {
        return missions.create(request);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get one mission",
            description = "With its crew requirements and staffing counts. A mission the caller "
                    + "cannot see - another organisation, another lead, or one they are not "
                    + "assigned to - is reported as absent rather than as forbidden.")
    @ApiResponse(responseCode = "200", description = "The mission")
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such mission the caller can see",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    MissionResponse getMission(@PathVariable UUID id) {
        return missions.get(id);
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "Edit a mission",
            description = "Omitted fields are left as they are. Editing an APPROVED or ACTIVE "
                    + "mission returns it to PLAN, because the approval described a plan that no "
                    + "longer exists and it has to be resubmitted.")
    @ApiResponse(responseCode = "200", description = "The mission, possibly back in PLAN")
    @ApiResponse(responseCode = "400", description = "A field is invalid, or endsAt is not after "
            + "startsAt",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "The caller is neither the owner nor a "
            + "director",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such mission the caller can see",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "The mission is closed",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    MissionResponse updateMission(@PathVariable UUID id,
                                  @Valid @RequestBody UpdateMissionRequest request) {
        return missions.update(id, request);
    }

    @PostMapping("/{id}/start")
    @Operation(
            summary = "Start a mission",
            description = "Moves an APPROVED mission to ACTIVE. Every crew requirement must be "
                    + "filled first; the conflict response names the ones that are not.")
    @ApiResponse(responseCode = "200", description = "The mission, now ACTIVE")
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "The caller is neither the owner nor a "
            + "director",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such mission the caller can see",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Not APPROVED, or not fully staffed",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    MissionResponse startMission(@PathVariable UUID id) {
        return missions.start(id);
    }

    @PostMapping("/{id}/close")
    @Operation(
            summary = "Close a mission",
            description = "Closing is terminal and is reachable from any other status - this is "
                    + "also how a mission is aborted. Omitting the reason records COMPLETED for a "
                    + "mission that was ACTIVE and ABORTED for anything else.")
    @ApiResponse(responseCode = "200", description = "The mission, now CLOSED")
    @ApiResponse(responseCode = "400", description = "The reason contradicts the mission history",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "The caller is neither the owner nor a "
            + "director",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such mission the caller can see",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "The mission is already closed",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    MissionResponse closeMission(@PathVariable UUID id,
                                 @Valid @RequestBody(required = false) CloseMissionRequest request) {
        return missions.close(id, request == null
                ? new CloseMissionRequest(null, null)
                : request);
    }
}
