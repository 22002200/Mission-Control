package com.missioncontrol.assignment.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.missioncontrol.platform.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The HTTP contract of the mission-facing staffing endpoints.
 *
 * <p>Filters are off, so this says nothing about who may offer - {@code CrewAssignmentIT} settles
 * that against a real mission. What it does pin down is the request validation and the two error
 * bodies a mission lead will actually meet: a full requirement and a duplicate offer.
 */
@WebMvcTest(controllers = MissionAssignmentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class MissionAssignmentControllerApiTest {

    private static final UUID MISSION = UUID.fromString("b4000000-0000-0000-0000-000000000002");
    private static final UUID REQUIREMENT = UUID.fromString("b5000000-0000-0000-0000-000000000002");
    private static final UUID CREW = UUID.fromString("b3000000-0000-0000-0000-000000000001");
    private static final UUID ASSIGNMENT = UUID.fromString("b6000000-0000-0000-0000-000000000001");

    private static final Instant OFFERED_AT = Instant.parse("2026-01-06T09:00:00Z");

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String ASSIGNMENTS = "/api/missions/{missionId}/assignments";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssignmentService assignments;

    @Test
    @DisplayName("Offering answers 201 with the new assignment - FR-1")
    void offering() throws Exception {
        when(assignments.offer(eq(MISSION), any())).thenReturn(new AssignmentResponse(
                ASSIGNMENT, REQUIREMENT, new CrewMemberRef(CREW, "Ines Varga"),
                AssignmentStatus.OFFERED, OFFERED_AT, null));

        mockMvc.perform(post(ASSIGNMENTS, MISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"crewRequirementId": "%s", "crewMemberId": "%s"}
                                """.formatted(REQUIREMENT, CREW)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ASSIGNMENT.toString()))
                .andExpect(jsonPath("$.status").value("OFFERED"))
                .andExpect(jsonPath("$.crewRequirementId").value(REQUIREMENT.toString()))
                .andExpect(jsonPath("$.crewMember.id").value(CREW.toString()))
                .andExpect(jsonPath("$.offeredAt").value(OFFERED_AT.toString()))
                .andExpect(jsonPath("$.respondedAt").doesNotExist());

        verify(assignments).offer(MISSION, new OfferAssignmentRequest(REQUIREMENT, CREW));
    }

    @Test
    @DisplayName("A missing crewMemberId is a 400 naming the field")
    void missingFieldIsRejected() throws Exception {
        mockMvc.perform(post(ASSIGNMENTS, MISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"crewRequirementId": "%s"}
                                """.formatted(REQUIREMENT)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"))
                .andExpect(jsonPath("$.errors.crewMemberId").value("crewMemberId is required"));
    }

    @Test
    @DisplayName("An empty body is a 400, not a 500")
    void emptyBodyIsRejected() throws Exception {
        mockMvc.perform(post(ASSIGNMENTS, MISSION).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"));
    }

    @Test
    @DisplayName("A full requirement says which half is full, and by how much")
    void requirementFullCarriesTheCounts() throws Exception {
        when(assignments.offer(eq(MISSION), any()))
                .thenThrow(new RequirementFullException(REQUIREMENT, 2, 1, 1));

        // A line full of outstanding offers is waiting and a withdrawal would free it; a line full
        // of acceptances is finished. Those are different actions, so the error says which.
        mockMvc.perform(post(ASSIGNMENTS, MISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"crewRequirementId": "%s", "crewMemberId": "%s"}
                                """.formatted(REQUIREMENT, CREW)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:requirement-full"))
                .andExpect(jsonPath("$.detail").value(
                        "All 2 places are taken, some by offers nobody has answered yet."))
                .andExpect(jsonPath("$.requiredCount").value(2))
                .andExpect(jsonPath("$.acceptedCount").value(1))
                .andExpect(jsonPath("$.offeredCount").value(1));
    }

    @Test
    @DisplayName("Offering the same crew member twice on one mission is a 409 of its own type")
    void duplicateHasItsOwnUrn() throws Exception {
        when(assignments.offer(eq(MISSION), any())).thenThrow(new DuplicateAssignmentException());

        // A distinct URN rather than a generic conflict, because the client's remedy differs: this
        // one means pick somebody else, not wait or withdraw.
        mockMvc.perform(post(ASSIGNMENTS, MISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"crewRequirementId": "%s", "crewMemberId": "%s"}
                                """.formatted(REQUIREMENT, CREW)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:duplicate-assignment"));
    }

    @Test
    @DisplayName("A mission that is not APPROVED refuses the offer and says what it is")
    void missionNotApproved() throws Exception {
        when(assignments.offer(eq(MISSION), any())).thenThrow(
                InvalidAssignmentTransitionException.mission(
                        com.missioncontrol.mission.api.MissionStatus.PLAN));

        mockMvc.perform(post(ASSIGNMENTS, MISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"crewRequirementId": "%s", "crewMemberId": "%s"}
                                """.formatted(REQUIREMENT, CREW)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:invalid-transition"))
                .andExpect(jsonPath("$.currentStatus").value("PLAN"))
                .andExpect(jsonPath("$.attemptedTransition").value("APPROVED"));
    }

    @Test
    @DisplayName("The mission view groups by requirement and keeps the empty lines - FR-2")
    void listingGroupsByRequirement() throws Exception {
        when(assignments.forMission(eq(MISSION), isNull())).thenReturn(
                new MissionAssignmentsResponse(MISSION, List.of(
                        new RequirementAssignmentsResponse(REQUIREMENT, "Thermal Engineer", 2, 1, 0,
                                List.of(new AssignmentResponse(ASSIGNMENT, REQUIREMENT,
                                        new CrewMemberRef(CREW, "Ines Varga"),
                                        AssignmentStatus.ACCEPTED, OFFERED_AT,
                                        Instant.parse("2026-01-07T10:00:00Z")))),
                        new RequirementAssignmentsResponse(UUID.randomUUID(), "Science Officer", 2,
                                0, 0, List.of()))));

        mockMvc.perform(get(ASSIGNMENTS, MISSION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionId").value(MISSION.toString()))
                .andExpect(jsonPath("$.requirements.length()").value(2))
                .andExpect(jsonPath("$.requirements[0].acceptedCount").value(1))
                .andExpect(jsonPath("$.requirements[0].assignments[0].crewMember.fullName")
                        .value("Ines Varga"))
                // The unstaffed line is present with an empty list. Omitting it would make an
                // unstaffed mission look like a short one.
                .andExpect(jsonPath("$.requirements[1].title").value("Science Officer"))
                .andExpect(jsonPath("$.requirements[1].assignments").isEmpty());
    }

    @Test
    @DisplayName("The status filter reaches the service as an enum")
    void passesTheStatusFilter() throws Exception {
        when(assignments.forMission(eq(MISSION), any()))
                .thenReturn(new MissionAssignmentsResponse(MISSION, List.of()));

        mockMvc.perform(get(ASSIGNMENTS, MISSION).param("status", "OFFERED"))
                .andExpect(status().isOk());

        verify(assignments).forMission(MISSION, AssignmentStatus.OFFERED);
    }

    @Test
    @DisplayName("An unknown status is a 400, not a 500")
    void unknownStatusIsRejected() throws Exception {
        mockMvc.perform(get(ASSIGNMENTS, MISSION).param("status", "MAYBE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"));
    }

    @Test
    @DisplayName("An unknown mission is a 404 that says nothing about which")
    void unknownMissionIsNotFound() throws Exception {
        when(assignments.forMission(eq(MISSION), isNull()))
                .thenThrow(AssignmentNotFoundException.assignment());

        mockMvc.perform(get(ASSIGNMENTS, MISSION))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:not-found"));
    }
}
