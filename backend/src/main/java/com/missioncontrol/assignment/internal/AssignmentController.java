package com.missioncontrol.assignment.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * An assignment as the person holding it, or the lead who made it, acts on it.
 *
 * <p>Three commands and a list, and the role split is the design. Accepting and declining are the
 * crew member's - BR-6 - and are guarded here by a role check before any row is read, so a mission
 * lead cannot even reach an assignment to answer on somebody's behalf. Withdrawing is the owning
 * mission lead's - BR-9 - and is guarded the same way, then narrowed to ownership once the mission
 * is in hand.
 *
 * <p>A director appears in neither list. They see every assignment through the mission's own
 * staffing view and act on none: their lever on a mission they disagree with is closing it, which
 * withdraws its outstanding offers as a decision about the mission rather than about one person.
 *
 * <p>None of the three takes a request body. Feature 07 originally allowed an optional reason on
 * decline and withdraw, and there was nowhere for it to live - {@code Assignment} has no column for
 * one and no response returned it - so it was dropped rather than accepted and discarded.
 *
 * <p>{@code POST} rather than {@code PATCH} for all three. Each is a named transition with its own
 * rules, not a field being set, and the URL saying which one it is keeps the audit trail in the
 * access log readable.
 *
 * <p>Method names matter. springdoc derives {@code operationId} from them and that becomes the
 * function name in the committed TypeScript client, so renaming one renames part of the frontend.
 */
@RestController
@Validated
@RequiredArgsConstructor
@Tag(name = "Assignments", description = "Accepting, declining and withdrawing a place on a mission.")
@Slf4j
class AssignmentController {

    /** Matches the paging default every other list in the application uses. */
    private static final String DEFAULT_SIZE = "20";

    private final AssignmentService assignments;

    @GetMapping("/api/assignments/me")
    @PreAuthorize("hasRole('CREW_MEMBER')")
    @Operation(
            summary = "The caller's own assignments",
            description = "Newest offer first. Filter by status, and by whether the mission is "
                    + "running now, still to come, or over - which is measured against the "
                    + "mission's dates rather than its status."
    )
    @ApiResponse(responseCode = "200", description = "One page of the caller's assignments")
    @ApiResponse(responseCode = "400", description = "A parameter is out of range",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Not a crew member",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    MyAssignmentPage listMyAssignments(
            @Parameter(description = "Show only assignments in this status. Omit for all of them.")
            @RequestParam(required = false) final AssignmentStatus status,

            @Parameter(description = "Narrow to missions running now, still to come, or over.")
            @RequestParam(required = false) final Timeframe timeframe,

            @Parameter(description = "Zero-based page index.")
            @RequestParam(defaultValue = "0") @Min(0) final int page,

            @Parameter(description = "How many to return.")
            @RequestParam(defaultValue = DEFAULT_SIZE) @Min(1) @Max(100) final int size
    ) {
        log.atInfo().setMessage("Request to GET my assignments; status={}, timeframe={}, page={}")
                .addArgument(status).addArgument(timeframe).addArgument(page).log();
        MyAssignmentPage response = assignments.mine(status, timeframe, page, size);
        log.atInfo().setMessage("Request to GET my assignments completed; returned={}")
                .addArgument(response.content().size()).log();
        return response;
    }

    @PostMapping("/api/assignments/{assignmentId}/accept")
    @PreAuthorize("hasRole('CREW_MEMBER')")
    @Operation(
            summary = "Accept a place",
            description = "Refused if the crew member has already accepted an overlapping mission "
                    + "that is not closed. That check runs here and not when the offer was made, "
                    + "so two leads may both offer the same person the same dates and only the "
                    + "first acceptance succeeds."
    )
    @ApiResponse(responseCode = "200", description = "Accepted")
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Not the crew member offered this place",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such assignment in the caller's organisation",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409",
            description = "The offer is no longer open, the requirement filled up, or this clashes "
                    + "with a mission already accepted",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    AssignmentResponse acceptAssignment(@PathVariable final UUID assignmentId) {
        log.atInfo().setMessage("Request to POST assignment accept; assignmentId={}")
                .addArgument(assignmentId).log();
        AssignmentResponse response = assignments.accept(assignmentId);
        log.atInfo().setMessage("Request to POST assignment accept completed; assignmentId={}")
                .addArgument(assignmentId).log();
        return response;
    }

    @PostMapping("/api/assignments/{assignmentId}/decline")
    @PreAuthorize("hasRole('CREW_MEMBER')")
    @Operation(
            summary = "Decline a place",
            description = "Frees the place immediately, so the mission lead can offer it to "
                    + "somebody else. Terminal: a declined offer cannot be accepted later, though "
                    + "the same crew member may be offered the mission again."
    )
    @ApiResponse(responseCode = "200", description = "Declined")
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Not the crew member offered this place",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such assignment in the caller's organisation",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "The offer is no longer open",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    AssignmentResponse declineAssignment(@PathVariable final UUID assignmentId) {
        log.atInfo().setMessage("Request to POST assignment decline; assignmentId={}")
                .addArgument(assignmentId).log();
        AssignmentResponse response = assignments.decline(assignmentId);
        log.atInfo().setMessage("Request to POST assignment decline completed; assignmentId={}")
                .addArgument(assignmentId).log();
        return response;
    }

    @PostMapping("/api/assignments/{assignmentId}/withdraw")
    @PreAuthorize("hasRole('MISSION_LEAD')")
    @Operation(
            summary = "Withdraw a place",
            description = "The owning mission lead's alone, and the only way an acceptance is "
                    + "undone. Withdrawing crew from a running mission does not send it back to "
                    + "APPROVED: full staffing is a precondition of starting, not a standing rule."
    )
    @ApiResponse(responseCode = "200", description = "Withdrawn")
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Not the mission lead who owns this mission",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such assignment in the caller's organisation",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "The assignment is already declined or withdrawn",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    AssignmentResponse withdrawAssignment(@PathVariable final UUID assignmentId) {
        log.atInfo().setMessage("Request to POST assignment withdraw; assignmentId={}")
                .addArgument(assignmentId).log();
        AssignmentResponse response = assignments.withdraw(assignmentId);
        log.atInfo().setMessage("Request to POST assignment withdraw completed; assignmentId={}")
                .addArgument(assignmentId).log();
        return response;
    }
}
