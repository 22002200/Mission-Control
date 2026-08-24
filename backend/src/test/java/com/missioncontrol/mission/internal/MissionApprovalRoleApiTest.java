package com.missioncontrol.mission.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * BR-3, at the only layer that can see it.
 *
 * <p>{@code MissionApprovalControllerApiTest} runs with the filters off, so {@code PreAuthorize} is
 * never evaluated there. This one turns method security on, which is what makes 'only a director
 * may approve' a tested rule rather than an annotation nobody has watched work.
 *
 * <p>Roles are set straight onto the security context with {@code WithMockUser} rather than by
 * minting a token and running the filter chain. Method security reads the context, not the request,
 * so the token path adds nothing here - and it is already covered for the application as a whole by
 * {@code RoleEnforcementApiTest} (that the {@code role} claim becomes a {@code ROLE_} authority)
 * and {@code SecurityFilterChainApiTest} (that an unauthenticated call is a 401). Those two live in
 * {@code platform} because the beans involved are package-private there.
 *
 * <p>The last two cases are the ones most worth having. Submit and replan deliberately carry
 * <em>no</em> role annotation, because ownership needs the mission in hand; a helpful future
 * addition of {@code hasRole('MISSION_LEAD')} would turn the 404 a non-owning lead is supposed to
 * get into a 403 that confirms the mission exists. Asserting that both reach the service is how
 * that stays true.
 */
@WebMvcTest(controllers = MissionApprovalController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, MissionApprovalRoleApiTest.MethodSecurity.class})
@ActiveProfiles("test")
class MissionApprovalRoleApiTest {

    /** {@code SecurityConfig} carries this in the application; the slice has to opt in. */
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurity {
    }

    private static final UUID MISSION = UUID.fromString("a4000000-0000-0000-0000-000000000001");
    private static final UUID LEAD = UUID.fromString("a1000000-0000-0000-0000-000000000002");

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private MissionApprovalService approvals;

    private static MissionResponse mission(MissionStatus status) {
        return new MissionResponse(MISSION, "Aurora Survey", null, status, null, null,
                Instant.parse("2026-09-01T08:00:00Z"), Instant.parse("2026-09-14T17:00:00Z"),
                new UserRef(LEAD, "Marcus Reyes"), false, List.of());
    }

    @Test
    @WithMockUser(roles = "DIRECTOR")
    @DisplayName("A director may approve and reject")
    void directorMayDecide() throws Exception {
        when(approvals.approve(eq(MISSION), any())).thenReturn(mission(MissionStatus.APPROVED));
        when(approvals.reject(eq(MISSION), any())).thenReturn(mission(MissionStatus.REJECTED));

        mockMvc.perform(post("/api/missions/{id}/approve", MISSION))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/missions/{id}/reject", MISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Not yet.\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MISSION_LEAD")
    @DisplayName("A mission lead attempting to decide is refused before the service is reached")
    void missionLeadMayNotDecide() throws Exception {
        mockMvc.perform(post("/api/missions/{id}/approve", MISSION))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:forbidden"));

        mockMvc.perform(post("/api/missions/{id}/reject", MISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Not yet.\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:mission-control:forbidden"));

        verifyNoInteractions(approvals);
    }

    @Test
    @WithMockUser(roles = "CREW_MEMBER")
    @DisplayName("A crew member may not decide either")
    void crewMemberMayNotDecide() throws Exception {
        mockMvc.perform(post("/api/missions/{id}/approve", MISSION))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:mission-control:forbidden"));

        verifyNoInteractions(approvals);
    }

    @Test
    @WithMockUser(roles = "MISSION_LEAD")
    @DisplayName("Submit and replan carry no role check - ownership is the service's to decide")
    void submitAndReplanReachTheService() throws Exception {
        when(approvals.submit(MISSION)).thenReturn(mission(MissionStatus.PENDING_APPROVAL));
        when(approvals.replan(MISSION)).thenReturn(mission(MissionStatus.PLAN));

        mockMvc.perform(post("/api/missions/{id}/submit", MISSION)).andExpect(status().isOk());
        mockMvc.perform(post("/api/missions/{id}/replan", MISSION)).andExpect(status().isOk());

        verify(approvals).submit(MISSION);
        verify(approvals).replan(MISSION);
    }

    @Test
    @WithMockUser(roles = "DIRECTOR")
    @DisplayName("A director is not blocked from submit either - the service answers 403 there")
    void directorReachesSubmit() throws Exception {
        when(approvals.submit(MISSION)).thenThrow(
                new MissionForbiddenException("Only the mission lead who owns this mission can "
                        + "submit this mission for approval."));

        mockMvc.perform(post("/api/missions/{id}/submit", MISSION))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:mission-control:forbidden"));

        // The point: the refusal came from the service, having loaded the mission, rather than from
        // an annotation that never looked at it.
        verify(approvals).submit(MISSION);
    }
}
