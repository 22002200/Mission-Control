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

import com.missioncontrol.mission.api.MissionStatus;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The HTTP contract of the crew-facing assignment endpoints.
 *
 * <p>Filters are off: this is about what the controller and the exception handler produce, not
 * about who is allowed through. Who may accept, decline or withdraw is covered end to end by
 * {@code CrewAssignmentIT}, because it needs a real assignment and a real caller to decide.
 *
 * <p>The error shapes are worth pinning here rather than only in the integration test. Every one of
 * them carries properties a client is expected to read - {@code currentStatus} to tell a stale
 * screen from a mistake, the conflicting mission's name and dates so a crew member can see what
 * they are already committed to - and those are exactly the fields that get dropped in a refactor
 * without anything noticing.
 */
@WebMvcTest(controllers = AssignmentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class AssignmentControllerApiTest {

    private static final UUID ASSIGNMENT = UUID.fromString("b6000000-0000-0000-0000-000000000001");
    private static final UUID MISSION = UUID.fromString("b4000000-0000-0000-0000-000000000002");
    private static final UUID CREW = UUID.fromString("b3000000-0000-0000-0000-000000000001");
    private static final UUID REQUIREMENT = UUID.fromString("b5000000-0000-0000-0000-000000000002");

    private static final Instant OFFERED_AT = Instant.parse("2026-01-06T09:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-10-14T03:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-11-11T21:00:00Z");

    private static final String PROBLEM_JSON = "application/problem+json";

    private static final String MINE = "/api/assignments/me";
    private static final String ACCEPT = "/api/assignments/{id}/accept";
    private static final String DECLINE = "/api/assignments/{id}/decline";
    private static final String WITHDRAW = "/api/assignments/{id}/withdraw";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssignmentService assignments;

    @Test
    @DisplayName("GET /api/assignments/me returns a page, mission-first - FR-3")
    void listsOwnAssignments() throws Exception {
        when(assignments.mine(isNull(), isNull(), eq(0), eq(20))).thenReturn(new MyAssignmentPage(
                List.of(new MyAssignmentResponse(ASSIGNMENT, AssignmentStatus.OFFERED, OFFERED_AT,
                        null,
                        new MissionRef(MISSION, "Perihelion Watch", MissionStatus.APPROVED,
                                STARTS, ENDS),
                        "Thermal Engineer")),
                0, 20, 1, 1));

        mockMvc.perform(get(MINE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ASSIGNMENT.toString()))
                .andExpect(jsonPath("$.content[0].status").value("OFFERED"))
                .andExpect(jsonPath("$.content[0].respondedAt").doesNotExist())
                .andExpect(jsonPath("$.content[0].mission.name").value("Perihelion Watch"))
                .andExpect(jsonPath("$.content[0].mission.status").value("APPROVED"))
                .andExpect(jsonPath("$.content[0].requirementTitle").value("Thermal Engineer"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Status and timeframe reach the service as enums - FR-9")
    void passesFiltersThrough() throws Exception {
        when(assignments.mine(any(), any(), eq(1), eq(5)))
                .thenReturn(new MyAssignmentPage(List.of(), 1, 5, 0, 0));

        mockMvc.perform(get(MINE)
                        .param("status", "ACCEPTED")
                        .param("timeframe", "UPCOMING")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk());

        verify(assignments).mine(AssignmentStatus.ACCEPTED, Timeframe.UPCOMING, 1, 5);
    }

    @Test
    @DisplayName("An unknown timeframe is a 400, not a 500")
    void unknownTimeframeIsRejected() throws Exception {
        mockMvc.perform(get(MINE).param("timeframe", "SOMEDAY"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"));
    }

    @Test
    @DisplayName("A negative page is a 400")
    void negativePageIsRejected() throws Exception {
        mockMvc.perform(get(MINE).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"));
    }

    @Test
    @DisplayName("Accepting answers with the assignment in its new status - FR-4")
    void accepts() throws Exception {
        when(assignments.accept(ASSIGNMENT)).thenReturn(response(AssignmentStatus.ACCEPTED));

        mockMvc.perform(post(ACCEPT, ASSIGNMENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.crewMember.id").value(CREW.toString()))
                .andExpect(jsonPath("$.crewMember.fullName").value("Ines Varga"));
    }

    @Test
    @DisplayName("Declining takes no body - the optional reason was dropped from the spec")
    void declinesWithoutABody() throws Exception {
        when(assignments.decline(ASSIGNMENT)).thenReturn(response(AssignmentStatus.DECLINED));

        // No content type and no payload. Feature 07 originally allowed an optional reason here
        // and there was nowhere to store it, so it went rather than being accepted and discarded.
        mockMvc.perform(post(DECLINE, ASSIGNMENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));
    }

    @Test
    @DisplayName("Withdrawing answers with the assignment in its new status - FR-6")
    void withdraws() throws Exception {
        when(assignments.withdraw(ASSIGNMENT)).thenReturn(response(AssignmentStatus.WITHDRAWN));

        mockMvc.perform(post(WITHDRAW, ASSIGNMENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }

    @Test
    @DisplayName("A schedule conflict names the mission already committed to")
    void scheduleConflictCarriesTheClash() throws Exception {
        when(assignments.accept(ASSIGNMENT)).thenThrow(new ScheduleConflictException(
                new com.missioncontrol.mission.api.MissionWindow(
                        MISSION, UUID.randomUUID(), "Zenith Station Run", MissionStatus.ACTIVE,
                        UUID.randomUUID(), STARTS, ENDS, false)));

        // The point of the whole error. A crew member told only 'schedule conflict' has to go and
        // look; one told which mission and when can decide what to do about it.
        mockMvc.perform(post(ACCEPT, ASSIGNMENT))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:schedule-conflict"))
                .andExpect(jsonPath("$.detail").value(
                        "This clashes with Zenith Station Run, which you have already accepted."))
                .andExpect(jsonPath("$.conflictingMissionName").value("Zenith Station Run"))
                .andExpect(jsonPath("$.conflictingStartsAt").value(STARTS.toString()))
                .andExpect(jsonPath("$.conflictingEndsAt").value(ENDS.toString()));
    }

    @Test
    @DisplayName("An assignment that has already been settled reports its current status")
    void invalidTransitionCarriesTheCurrentStatus() throws Exception {
        when(assignments.accept(ASSIGNMENT)).thenThrow(
                InvalidAssignmentTransitionException.assignment(
                        AssignmentStatus.WITHDRAWN, AssignmentStatus.ACCEPTED));

        // currentStatus is what lets a client tell a stale screen from a mistake: seeing that the
        // offer was withdrawn while it was open is a cue to refresh, not an error to shout about.
        mockMvc.perform(post(ACCEPT, ASSIGNMENT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:invalid-transition"))
                .andExpect(jsonPath("$.currentStatus").value("WITHDRAWN"))
                .andExpect(jsonPath("$.attemptedTransition").value("ACCEPTED"));
    }

    @Test
    @DisplayName("Answering somebody else's offer is a 403")
    void forbiddenIsAProblemDocument() throws Exception {
        when(assignments.accept(ASSIGNMENT))
                .thenThrow(AssignmentForbiddenException.notTheCrewMember("accept"));

        mockMvc.perform(post(ACCEPT, ASSIGNMENT))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:forbidden"));
    }

    @Test
    @DisplayName("An unknown assignment is a 404 that says nothing about which one")
    void notFoundSaysNothing() throws Exception {
        when(assignments.accept(ASSIGNMENT)).thenThrow(AssignmentNotFoundException.assignment());

        mockMvc.perform(post(ACCEPT, ASSIGNMENT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:mission-control:not-found"))
                .andExpect(jsonPath("$.detail").value("No such assignment."));
    }

    @Test
    @DisplayName("A malformed assignment id is a 400, not a 500")
    void malformedIdIsRejected() throws Exception {
        mockMvc.perform(post(ACCEPT, "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"));
    }

    private static AssignmentResponse response(AssignmentStatus status) {
        return new AssignmentResponse(ASSIGNMENT, REQUIREMENT,
                new CrewMemberRef(CREW, "Ines Varga"), status, OFFERED_AT,
                status == AssignmentStatus.OFFERED ? null : Instant.parse("2026-01-07T10:00:00Z"));
    }
}
