package com.missioncontrol.assignment.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A mission's crew: who has been offered a place on it, and offering one.
 *
 * <p>Split from {@code AssignmentController} along the line the URLs already draw. These two are
 * addressed by mission and are a mission lead's or a director's view of staffing; the other is
 * addressed by assignment and is a crew member's view of their own commitments. One controller for
 * both would have needed a role check in every method to say which half it belonged to.
 *
 * <p>{@code PreAuthorize} on the offer carries the role half of BR-9 and no more. That the caller
 * must own <em>this</em> mission needs the mission in hand and is settled by
 * {@code MissionPlans.forStaffingUpdate} plus {@code AssignmentAccess} - the same division
 * {@code MissionApprovalController} makes. Putting the role check here is what stops a crew member
 * reaching a mission lookup at all, so they cannot learn whether a mission id is real.
 *
 * <p>The read has no {@code PreAuthorize}: owner-or-director is not a question about a role on its
 * own, and {@code MissionPlans} already answers it with the 404 and 403 the mission endpoints use.
 *
 * <p>Method names matter. springdoc derives {@code operationId} from them and that becomes the
 * function name in the committed TypeScript client, so renaming one renames part of the frontend.
 */
@RestController
@Validated
@RequiredArgsConstructor
@Tag(name = "Mission crew", description = "Offering places on a mission and seeing who holds them.")
@Slf4j
class MissionAssignmentController {

    private final AssignmentService assignments;

    @PostMapping("/api/missions/{missionId}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MISSION_LEAD')")
    @Operation(
            summary = "Offer a crew member a place",
            description = "Creates an OFFERED assignment against one of the mission's "
                    + "requirements. Only the mission lead who owns the mission may offer, and "
                    + "only while it is APPROVED. An offer reserves the seat but not the crew "
                    + "member: two leads may offer the same person clashing dates, and the clash "
                    + "is settled when one of them is accepted."
    )
    @ApiResponse(responseCode = "201", description = "The place was offered")
    @ApiResponse(responseCode = "400", description = "A required id is missing or malformed",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Not the mission lead who owns this mission",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404",
            description = "No such mission, requirement or crew member in the caller's organisation",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409",
            description = "The mission is not APPROVED, the requirement is full, or this crew "
                    + "member already holds a place on the mission",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    AssignmentResponse offerAssignment(@PathVariable final UUID missionId,
                                       @Valid @RequestBody final OfferAssignmentRequest request) {
        log.atInfo().setMessage("Request to POST assignment; missionId={}, requirementId={}")
                .addArgument(missionId).addArgument(request.crewRequirementId()).log();
        AssignmentResponse response = assignments.offer(missionId, request);
        log.atInfo().setMessage("Request to POST assignment completed; missionId={}, assignmentId={}")
                .addArgument(missionId).addArgument(response.id()).log();
        return response;
    }

    @GetMapping("/api/missions/{missionId}/assignments")
    @Operation(
            summary = "The mission's crew, by requirement",
            description = "Every requirement on the mission, each with the crew offered places on "
                    + "it. Requirements nobody has been offered are included with an empty list - "
                    + "an unstaffed line is the one most worth seeing."
    )
    @ApiResponse(responseCode = "200", description = "The mission's crew")
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Not the owning mission lead or a director",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such mission in the caller's organisation",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    MissionAssignmentsResponse listMissionAssignments(
            @PathVariable final UUID missionId,

            @Parameter(description = "Show only assignments in this status. Omit for all of them.")
            @RequestParam(required = false) final AssignmentStatus status
    ) {
        log.atInfo().setMessage("Request to GET mission assignments; missionId={}, status={}")
                .addArgument(missionId).addArgument(status).log();
        MissionAssignmentsResponse response = assignments.forMission(missionId, status);
        log.atInfo().setMessage("Request to GET mission assignments completed; missionId={}")
                .addArgument(missionId).log();
        return response;
    }
}
