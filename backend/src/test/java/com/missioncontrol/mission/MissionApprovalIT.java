package com.missioncontrol.mission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.missioncontrol.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The approval lifecycle end to end, over HTTP, against a real database.
 *
 * <p><strong>Not {@code Transactional}, and it creates its own missions.</strong> Both matter.
 * These tests are about what survives a commit - two cycles in a history, a decision that another
 * caller then loses a race to - and a test-managed transaction would roll the arrangement back
 * before the assertion could see it. And every integration test shares one database, with
 * {@code MissionManagementIT} asserting on the seeded {@code PLAN} mission, so mutating a seeded
 * row here would break a test in another file for reasons invisible from either. Now that submit
 * exists, building the fixture through the API is also simply the more honest arrangement.
 *
 * <p>Whatever is created is deleted again in {@code AfterEach}. Nothing rolls back, and a shared
 * database that only ever grows makes the next failure harder to read than it needs to be.
 */
class MissionApprovalIT extends AbstractIntegrationTest {

    /** The second lead in organisation A, for the case where a mission is not the caller's. */
    private static final String OTHER_LEAD_A = "priya.raman@orbitaldynamics.example";

    /** A seeded, active skill in organisation A - requirements have to name a real one. */
    private static final String EVA_SKILL = "a2000000-0000-0000-0000-000000000001";

    @Autowired private JdbcTemplate jdbc;

    private final List<String> created = new ArrayList<>();

    @AfterEach
    void removeWhatThisTestCreated() {
        // mission_approval, crew_requirement and required_skill all cascade from mission.
        created.forEach(id -> jdbc.update("DELETE FROM mission WHERE id = ?::uuid", id));
        created.clear();
    }

