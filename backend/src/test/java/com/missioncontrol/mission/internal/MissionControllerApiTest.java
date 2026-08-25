package com.missioncontrol.mission.internal;

import com.missioncontrol.mission.api.MissionStatus;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * The HTTP contract of the mission endpoints.
 *
 * <p>Filters are off: this is about what the controller and the exception handler produce, not
 * about who is allowed through. Which roles may call what is covered end to end by
 * {@code MissionManagementIT}.
 */
@WebMvcTest(controllers = MissionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class MissionControllerApiTest {

    private static final UUID MISSION = UUID.fromString("a4000000-0000-0000-0000-000000000001");
    private static final UUID LEAD = UUID.fromString("a1000000-0000-0000-0000-000000000002");
    private static final UUID REQUIREMENT = UUID.fromString("a5000000-0000-0000-0000-000000000001");

    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private MissionService missions;

    @Captor private ArgumentCaptor<List<MissionStatus>> statusCaptor;

    private static MissionResponse planned() {
        return new MissionResponse(MISSION, "Aurora Survey", "Mapping auroral activity.",
                MissionStatus.PLAN, null, null,
                Instant.parse("2026-09-01T08:00:00Z"), Instant.parse("2026-09-14T17:00:00Z"),
                new UserRef(LEAD, "Marcus Reyes"), false,
                List.of(new CrewRequirementResponse(REQUIREMENT, "Flight Engineer", null, 2, 0,
                        List.of())));
    }

    private static MissionPage pageOf(MissionSummaryResponse... entries) {
        return new MissionPage(List.of(entries), 0, 20, entries.length, 1);
    }

    private static MissionSummaryResponse summary() {
        return new MissionSummaryResponse(MISSION, "Aurora Survey", MissionStatus.PLAN, null,
                Instant.parse("2026-09-01T08:00:00Z"), Instant.parse("2026-09-14T17:00:00Z"),
                new UserRef(LEAD, "Marcus Reyes"), 0, 4, false);
    }

    @Test
    void listReturnsThePageAndItsMetadata() throws Exception {
        when(missions.list(any(), any(), anyInt(), anyInt())).thenReturn(pageOf(summary()));

        mockMvc.perform(get("/api/missions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Aurora Survey"))
                .andExpect(jsonPath("$.content[0].status").value("PLAN"))
                .andExpect(jsonPath("$.content[0].missionLead.fullName").value("Marcus Reyes"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    @DisplayName("A response never carries the organisation the caller already supplied")
    void listDoesNotLeakTheOrganisation() throws Exception {
        when(missions.list(any(), any(), anyInt(), anyInt())).thenReturn(pageOf(summary()));

        String body = mockMvc.perform(get("/api/missions"))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("organisationId");
    }

    @Test
    void defaultsAreNoFilterFirstPageAndTwenty() throws Exception {
        when(missions.list(any(), any(), anyInt(), anyInt())).thenReturn(pageOf());

        mockMvc.perform(get("/api/missions")).andExpect(status().isOk());

        verify(missions).list(null, null, 0, 20);
    }

    @Test
    @DisplayName("A repeated status parameter becomes a multi-value filter, not the last one")
    void statusParameterIsRepeatable() throws Exception {
        when(missions.list(any(), any(), anyInt(), anyInt())).thenReturn(pageOf());

        mockMvc.perform(get("/api/missions?status=PLAN&status=APPROVED&status=ACTIVE"))
                .andExpect(status().isOk());

        verify(missions).list(statusCaptor.capture(), eq(null), eq(0), eq(20));
        org.assertj.core.api.Assertions.assertThat(statusCaptor.getValue())
                .containsExactly(MissionStatus.PLAN, MissionStatus.APPROVED, MissionStatus.ACTIVE);
    }

    @Test
    void anUnknownStatusIsARequestError() throws Exception {
        mockMvc.perform(get("/api/missions?status=LAUNCHED"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"));
    }

    @Test
    void pageAndSizeAreBounded() throws Exception {
        mockMvc.perform(get("/api/missions?page=-1")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/missions?size=0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/missions?size=101")).andExpect(status().isBadRequest());
    }

    @Test
    void createReturns201AndTheMission() throws Exception {
        when(missions.create(any())).thenReturn(planned());

        mockMvc.perform(post("/api/missions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Aurora Survey","startsAt":"2026-09-01T08:00:00Z",
                                 "endsAt":"2026-09-14T17:00:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLAN"))
                .andExpect(jsonPath("$.requirements[0].acceptedCount").value(0));
    }

    @Test
    @DisplayName("A missing name is a 400 naming the field, not a 500")
    void createValidatesTheBody() throws Exception {
        mockMvc.perform(post("/api/missions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsAt":"2026-09-01T08:00:00Z","endsAt":"2026-09-14T17:00:00Z"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"))
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void createRequiresBothDates() throws Exception {
        mockMvc.perform(post("/api/missions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Aurora Survey\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.startsAt").exists())
                .andExpect(jsonPath("$.errors.endsAt").exists());
    }

    @Test
    @DisplayName("closeReason is absent, not null, while a mission is open")
    void optionalFieldsAreOmittedRatherThanNull() throws Exception {
        when(missions.get(MISSION)).thenReturn(planned());

        String body = mockMvc.perform(get("/api/missions/{id}", MISSION))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("closeReason");
    }

    @Test
    void aMissingMissionIsReportedAsNotFound() throws Exception {
        when(missions.get(MISSION)).thenThrow(new MissionNotFoundException());

        mockMvc.perform(get("/api/missions/{id}", MISSION))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:mission-control:not-found"))
                .andExpect(jsonPath("$.detail").value("No such mission."));
    }

    @Test
    @DisplayName("A malformed id is a 400, not a 500")
    void aMalformedIdIsARequestError() throws Exception {
        mockMvc.perform(get("/api/missions/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"));
    }

    @Test
    @DisplayName("An invalid transition carries both statuses so a client can spot a stale view")
    void invalidTransitionCarriesItsStatuses() throws Exception {
        when(missions.start(MISSION)).thenThrow(
                new InvalidMissionTransitionException(MissionStatus.PLAN, MissionStatus.ACTIVE));

        mockMvc.perform(post("/api/missions/{id}/start", MISSION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:invalid-transition"))
                .andExpect(jsonPath("$.currentStatus").value("PLAN"))
                .andExpect(jsonPath("$.attemptedTransition").value("ACTIVE"));
    }

    @Test
    @DisplayName("Under-staffing lists every short requirement, so the caller knows what to fix")
    void understaffingListsTheShortfalls() throws Exception {
        when(missions.start(MISSION)).thenThrow(new MissionUnderstaffedException(List.of(
                new MissionUnderstaffedException.Shortfall(REQUIREMENT, "Flight Engineer", 2, 1))));

        mockMvc.perform(post("/api/missions/{id}/start", MISSION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:mission-understaffed"))
                .andExpect(jsonPath("$.requirements[0].title").value("Flight Engineer"))
                .andExpect(jsonPath("$.requirements[0].requiredCount").value(2))
                .andExpect(jsonPath("$.requirements[0].acceptedCount").value(1));
    }

    @Test
    @DisplayName("Closing accepts no body at all - the defaults are the common case")
    void closeAcceptsAnAbsentBody() throws Exception {
        when(missions.close(eq(MISSION), any())).thenReturn(planned());

        mockMvc.perform(post("/api/missions/{id}/close", MISSION))
                .andExpect(status().isOk());
    }

    @Test
    void patchPassesThroughOnlyWhatWasSupplied() throws Exception {
        when(missions.update(eq(MISSION), any())).thenReturn(planned());
        ArgumentCaptor<UpdateMissionRequest> captor =
                ArgumentCaptor.forClass(UpdateMissionRequest.class);

        mockMvc.perform(patch("/api/missions/{id}", MISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isOk());

        verify(missions).update(eq(MISSION), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().name()).isEqualTo("Renamed");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().startsAt()).isNull();
    }

    @Test
    @DisplayName("There is no endpoint that replaces a mission wholesale")
    void thereIsNoPutEndpoint() throws Exception {
        mockMvc.perform(put("/api/missions/{id}", MISSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.type").value("urn:mission-control:method-not-allowed"));
    }
}
