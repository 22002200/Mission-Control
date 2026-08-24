package com.missioncontrol.skill.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.missioncontrol.platform.GlobalExceptionHandler;
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
 * The HTTP contract of the two read endpoints.
 *
 * <p>Filters are off: this is about what the controller and the exception handler produce, not
 * about who is allowed through. Which roles may call these is covered end to end by
 * {@code SkillCatalogueIT}.
 */
@WebMvcTest(controllers = SkillController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class SkillControllerApiTest {

    private static final UUID SKILL_ID = UUID.fromString("a2000000-0000-0000-0000-000000000001");

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SkillService skills;

    private static SkillResponse eva() {
        return new SkillResponse(SKILL_ID, "EVA Operations", "Operations",
                "Suit handling, tethering, external repair.", true);
    }

    private static SkillResponse minimal() {
        return new SkillResponse(SKILL_ID, "EVA Operations", null, null, true);
    }

    private static SkillPage pageOf(SkillResponse... entries) {
        return new SkillPage(List.of(entries), 0, 50, entries.length, 1);
    }

    @Test
    void listReturnsThePageAndItsMetadata() throws Exception {
        when(skills.list(any(), any(), anyInt(), anyInt())).thenReturn(pageOf(eva()));

        mockMvc.perform(get("/api/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(SKILL_ID.toString()))
                .andExpect(jsonPath("$.content[0].name").value("EVA Operations"))
                .andExpect(jsonPath("$.content[0].category").value("Operations"))
                .andExpect(jsonPath("$.content[0].active").value(true))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("A response never carries the organisation the caller already supplied")
    void listNeverEchoesTheOrganisation() throws Exception {
        when(skills.list(any(), any(), anyInt(), anyInt())).thenReturn(pageOf(eva()));

        String body = mockMvc.perform(get("/api/skills"))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("organisationId");
    }

    @Test
    void theOptionalFieldsAreOmittedRatherThanNull() throws Exception {
        when(skills.list(any(), any(), anyInt(), anyInt())).thenReturn(pageOf(minimal()));

        mockMvc.perform(get("/api/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].category").doesNotExist())
                .andExpect(jsonPath("$.content[0].description").doesNotExist());
    }

    @Test
    void listDefaultsToTheWholeCatalogueUnfiltered() throws Exception {
        when(skills.list(any(), any(), anyInt(), anyInt())).thenReturn(pageOf());

        mockMvc.perform(get("/api/skills")).andExpect(status().isOk());

        verify(skills).list(null, null, 0, 50);
    }

    @Test
    void listPassesEveryParameterThrough() throws Exception {
        when(skills.list(any(), any(), anyInt(), anyInt())).thenReturn(pageOf());

        mockMvc.perform(get("/api/skills")
                        .param("active", "false")
                        .param("search", "eva")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk());

        verify(skills).list(false, "eva", 2, 10);
    }

    @Test
    void aNegativePageIsAValidationFailure() throws Exception {
        mockMvc.perform(get("/api/skills").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"));

        verify(skills, never()).list(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("A page size beyond the cap is refused rather than quietly clamped")
    void anOversizedPageIsAValidationFailure() throws Exception {
        mockMvc.perform(get("/api/skills").param("size", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"));

        verify(skills, never()).list(any(), any(), anyInt(), anyInt());
    }

    @Test
    void aZeroPageSizeIsAValidationFailure() throws Exception {
        mockMvc.perform(get("/api/skills").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"));
    }

    @Test
    void anOverlongSearchTermIsAValidationFailure() throws Exception {
        mockMvc.perform(get("/api/skills").param("search", "x".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"));
    }

    @Test
    void aNonBooleanActiveFilterIsAValidationFailure() throws Exception {
        mockMvc.perform(get("/api/skills").param("active", "maybe"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"));
    }

    @Test
    void getReturnsTheSkill() throws Exception {
        when(skills.get(SKILL_ID)).thenReturn(eva());

        mockMvc.perform(get("/api/skills/{id}", SKILL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SKILL_ID.toString()))
                .andExpect(jsonPath("$.name").value("EVA Operations"))
                .andExpect(jsonPath("$.description")
                        .value("Suit handling, tethering, external repair."));
    }

    @Test
    void anAbsentSkillIsANotFoundProblem() throws Exception {
        when(skills.get(eq(SKILL_ID))).thenThrow(new SkillNotFoundException());

        mockMvc.perform(get("/api/skills/{id}", SKILL_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:not-found"))
                .andExpect(jsonPath("$.title").value("Not found"));
    }

    @Test
    @DisplayName("A malformed id is a 400, not a 500")
    void aMalformedIdIsAValidationFailure() throws Exception {
        mockMvc.perform(get("/api/skills/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"));

        verify(skills, never()).get(any());
    }

    @Test
    @DisplayName("FR-7: there is no endpoint that deletes a skill")
    void thereIsNoDeleteEndpoint() throws Exception {
        mockMvc.perform(delete("/api/skills/{id}", SKILL_ID))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:method-not-allowed"));

        verify(skills, never()).get(any());
    }
}
