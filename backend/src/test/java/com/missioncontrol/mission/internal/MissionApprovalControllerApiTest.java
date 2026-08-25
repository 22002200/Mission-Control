package com.missioncontrol.mission.internal;

import com.missioncontrol.mission.api.MissionStatus;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The HTTP contract of the approval endpoints.
 *
 * <p>Filters are off, so <strong>this test cannot see {@code PreAuthorize}</strong> - the
 * director-only rule on approve and reject is covered by {@code MissionApprovalRoleApiTest}, which
 * runs the real filter chain. What is covered here is the wire format, the request validation, and
 * the shape of the problem bodies.
 */
@WebMvcTest(controllers = MissionApprovalController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class MissionApprovalControllerApiTest {

    private static final UUID MISSION = UUID.fromString("a4000000-0000-0000-0000-000000000001");
    private static final UUID LEAD = UUID.fromString("a1000000-0000-0000-0000-000000000002");
    private static final UUID DIRECTOR = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID CYCLE = UUID.fromString("a6000000-0000-0000-0000-000000000001");

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private MissionApprovalService approvals;

    @Captor private ArgumentCaptor<ApproveMissionRequest> approveCaptor;
    @Captor private ArgumentCaptor<RejectMissionRequest> rejectCaptor;

    private static MissionResponse mission(MissionStatus status) {
        return new MissionResponse(MISSION, "Aurora Survey", "Mapping auroral activity.",
                status, null, null,
                Instant.parse("2026-09-01T08:00:00Z"), Instant.parse("2026-09-14T17:00:00Z"),
                new UserRef(LEAD, "Marcus Reyes"), false, List.of());
    }

    @Test
    @DisplayName("Submit returns the mission, now PENDING_APPROVAL")
    void submitReturnsTheMission() throws Exception {
        when(approvals.submit(MISSION)).thenReturn(mission(MissionStatus.PENDING_APPROVAL));

        mockMvc.perform(post("/api/missions/{id}/submit", MISSION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("Submitting a mission with no requirements is a 409 naming the reason")
    void submitWithoutRequirements() throws Exception {
        when(approvals.submit(MISSION)).thenThrow(new MissionHasNoRequirementsException());

        mockMvc.perform(post("/api/missions/{id}/submit", MISSION))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("urn:mission-control:mission-has-no-requirements"));
    }

    @Test
    @DisplayName("Approve accepts an optional note")
    void approveWithANote() throws Exception {
        when(approvals.approve(eq(MISSION), any())).thenReturn(mission(MissionStatus.APPROVED));

        mockMvc.perform(post("/api/missions/{id}/approve", MISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Cleared.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(approvals).approve(eq(MISSION), approveCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(approveCaptor.getValue().comment())
                .isEqualTo("Cleared.");
    }

    @Test
    @DisplayName("Approve accepts no body at all, like close does")
    void approveWithoutABody() throws Exception {
        when(approvals.approve(eq(MISSION), any())).thenReturn(mission(MissionStatus.APPROVED));

        mockMvc.perform(post("/api/missions/{id}/approve", MISSION))
                .andExpect(status().isOk());

        verify(approvals).approve(eq(MISSION), approveCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(approveCaptor.getValue().comment()).isNull();
    }

    @Test
    @DisplayName("Approving a mission that has moved on is a 409 saying where it moved to")
    void approveAfterSomeoneElseDecided() throws Exception {
        when(approvals.approve(eq(MISSION), any())).thenThrow(
                new InvalidMissionTransitionException(MissionStatus.APPROVED,
                        MissionStatus.APPROVED));

        mockMvc.perform(post("/api/missions/{id}/approve", MISSION))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:invalid-transition"))
                .andExpect(jsonPath("$.currentStatus").value("APPROVED"))
                .andExpect(jsonPath("$.attemptedTransition").value("APPROVED"));
    }

    @Test
    @DisplayName("Reject passes the comment through")
    void rejectWithAComment() throws Exception {
        when(approvals.reject(eq(MISSION), any())).thenReturn(mission(MissionStatus.REJECTED));

        mockMvc.perform(post("/api/missions/{id}/reject", MISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Timeline clashes with the Vesta flyby.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        verify(approvals).reject(eq(MISSION), rejectCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(rejectCaptor.getValue().comment())
                .isEqualTo("Timeline clashes with the Vesta flyby.");
    }

    /**
     * BR-6 read honestly. Whitespace satisfies the letter of 'a comment was supplied' and none of
     * its purpose, and an absent body must be a 400 rather than a NullPointerException, so all four
     * shapes are asserted rather than just the obvious one.
     */
    @Test
    @DisplayName("Rejecting without a usable comment is a 400, however it is omitted")
    void rejectWithoutAComment() throws Exception {
        for (String body : new String[] {"{}", "{\"comment\":null}", "{\"comment\":\"\"}",
                "{\"comment\":\"   \"}"}) {
            mockMvc.perform(post("/api/missions/{id}/reject", MISSION)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"))
                    .andExpect(jsonPath("$.errors.comment").exists());
        }
    }

    @Test
    @DisplayName("Rejecting with no body at all is a 400, not a 500")
    void rejectWithNoBody() throws Exception {
        mockMvc.perform(post("/api/missions/{id}/reject", MISSION))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    @DisplayName("A comment over 1000 characters is refused")
    void rejectWithAnOverlongComment() throws Exception {
        mockMvc.perform(post("/api/missions/{id}/reject", MISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"" + "x".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.comment").exists());
    }

    @Test
    @DisplayName("Replan returns the mission, back in PLAN")
    void replanReturnsTheMission() throws Exception {
        when(approvals.replan(MISSION)).thenReturn(mission(MissionStatus.PLAN));

        mockMvc.perform(post("/api/missions/{id}/replan", MISSION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLAN"));
    }

    @Test
    @DisplayName("A caller who is not the owner gets a forbidden problem, not an empty 403")
    void replanAsSomeoneElse() throws Exception {
        when(approvals.replan(MISSION)).thenThrow(
                new MissionForbiddenException("Only the mission lead who owns this mission can "
                        + "return this mission to planning."));

        mockMvc.perform(post("/api/missions/{id}/replan", MISSION))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:forbidden"));
    }

    @Test
    @DisplayName("The history is a bare array, newest first, with both names resolved")
    void historyIsAnArray() throws Exception {
        when(approvals.history(MISSION)).thenReturn(List.of(
                new MissionApprovalResponse(CYCLE, ApprovalDecision.REJECTED, "Too tight.",
                        new UserRef(LEAD, "Marcus Reyes"), Instant.parse("2026-02-01T09:00:00Z"),
                        new UserRef(DIRECTOR, "Vera Lindholm"),
                        Instant.parse("2026-02-02T14:30:00Z")),
                new MissionApprovalResponse(UUID.randomUUID(), ApprovalDecision.PENDING, null,
                        new UserRef(LEAD, "Marcus Reyes"), Instant.parse("2026-01-01T09:00:00Z"),
                        null, null)));

        mockMvc.perform(get("/api/missions/{id}/approvals", MISSION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].decision").value("REJECTED"))
                .andExpect(jsonPath("$[0].submittedBy.fullName").value("Marcus Reyes"))
                .andExpect(jsonPath("$[0].decidedBy.fullName").value("Vera Lindholm"))
                // A pending cycle omits the decision fields entirely rather than sending nulls,
                // which is the same wire behaviour closeReason has on a mission.
                .andExpect(jsonPath("$[1].decidedBy").doesNotExist())
                .andExpect(jsonPath("$[1].decidedAt").doesNotExist())
                .andExpect(jsonPath("$[1].comment").doesNotExist());
    }

    @Test
    @DisplayName("A mission that was never submitted has an empty history, not a 404")
    void emptyHistory() throws Exception {
        when(approvals.history(MISSION)).thenReturn(List.of());

        mockMvc.perform(get("/api/missions/{id}/approvals", MISSION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("A mission the caller cannot see is absent, not forbidden")
    void missingMission() throws Exception {
        when(approvals.history(MISSION)).thenThrow(new MissionNotFoundException());

        mockMvc.perform(get("/api/missions/{id}/approvals", MISSION))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:not-found"));
    }
}