    @Test
    @DisplayName("An owning lead submits a staffed plan and a director approves it - FR-1, FR-2")
    void submitAndApprove() throws Exception {
        String mission = givenSubmittableMission("Approval happy path");

        mockMvc.perform(as(post("/api/missions/{id}/submit", mission), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        // FR-8: the director's queue is the existing list, filtered - there is no separate endpoint.
        mockMvc.perform(as(get("/api/missions?status=PENDING_APPROVAL"), DIRECTOR_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + mission + "')]").exists());

        mockMvc.perform(as(post("/api/missions/{id}/approve", mission), DIRECTOR_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Cleared for planning.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        JsonNode history = historyOf(mission, MISSION_LEAD_A);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("decision").asText()).isEqualTo("APPROVED");
        assertThat(history.get(0).get("submittedBy").get("fullName").asText())
                .isEqualTo("Marcus Reyes");
        assertThat(history.get(0).get("decidedBy").get("fullName").asText())
                .isEqualTo("Vera Lindholm");
        assertThat(history.get(0).get("comment").asText()).isEqualTo("Cleared for planning.");
    }

    @Test
    @DisplayName("Submitting a mission with no crew requirements is refused - M12, BR-5")
    void submitWithoutRequirements() throws Exception {
        String mission = givenMission("Nothing to staff");

        mockMvc.perform(as(post("/api/missions/{id}/submit", mission), MISSION_LEAD_A))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value("urn:mission-control:mission-has-no-requirements"));

        assertThat(historyOf(mission, MISSION_LEAD_A)).isEmpty();
    }

    @Test
    @DisplayName("A lead who does not own the mission cannot see it, let alone submit it")
    void nonOwningLeadGetsNotFound() throws Exception {
        String mission = givenSubmittableMission("Not yours to submit");

        mockMvc.perform(as(post("/api/missions/{id}/submit", mission), OTHER_LEAD_A))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:mission-control:not-found"));
    }

    @Test
    @DisplayName("A director cannot submit - they can see the mission, so they are told no")
    void directorCannotSubmit() throws Exception {
        String mission = givenSubmittableMission("Directors do not plan");

        mockMvc.perform(as(post("/api/missions/{id}/submit", mission), DIRECTOR_A))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:mission-control:forbidden"));
    }

    @Test
    @DisplayName("Submitting twice is refused, and says where the mission already is")
    void submittingTwice() throws Exception {
        String mission = givenSubmittableMission("Submitted once already");
        submit(mission);

        mockMvc.perform(as(post("/api/missions/{id}/submit", mission), MISSION_LEAD_A))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:invalid-transition"))
                .andExpect(jsonPath("$.currentStatus").value("PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("A mission lead cannot approve, even their own mission")
    void leadCannotApprove() throws Exception {
        String mission = givenSubmittableMission("Not the lead's call");
        submit(mission);

        mockMvc.perform(as(post("/api/missions/{id}/approve", mission), MISSION_LEAD_A))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:mission-control:forbidden"));
    }

    @Test
    @DisplayName("A director in another organisation gets 404, never 403 - T2")
    void directorFromAnotherOrganisation() throws Exception {
        String mission = givenSubmittableMission("Another tenant cannot see this");
        submit(mission);

        mockMvc.perform(as(post("/api/missions/{id}/approve", mission), DIRECTOR_B))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:mission-control:not-found"));

        mockMvc.perform(as(get("/api/missions/{id}/approvals", mission), DIRECTOR_B))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Rejecting without a comment is refused before anything moves - BR-6")
    void rejectionNeedsAComment() throws Exception {
        String mission = givenSubmittableMission("A rejection has to say why");
        submit(mission);

        mockMvc.perform(as(post("/api/missions/{id}/reject", mission), DIRECTOR_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"));

        // Still awaiting a decision: a refused request leaves nothing half-applied.
        mockMvc.perform(as(get("/api/missions/{id}", mission), MISSION_LEAD_A))
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
        assertThat(pendingCycleCount(mission)).isEqualTo(1);
    }

    @Test
    @DisplayName("Approving twice is refused, and names the status it is now in")
    void approvingTwice() throws Exception {
        String mission = givenSubmittableMission("Decided once already");
        submit(mission);
        mockMvc.perform(as(post("/api/missions/{id}/approve", mission), DIRECTOR_A))
                .andExpect(status().isOk());

        mockMvc.perform(as(post("/api/missions/{id}/approve", mission), DIRECTOR_A))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:invalid-transition"))
                .andExpect(jsonPath("$.currentStatus").value("APPROVED"))
                .andExpect(jsonPath("$.attemptedTransition").value("APPROVED"));
    }

    @Test
    @DisplayName("Reject, replan, edit, resubmit, approve - two cycles in order, never two pending")
    void theWholeLoop() throws Exception {
        String mission = givenSubmittableMission("The full loop");

        submit(mission);
        assertThat(pendingCycleCount(mission)).isEqualTo(1);

        mockMvc.perform(as(post("/api/missions/{id}/reject", mission), DIRECTOR_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"The window clashes with the Vesta flyby.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(pendingCycleCount(mission)).isZero();

        mockMvc.perform(as(post("/api/missions/{id}/replan", mission), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLAN"));
        // BR-9: returning to plan settles nothing and opens nothing.
        assertThat(historyOf(mission, MISSION_LEAD_A)).hasSize(1);
        assertThat(pendingCycleCount(mission)).isZero();

        mockMvc.perform(as(patch("/api/missions/{id}", mission), MISSION_LEAD_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Timeline reshaped.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLAN"));

        submit(mission);
        assertThat(pendingCycleCount(mission)).isEqualTo(1);

        mockMvc.perform(as(post("/api/missions/{id}/approve", mission), DIRECTOR_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // FR-7: each cycle is its own record, newest first.
        JsonNode history = historyOf(mission, MISSION_LEAD_A);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).get("decision").asText()).isEqualTo("APPROVED");
        assertThat(history.get(1).get("decision").asText()).isEqualTo("REJECTED");
        assertThat(history.get(1).get("comment").asText())
                .isEqualTo("The window clashes with the Vesta flyby.");
        assertThat(history.get(0).get("id").asText())
                .isNotEqualTo(history.get(1).get("id").asText());
    }

    @Test
    @DisplayName("A rejected mission can be closed as REJECTED instead - FR-5")
    void rejectedMissionMayBeClosed() throws Exception {
        String mission = givenSubmittableMission("Abandoned after rejection");
        submit(mission);
        mockMvc.perform(as(post("/api/missions/{id}/reject", mission), DIRECTOR_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Not viable this cycle.\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(as(post("/api/missions/{id}/close", mission), MISSION_LEAD_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closeReason\":\"REJECTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closeReason").value("REJECTED"));

        // The rejection stands as the record of what happened. Closing did not rewrite it.
        JsonNode history = historyOf(mission, MISSION_LEAD_A);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("decision").asText()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("Closing a mission awaiting a decision cancels the open cycle - feature 05")
    void closingCancelsAnOpenCycle() throws Exception {
        String mission = givenSubmittableMission("Stood down mid-approval");
        submit(mission);

        mockMvc.perform(as(post("/api/missions/{id}/close", mission), DIRECTOR_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Programme cancelled.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closeReason").value("ABORTED"));

        JsonNode history = historyOf(mission, DIRECTOR_A);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("decision").asText()).isEqualTo("CANCELLED");
        assertThat(history.get(0).get("comment").asText()).isEqualTo("Programme cancelled.");
        assertThat(pendingCycleCount(mission)).isZero();
    }

    @Test
    @DisplayName("Returning an approved mission to plan is refused - the arrow belongs to editing")
    void replanOnlyAppliesToARejectedMission() throws Exception {
        String mission = givenSubmittableMission("Approved, not rejected");
        submit(mission);
        mockMvc.perform(as(post("/api/missions/{id}/approve", mission), DIRECTOR_A))
                .andExpect(status().isOk());

        // APPROVED to PLAN is a legal transition - that is M5, an edit discarding an approval - so
        // this endpoint has to name REJECTED explicitly rather than trust the transition table.
        mockMvc.perform(as(post("/api/missions/{id}/replan", mission), MISSION_LEAD_A))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:invalid-transition"))
                .andExpect(jsonPath("$.currentStatus").value("APPROVED"));
    }

    @Test
    @DisplayName("The seeded missions past PLAN already have a consistent history")
    void seededMissionsHaveHistory() throws Exception {
        // Tethys Relay is seeded PENDING_APPROVAL. Feature 05 seeds the open cycle that put it
        // there, so the seed is a state the application itself could have produced - and a director
        // opening it on first login has something to decide.
        JsonNode pending = historyOf("a4000000-0000-0000-0000-000000000002", DIRECTOR_A);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).get("decision").asText()).isEqualTo("PENDING");
        assertThat(pending.get(0).get("decidedBy")).isNull();

        // Rhea Descent is seeded REJECTED, with the reason its lead has to act on.
        JsonNode rejected = historyOf("a4000000-0000-0000-0000-000000000006", DIRECTOR_A);
        assertThat(rejected).hasSize(1);
        assertThat(rejected.get(0).get("decision").asText()).isEqualTo("REJECTED");
        assertThat(rejected.get(0).get("comment").asText()).isNotBlank();

        // Aurora Survey is seeded in PLAN and was never submitted, so it has none. Io Survey was
        // aborted straight out of PLAN, so it has none either - both are cases, not omissions.
        assertThat(historyOf("a4000000-0000-0000-0000-000000000001", DIRECTOR_A)).isEmpty();
        assertThat(historyOf("a4000000-0000-0000-0000-000000000004", DIRECTOR_A)).isEmpty();
    }

    @Test
    @DisplayName("A crew member can read the history of a mission they can see, and no other")
    void crewMemberVisibility() throws Exception {
        // With no assignment module a crew member can see no mission at all, so every id is a 404.
        // That is the correct answer to the question rather than a stub: it runs through the same
        // read model feature 07 will supply.
        mockMvc.perform(as(get("/api/missions/{id}/approvals",
                        "a4000000-0000-0000-0000-000000000002"), CREW_A))
                .andExpect(status().isNotFound());
    }

    // --- helpers ------------------------------------------------------------------------------

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String email)
            throws Exception {
        return request.header("Authorization", bearer(tokenFor(email)));
    }

    /** A mission in PLAN, owned by {@code MISSION_LEAD_A}, with nothing to staff it. */
    private String givenMission(String name) throws Exception {
        String body = """
                {
                  "name": "%s",
                  "description": "Created by MissionApprovalIT.",
                  "startsAt": "2027-05-01T08:00:00Z",
                  "endsAt": "2027-05-20T17:00:00Z"
                }
                """.formatted(name);

        String id = json(mockMvc.perform(as(post("/api/missions"), MISSION_LEAD_A)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isCreated())
                        .andReturn())
                .get("id").asText();

        created.add(id);
        return id;
    }

    /** The same mission with one crew requirement, so M12 is satisfied. */
    private String givenSubmittableMission(String name) throws Exception {
        String id = givenMission(name);

        String requirement = """
                {
                  "title": "Flight Engineer",
                  "requiredCount": 1,
                  "skills": [{"skillId": "%s", "minimumProficiency": 3, "mandatory": true}]
                }
                """.formatted(EVA_SKILL);

        mockMvc.perform(as(post("/api/missions/{id}/requirements", id), MISSION_LEAD_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requirement))
                .andExpect(status().isCreated());

        return id;
    }

    private void submit(String mission) throws Exception {
        mockMvc.perform(as(post("/api/missions/{id}/submit", mission), MISSION_LEAD_A))
                .andExpect(status().isOk());
    }

    private JsonNode historyOf(String mission, String email) throws Exception {
        return json(mockMvc.perform(as(get("/api/missions/{id}/approvals", mission), email))
                .andExpect(status().isOk())
                .andReturn());
    }

    /**
     * Invariant M8, asked of the database rather than of the API.
     *
     * <p>Counted straight out of the table because the endpoint cannot distinguish 'no pending
     * cycle' from 'a pending cycle the response happens not to show'. This is the assertion that
     * would catch a second cycle being opened without the first being settled.
     */
    private int pendingCycleCount(String mission) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM mission_approval WHERE mission_id = ?::uuid AND decision = 1",
                Integer.class, mission);
        return count == null ? 0 : count;
    }
}
