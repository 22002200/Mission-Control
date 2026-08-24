package com.missioncontrol.mission.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.missioncontrol.platform.GlobalExceptionHandler;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The HTTP contract of the crew requirement endpoints. Filters off, as with the mission one. */
@WebMvcTest(controllers = CrewRequirementController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class CrewRequirementControllerApiTest {

    private static final UUID MISSION = UUID.fromString("a4000000-0000-0000-0000-000000000001");
    private static final UUID REQUIREMENT = UUID.fromString("a5000000-0000-0000-0000-000000000001");
    private static final UUID SKILL = UUID.fromString("a2000000-0000-0000-0000-000000000001");

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CrewRequirementService requirements;

    private static CrewRequirementResponse engineer() {
        return new CrewRequirementResponse(REQUIREMENT, "Flight Engineer", "Repairs.", 2, 0,
                List.of(new RequiredSkillResponse(SKILL, "EVA Operations", 3, true, 2)));
    }

    private static String body(String json) {
        return json;
    }

    @Test
    void addReturns201AndTheRequirement() throws Exception {
        when(requirements.add(eq(MISSION), any())).thenReturn(engineer());

        mockMvc.perform(post("/api/missions/{id}/requirements", MISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"title":"Flight Engineer","requiredCount":2,
                                 "skills":[{"skillId":"%s","minimumProficiency":3,
                                            "mandatory":true,"weight":2}]}
                                """.formatted(SKILL))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Flight Engineer"))
                .andExpect(jsonPath("$.skills[0].skillName").value("EVA Operations"))
                .andExpect(jsonPath("$.skills[0].mandatory").value(true));
    }

    @Test
    @DisplayName("requiredCount below one is refused by validation - M9")
    void requiredCountMustBeAtLeastOne() throws Exception {
        mockMvc.perform(post("/api/missions/{id}/requirements", MISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Engineer\",\"requiredCount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.requiredCount").exists());
    }

    @Test
    void titleIsRequired() throws Exception {
        mockMvc.perform(post("/api/missions/{id}/requirements", MISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requiredCount\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    @DisplayName("Constraints inside the skills list are enforced, not silently skipped")
    void nestedSkillConstraintsRun() throws Exception {
        mockMvc.perform(post("/api/missions/{id}/requirements", MISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Engineer","requiredCount":1,
                                 "skills":[{"skillId":"%s","minimumProficiency":9,
                                            "mandatory":true}]}
                                """.formatted(SKILL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void aDuplicateSkillIsAConflict() throws Exception {
        when(requirements.add(eq(MISSION), any())).thenThrow(new DuplicateSkillException());

        mockMvc.perform(post("/api/missions/{id}/requirements", MISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Engineer\",\"requiredCount\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:duplicate-skill"));
    }

    @Test
    void anInvalidSkillIsAConflict() throws Exception {
        when(requirements.add(eq(MISSION), any())).thenThrow(new InvalidSkillException());

        mockMvc.perform(post("/api/missions/{id}/requirements", MISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Engineer\",\"requiredCount\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:invalid-skill"));
    }

    @Test
    @DisplayName("Editing outside PLAN reports the status that blocked it")
    void notEditableCarriesTheStatus() throws Exception {
        when(requirements.add(eq(MISSION), any()))
                .thenThrow(new MissionNotEditableException(MissionStatus.APPROVED));

        mockMvc.perform(post("/api/missions/{id}/requirements", MISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Engineer\",\"requiredCount\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:mission-not-editable"))
                .andExpect(jsonPath("$.currentStatus").value("APPROVED"));
    }

    @Test
    void updatePassesBothIdsThrough() throws Exception {
        when(requirements.update(eq(MISSION), eq(REQUIREMENT), any())).thenReturn(engineer());
        ArgumentCaptor<CrewRequirementRequest> captor =
                ArgumentCaptor.forClass(CrewRequirementRequest.class);

        mockMvc.perform(patch("/api/missions/{id}/requirements/{req}", MISSION, REQUIREMENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Renamed\",\"requiredCount\":3}"))
                .andExpect(status().isOk());

        verify(requirements).update(eq(MISSION), eq(REQUIREMENT), captor.capture());
        Assertions.assertThat(captor.getValue().requiredCount()).isEqualTo(3);
        Assertions.assertThat(captor.getValue().skillsOrEmpty()).isEmpty();
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/missions/{id}/requirements/{req}", MISSION, REQUIREMENT))
                .andExpect(status().isNoContent());

        verify(requirements).delete(MISSION, REQUIREMENT);
    }

    @Test
    void deletingAnUnknownRequirementIsNotFound() throws Exception {
        doThrow(new RequirementNotFoundException())
                .when(requirements).delete(MISSION, REQUIREMENT);

        mockMvc.perform(delete("/api/missions/{id}/requirements/{req}", MISSION, REQUIREMENT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:mission-control:not-found"));
    }

    @Test
    void aForbiddenWriteSaysWhy() throws Exception {
        when(requirements.add(eq(MISSION), any()))
                .thenThrow(new MissionForbiddenException("Only the owner can do that."));

        mockMvc.perform(post("/api/missions/{id}/requirements", MISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Engineer\",\"requiredCount\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:mission-control:forbidden"));
    }
}
