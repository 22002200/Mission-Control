package com.missioncontrol.mission.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The crew a mission calls for.
 *
 * <p>Nested under the mission because a requirement has no meaning apart from one, and reachable
 * only while the mission is still in PLAN. There is no endpoint for an individual required skill:
 * skills travel inline with their requirement - FR-8 - so a requirement is always saved as one
 * complete thing.
 *
 * <p>A separate controller from {@link MissionController} rather than five more methods on it,
 * because the authorisation is different - owner only, not owner or director - and because it
 * gives the two a tag each in the API document.
 */
@RestController
@RequestMapping("/api/missions/{missionId}/requirements")
@Validated
@Tag(name = "Crew requirements", description = "The staffing lines on a mission.")
class CrewRequirementController {

    private final CrewRequirementService requirements;

    CrewRequirementController(CrewRequirementService requirements) {
        this.requirements = requirements;
    }

    @PostMapping
    @Operation(
            summary = "Add a crew requirement",
            description = "Only the owning mission lead, and only while the mission is in PLAN."
    )
    @ApiResponse(responseCode = "201", description = "The new requirement")
    @ApiResponse(responseCode = "400", description = "A field is invalid",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "The caller does not own this mission",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such mission the caller can see",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "The mission has left PLAN, a skill is listed "
            + "twice, or a skill is unknown or retired",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ResponseStatus(HttpStatus.CREATED)
    CrewRequirementResponse addRequirement(@PathVariable UUID missionId, @Valid @RequestBody CrewRequirementRequest request) {
        return requirements.add(missionId, request);
    }

    @PatchMapping("/{requirementId}")
    @Operation(
            summary = "Replace a crew requirement",
            description = "Replaces the requirement whole, skills included. A skill absent from "
                    + "the request is removed, because there is no meaningful partial update of a "
                    + "set."
    )
    @ApiResponse(responseCode = "200", description = "The updated requirement")
    @ApiResponse(responseCode = "400", description = "A field is invalid",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "The caller does not own this mission",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such mission, or no such requirement on it",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "The mission has left PLAN, a skill is listed "
            + "twice, or a skill is unknown or retired",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    CrewRequirementResponse updateRequirement(@PathVariable UUID missionId,
                                              @PathVariable UUID requirementId,
                                              @Valid @RequestBody CrewRequirementRequest request) {
        return requirements.update(missionId, requirementId, request);
    }

    @DeleteMapping("/{requirementId}")
    @Operation(
            summary = "Remove a crew requirement",
            description = "Only the owning mission lead, and only while the mission is in PLAN."
    )
    @ApiResponse(responseCode = "204", description = "Removed")
    @ApiResponse(responseCode = "401", description = "Not authenticated",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "The caller does not own this mission",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No such mission, or no such requirement on it",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "The mission has left PLAN",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<Void> deleteRequirement(@PathVariable UUID missionId, @PathVariable UUID requirementId) {
        requirements.delete(missionId, requirementId);
        return ResponseEntity.noContent().build();
    }
}
