package com.missioncontrol.mission.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Putting a director's decision between a planned mission and a crewed one.
 *
 * <p>Its own controller rather than five more methods on {@link MissionController}, for the reason
 * {@link CrewRequirementController} already gives: the authorisation is different, and the two get
 * a tag each in the API document. It is sharper here - this is the only place in the product where
 * a {@code DIRECTOR} role check appears, and the only place the roles genuinely divide.
 *
 * <p>{@code PreAuthorize} carries BR-3, because whether the caller is a director needs no mission
 * in hand. It does not weaken the tenant rules: a director from another organisation passes the
 * role check and is then told the mission does not exist, because the query is tenant-scoped.
 * Submit and replan carry <strong>no</strong> annotation on purpose - the owning lead cannot be
 * identified without the mission, and a role expression here would turn the 404 a non-owning lead
 * is supposed to get into a 403 that confirms the mission exists.
 *
 * <p>Method names matter. springdoc derives {@code operationId} from them and that becomes the
 * function name in the committed TypeScript client, so renaming one renames part of the frontend.
 */
@RestController
@RequestMapping("/api/missions/{id}")
@Validated
@Tag(name = "Mission approval",
        description = "Submitting a plan for approval, and a director's decision on it.")
class MissionApprovalController {

    private final MissionApprovalService approvals;

    MissionApprovalController(MissionApprovalService approvals) {
        this.approvals = approvals;
    }

    @PostMapping("/submit")
    @Operation(
            summary = "Submit a mission for approval",
            description = "Moves a PLAN mission to PENDING_APPROVAL and opens an approval cycle "
                    + "for a director to decide. The mission must have at least one crew "
                    + "requirement: without one it would be vacuously fully staffed and could be "
                    + "started the moment it was approved.")
    @ApiResponse(responseCode = "200", description = "The mission, now PENDING_APPROVAL")
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "The caller does not own this mission",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such mission the caller can see",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Not in PLAN, or it has no crew requirements",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    MissionResponse submitMission(@PathVariable UUID id) {
        return approvals.submit(id);
    }

    @PostMapping("/approve")
    @PreAuthorize("hasRole('DIRECTOR')")
    @Operation(
            summary = "Approve a mission",
            description = "Moves a PENDING_APPROVAL mission to APPROVED and records the decision. "
                    + "A director may approve any mission in their organisation; because directors "
                    + "cannot own missions, they can never be approving their own work.")
    @ApiResponse(responseCode = "200", description = "The mission, now APPROVED")
    @ApiResponse(responseCode = "400", description = "The note is too long",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "The caller is not a director",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such mission the caller can see",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409",
            description = "Not PENDING_APPROVAL - including because another director decided it "
                    + "first. The response carries currentStatus, so a stale view can be told "
                    + "from a genuine mistake.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    MissionResponse approveMission(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ApproveMissionRequest request) {

        return approvals.approve(id, request == null ? new ApproveMissionRequest(null) : request);
    }

    @PostMapping("/reject")
    @PreAuthorize("hasRole('DIRECTOR')")
    @Operation(
            summary = "Reject a mission",
            description = "Moves a PENDING_APPROVAL mission to REJECTED. The comment is required: "
                    + "a rejected plan that does not say why leaves its lead guessing. The mission "
                    + "can then be returned to planning and resubmitted, or closed.")
    @ApiResponse(responseCode = "200", description = "The mission, now REJECTED")
    @ApiResponse(responseCode = "400", description = "The comment is missing, blank or too long",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "The caller is not a director",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such mission the caller can see",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409",
            description = "Not PENDING_APPROVAL - including because another director decided it "
                    + "first",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    MissionResponse rejectMission(@PathVariable UUID id,
                                  @Valid @RequestBody RejectMissionRequest request) {
        return approvals.reject(id, request);
    }

    @PostMapping("/replan")
    @Operation(
            summary = "Return a rejected mission to planning",
            description = "Moves a REJECTED mission back to PLAN so it can be revised and "
                    + "resubmitted. The approval history is left intact and the next submission "
                    + "opens a new cycle. Owner-only: the route out of a rejected mission for a "
                    + "director is to close it.")
    @ApiResponse(responseCode = "200", description = "The mission, back in PLAN")
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "The caller does not own this mission",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such mission the caller can see",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "The mission is not REJECTED",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    MissionResponse replanMission(@PathVariable UUID id) {
        return approvals.replan(id);
    }

    @GetMapping("/approvals")
    @Operation(
            summary = "List a mission's approval history",
            description = "Every submit-and-decide cycle, newest first. Returned whole rather than "
                    + "paged: a mission gains a cycle only when a plan is sent back and "
                    + "resubmitted, so the list stays small, and the screen showing it needs the "
                    + "count before it can render.")
    @ApiResponse(responseCode = "200", description = "The history, newest first")
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such mission the caller can see",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    List<MissionApprovalResponse> listMissionApprovals(@PathVariable UUID id) {
        return approvals.history(id);
    }
}
