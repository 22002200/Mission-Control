package com.missioncontrol.matching.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.missioncontrol.platform.GlobalExceptionHandler;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The HTTP contract of the matching endpoints.
 *
 * <p>Filters are off: this is about what the controller and the exception handler produce, not
 * about who is allowed through. Who may run a match is covered end to end by
 * {@code CrewMatchingIT}, because it needs a real mission to decide.
 */
@WebMvcTest(controllers = CrewMatchController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class CrewMatchControllerApiTest {

    private static final UUID MISSION = UUID.fromString("a4000000-0000-0000-0000-000000000001");
    private static final UUID REQUIREMENT = UUID.fromString("a5000000-0000-0000-0000-000000000001");
    private static final UUID CREW = UUID.fromString("a3000000-0000-0000-0000-000000000001");
    private static final UUID SKILL = UUID.fromString("a2000000-0000-0000-0000-000000000001");

    private static final String PROBLEM_JSON = "application/problem+json";

    private static final String MATCH_ALL = "/api/missions/{missionId}/matches";
    private static final String MATCH_ONE =
            "/api/missions/{missionId}/requirements/{requirementId}/matches";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CrewMatchingService matching;

    @Captor private ArgumentCaptor<Set<UUID>> excludeCaptor;

    private static CandidateResponse candidate() {
        return new CandidateResponse(CREW, "Ada Kowalski", 1.0,
                new CandidateBreakdown(1.0, 0.0, 0, 0.0, 0),
                List.of(new CandidateSkillResponse(SKILL, "EVA Operations", 3, 3, true, 1, 1.0)),
                List.of());
    }

    private static RequirementMatchResponse requirementMatch(int remaining) {
        return new RequirementMatchResponse(REQUIREMENT, "Flight Engineer", 2, 0, 0, 2, remaining,
                List.of(candidate()));
    }

    @Test
    @DisplayName("Match all returns a requirement list with candidates and their breakdowns")
    void matchAllReturnsTheDraft() throws Exception {
        when(matching.matchAll(MISSION))
                .thenReturn(new MissionMatchResponse(MISSION, List.of(requirementMatch(4))));

        mockMvc.perform(get(MATCH_ALL, MISSION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionId").value(MISSION.toString()))
                .andExpect(jsonPath("$.requirements[0].requirementId")
                        .value(REQUIREMENT.toString()))
                .andExpect(jsonPath("$.requirements[0].openSeats").value(2))
                .andExpect(jsonPath("$.requirements[0].candidates[0].fullName")
                        .value("Ada Kowalski"))
                .andExpect(jsonPath("$.requirements[0].candidates[0].score").value(1.0))
                .andExpect(jsonPath("$.requirements[0].candidates[0].breakdown.skillScore")
                        .value(1.0))
                .andExpect(jsonPath("$.requirements[0].candidates[0].skills[0].skillName")
                        .value("EVA Operations"));
    }

    @Test
    @DisplayName("A mission with no requirements is a 200 with an empty list")
    void emptyMissionIsNotAnError() throws Exception {
        when(matching.matchAll(MISSION)).thenReturn(new MissionMatchResponse(MISSION, List.of()));

        mockMvc.perform(get(MATCH_ALL, MISSION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirements").isEmpty());
    }

    @Test
    @DisplayName("The per-requirement endpoint defaults to three candidates and no exclusions")
    void defaultsToThreeAndNoExclusions() throws Exception {
        when(matching.matchRequirement(any(), any(), anyInt(), any()))
                .thenReturn(requirementMatch(0));

        mockMvc.perform(get(MATCH_ONE, MISSION, REQUIREMENT))
                .andExpect(status().isOk());

        verify(matching).matchRequirement(eq(MISSION), eq(REQUIREMENT), eq(3), excludeCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(excludeCaptor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("Exclusions are passed through as crew member ids")
    void passesExclusionsThrough() throws Exception {
        UUID other = UUID.fromString("a3000000-0000-0000-0000-000000000002");
        when(matching.matchRequirement(any(), any(), anyInt(), any()))
                .thenReturn(requirementMatch(0));

        mockMvc.perform(get(MATCH_ONE, MISSION, REQUIREMENT)
                        .param("exclude", CREW.toString(), other.toString()))
                .andExpect(status().isOk());

        verify(matching).matchRequirement(any(), any(), anyInt(), excludeCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(excludeCaptor.getValue())
                .containsExactlyInAnyOrder(CREW, other);
    }

    @Test
    @DisplayName("remainingCount reaches the client so a rematch button knows when to stop")
    void reportsWhatIsLeft() throws Exception {
        when(matching.matchRequirement(any(), any(), anyInt(), any()))
                .thenReturn(requirementMatch(0));

        mockMvc.perform(get(MATCH_ONE, MISSION, REQUIREMENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingCount").value(0));
    }

    @Test
    @DisplayName("A limit above the maximum is a 400, not a silently clamped list")
    void rejectsAnOversizedLimit() throws Exception {
        mockMvc.perform(get(MATCH_ONE, MISSION, REQUIREMENT).param("limit", "11"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"));
    }

    @Test
    @DisplayName("A limit below one is a 400")
    void rejectsAnEmptyLimit() throws Exception {
        mockMvc.perform(get(MATCH_ONE, MISSION, REQUIREMENT).param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"));
    }

    @Test
    @DisplayName("More than fifty exclusions is a 400")
    void rejectsAnOversizedExclusionList() throws Exception {
        String[] tooMany = new String[51];
        for (int index = 0; index < tooMany.length; index++) {
            tooMany[index] = UUID.randomUUID().toString();
        }

        mockMvc.perform(get(MATCH_ONE, MISSION, REQUIREMENT).param("exclude", tooMany))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"));
    }

    @Test
    @DisplayName("Fifty exclusions is accepted - the cap is inclusive")
    void acceptsExactlyFiftyExclusions() throws Exception {
        when(matching.matchRequirement(any(), any(), anyInt(), any()))
                .thenReturn(requirementMatch(0));

        String[] fifty = new String[50];
        for (int index = 0; index < fifty.length; index++) {
            fifty[index] = UUID.randomUUID().toString();
        }

        mockMvc.perform(get(MATCH_ONE, MISSION, REQUIREMENT).param("exclude", fifty))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A requirement on a different mission is reported as absent")
    void unknownRequirementIsNotFound() throws Exception {
        when(matching.matchRequirement(any(), any(), anyInt(), any()))
                .thenThrow(new RequirementNotOnMissionException());

        mockMvc.perform(get(MATCH_ONE, MISSION, REQUIREMENT))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:not-found"))
                .andExpect(jsonPath("$.detail").value("No such crew requirement on this mission."));
    }
}
