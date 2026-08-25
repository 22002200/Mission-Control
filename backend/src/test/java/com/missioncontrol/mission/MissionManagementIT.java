package com.missioncontrol.mission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.missioncontrol.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Mission management end to end, over real HTTP against a real database.
 *
 * <p>Everything here stays within the part of the lifecycle feature 04 owns, which means every
 * mission this class creates is in {@code PLAN}. The states beyond that cannot be reached through
 * the API until feature 05 adds approval, so they are covered by
 * {@code com.missioncontrol.mission.internal.MissionLifecycleIT}, which can arrange them directly.
 *
 * <p>All integration tests share one database. This class only ever creates missions and never
 * logs anyone out, so it can safely use the same seeded leads other classes read.
 */
class MissionManagementIT extends AbstractIntegrationTest {

    /**
     * A second mission lead in organisation A, needed to show that one lead cannot reach another
     * one's work. Declared here rather than in the shared base because only this class needs it.
     */
    private static final String MISSION_LEAD_A2 = "priya.raman@orbitaldynamics.example";

    private static final String EVA_SKILL_A = "a2000000-0000-0000-0000-000000000001";
    private static final String ROBOTICS_SKILL_A = "a2000000-0000-0000-0000-000000000002";
    private static final String LIFE_SUPPORT_SKILL_A = "a2000000-0000-0000-0000-000000000003";
    private static final String EVA_SKILL_B = "b2000000-0000-0000-0000-000000000001";

    /** Seeded, owned by Marcus Reyes, and left in PLAN by the changelog. */
    private static final String SEEDED_PLAN_MISSION = "a4000000-0000-0000-0000-000000000001";

    private static final String PROBLEM_JSON = "application/problem+json";

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request,
                                                 String email) throws Exception {
        return request.header("Authorization", bearer(tokenFor(email)));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String createMission(String email, String name) throws Exception {
        MvcResult result = mockMvc.perform(asUser(json(post("/api/missions"), """
                        {"name": "%s", "description": "Created by a test.",
                         "startsAt": "2027-03-01T08:00:00Z", "endsAt": "2027-03-20T17:00:00Z"}
                        """.formatted(name)), email))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asText();
    }

    // ---------------------------------------------------------------- creating

    @Test
    @DisplayName("A mission lead can create a mission, and it starts in PLAN owned by them")
    void aMissionLeadCanCreateAMission() throws Exception {
        mockMvc.perform(asUser(json(post("/api/missions"), """
                        {"name": "Corona Watch", "startsAt": "2027-04-01T08:00:00Z",
                         "endsAt": "2027-04-20T17:00:00Z"}
                        """), MISSION_LEAD_A))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLAN"))
                .andExpect(jsonPath("$.missionLead.fullName").value("Marcus Reyes"))
                .andExpect(jsonPath("$.fullyStaffed").value(false))
                .andExpect(jsonPath("$.requirements").isEmpty());
    }

    @Test
    @DisplayName("A director attempting to create a mission receives 403 - M2")
    void aDirectorCannotCreateAMission() throws Exception {
        mockMvc.perform(asUser(json(post("/api/missions"), """
                        {"name": "X", "startsAt": "2027-04-01T08:00:00Z",
                         "endsAt": "2027-04-20T17:00:00Z"}
                        """), DIRECTOR_A))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:mission-control:forbidden"));
    }

    @Test
    void aCrewMemberCannotCreateAMission() throws Exception {
        mockMvc.perform(asUser(json(post("/api/missions"), """
                        {"name": "X", "startsAt": "2027-04-01T08:00:00Z",
                         "endsAt": "2027-04-20T17:00:00Z"}
                        """), CREW_A))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A mission whose endsAt precedes its startsAt is rejected - M1")
    void reversedDatesAreRejected() throws Exception {
        mockMvc.perform(asUser(json(post("/api/missions"), """
                        {"name": "Backwards", "startsAt": "2027-04-20T08:00:00Z",
                         "endsAt": "2027-04-01T17:00:00Z"}
                        """), MISSION_LEAD_A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"));
    }

    @Test
    void creatingWithoutATokenIsRejected() throws Exception {
        mockMvc.perform(json(post("/api/missions"), "{}"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------- visibility

    @Test
    @DisplayName("A mission lead sees only their own missions; a director sees all of them")
    void listsAreScopedByRole() throws Exception {
        MvcResult leadResult = mockMvc.perform(asUser(get("/api/missions?size=100"), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult directorResult = mockMvc.perform(asUser(get("/api/missions?size=100"), DIRECTOR_A))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode leadsOwn = json(leadResult).get("content");
        assertThat(leadsOwn).isNotEmpty();
        leadsOwn.forEach(mission ->
                assertThat(mission.get("missionLead").get("fullName").asText())
                        .isEqualTo("Marcus Reyes"));

        assertThat(json(directorResult).get("totalElements").asInt())
                .isGreaterThan(json(leadResult).get("totalElements").asInt());
    }

    @Test
    @DisplayName("A crew member with no assignments sees an empty list, not an error")
    void crewSeeOnlyMissionsTheyAreAssignedTo() throws Exception {
        mockMvc.perform(asUser(get("/api/missions"), CREW_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("A mission from another organisation returns 404 - T2")
    void anotherOrganisationsMissionIsNotFound() throws Exception {
        mockMvc.perform(asUser(get("/api/missions/{id}", SEEDED_PLAN_MISSION), DIRECTOR_B))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:mission-control:not-found"));
    }

    @Test
    @DisplayName("A cross-tenant miss is indistinguishable from an id that never existed")
    void aCrossTenantMissLooksLikeAnUnknownId() throws Exception {
        MvcResult crossTenant = mockMvc.perform(
                        asUser(get("/api/missions/{id}", SEEDED_PLAN_MISSION), DIRECTOR_B))
                .andExpect(status().isNotFound())
                .andReturn();
        MvcResult unknown = mockMvc.perform(asUser(
                        get("/api/missions/{id}", "00000000-0000-0000-0000-000000000000"), DIRECTOR_B))
                .andExpect(status().isNotFound())
                .andReturn();

        // `instance` differs by construction - it is the request path - so it is excluded.
        assertThat(withoutInstance(json(crossTenant))).isEqualTo(withoutInstance(json(unknown)));
    }

    @Test
    @DisplayName("A second mission lead cannot read a mission they do not own")
    void anotherLeadCannotReadTheMission() throws Exception {
        String id = createMission(MISSION_LEAD_A, "Owned by Marcus");

        mockMvc.perform(asUser(get("/api/missions/{id}", id), MISSION_LEAD_A2))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A second mission lead cannot edit a mission they do not own - M6")
    void anotherLeadCannotEditTheMission() throws Exception {
        String id = createMission(MISSION_LEAD_A, "Also owned by Marcus");

        mockMvc.perform(asUser(json(patch("/api/missions/{id}", id), """
                        {"name": "Hijacked"}
                        """), MISSION_LEAD_A2))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A director in the same organisation may edit any mission - M6")
    void aDirectorMayEditAnyMission() throws Exception {
        String id = createMission(MISSION_LEAD_A, "Director edits this");

        mockMvc.perform(asUser(json(patch("/api/missions/{id}", id), """
                        {"description": "Amended by the director."}
                        """), DIRECTOR_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Amended by the director."))
                .andExpect(jsonPath("$.missionLead.fullName").value("Marcus Reyes"));
    }

    // ------------------------------------------------------------ requirements

    @Test
    @DisplayName("A requirement is created with its skills inline, and the names come back - FR-8")
    void requirementsCarryTheirSkills() throws Exception {
        String id = createMission(MISSION_LEAD_A, "Needs crew");

        mockMvc.perform(asUser(json(post("/api/missions/{id}/requirements", id), """
                        {"title": "Flight Engineer", "requiredCount": 2,
                         "skills": [{"skillId": "%s", "minimumProficiency": 3,
                                     "mandatory": true, "weight": 2}]}
                        """.formatted(EVA_SKILL_A)), MISSION_LEAD_A))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requiredCount").value(2))
                .andExpect(jsonPath("$.acceptedCount").value(0))
                .andExpect(jsonPath("$.skills[0].skillName").value("EVA Operations"));

        mockMvc.perform(asUser(get("/api/missions/{id}", id), MISSION_LEAD_A))
                .andExpect(jsonPath("$.requirements[0].title").value("Flight Engineer"))
                .andExpect(jsonPath("$.fullyStaffed").value(false));
    }

    @Test
    @DisplayName("A requirement listing the same skill twice is rejected - M10")
    void duplicateSkillsAreRejected() throws Exception {
        String id = createMission(MISSION_LEAD_A, "Duplicate skills");

        mockMvc.perform(asUser(json(post("/api/missions/{id}/requirements", id), """
                        {"title": "Engineer", "requiredCount": 1,
                         "skills": [{"skillId": "%s", "minimumProficiency": 3, "mandatory": true},
                                    {"skillId": "%s", "minimumProficiency": 4, "mandatory": false}]}
                        """.formatted(EVA_SKILL_A, EVA_SKILL_A)), MISSION_LEAD_A))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:duplicate-skill"));
    }

    @Test
    @DisplayName("A requirement with requiredCount of 0 is rejected - M9")
    void zeroRequiredCountIsRejected() throws Exception {
        String id = createMission(MISSION_LEAD_A, "Zero count");

        mockMvc.perform(asUser(json(post("/api/missions/{id}/requirements", id), """
                        {"title": "Engineer", "requiredCount": 0}
                        """), MISSION_LEAD_A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.requiredCount").exists());
    }

    @Test
    @DisplayName("A skill from another organisation cannot be required - T2")
    void aCrossTenantSkillIsRejected() throws Exception {
        String id = createMission(MISSION_LEAD_A, "Foreign skill");

        mockMvc.perform(asUser(json(post("/api/missions/{id}/requirements", id), """
                        {"title": "Engineer", "requiredCount": 1,
                         "skills": [{"skillId": "%s", "minimumProficiency": 3, "mandatory": true}]}
                        """.formatted(EVA_SKILL_B)), MISSION_LEAD_A))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:invalid-skill"));
    }

    @Test
    @DisplayName("Updating a requirement replaces its skills; deleting removes the line")
    void requirementsCanBeUpdatedAndRemoved() throws Exception {
        String id = createMission(MISSION_LEAD_A, "Editable requirements");
        MvcResult added = mockMvc.perform(asUser(
                        json(post("/api/missions/{id}/requirements", id), """
                                {"title": "Engineer", "requiredCount": 1,
                                 "skills": [{"skillId": "%s", "minimumProficiency": 3,
                                             "mandatory": true}]}
                                """.formatted(EVA_SKILL_A)), MISSION_LEAD_A))
                .andExpect(status().isCreated())
                .andReturn();
        String requirementId = json(added).get("id").asText();

        mockMvc.perform(asUser(json(
                        patch("/api/missions/{id}/requirements/{req}", id, requirementId), """
                                {"title": "Senior Engineer", "requiredCount": 3,
                                 "skills": [{"skillId": "%s", "minimumProficiency": 5,
                                             "mandatory": true, "weight": 4}]}
                                """.formatted(ROBOTICS_SKILL_A)), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Senior Engineer"))
                .andExpect(jsonPath("$.requiredCount").value(3))
                .andExpect(jsonPath("$.skills.length()").value(1))
                .andExpect(jsonPath("$.skills[0].skillName").value("Robotics"));

        mockMvc.perform(asUser(
                        delete("/api/missions/{id}/requirements/{req}", id, requirementId),
                        MISSION_LEAD_A))
                .andExpect(status().isNoContent());

        mockMvc.perform(asUser(get("/api/missions/{id}", id), MISSION_LEAD_A))
                .andExpect(jsonPath("$.requirements").isEmpty());
    }

    /**
     * Adds a requirement asking for the given skills, and returns its id.
     *
     * <p>Each entry is {@code skillId:minimumProficiency}.
     */
    private String addRequirement(String missionId, String title, String... skills)
            throws Exception {
        String inline = java.util.Arrays.stream(skills)
                .map(spec -> spec.split(":"))
                .map(parts -> """
                        {"skillId": "%s", "minimumProficiency": %s, "mandatory": true}
                        """.formatted(parts[0], parts[1]))
                .collect(java.util.stream.Collectors.joining(","));

        MvcResult added = mockMvc.perform(asUser(
                        json(post("/api/missions/{id}/requirements", missionId), """
                                {"title": "%s", "requiredCount": 1, "skills": [%s]}
                                """.formatted(title, inline)), MISSION_LEAD_A))
                .andExpect(status().isCreated())
                .andReturn();
        return json(added).get("id").asText();
    }

    @Test
    @DisplayName("A requirement can be edited while keeping a skill it already asks for")
    void aRetainedSkillIsUpdatedInPlace() throws Exception {
        // The regression this exists for: an edit that keeps a skill used to build a second
        // RequiredSkillEntity carrying the identifier the removed one still held, and Hibernate
        // refused it at the next flush. It surfaced as a 500 from whatever query flushed first,
        // which pointed at the read path rather than at the write that caused it.
        String id = createMission(MISSION_LEAD_A, "Retained skill");
        String requirementId = addRequirement(id, "Engineer", EVA_SKILL_A + ":3");

        mockMvc.perform(asUser(json(
                        patch("/api/missions/{id}/requirements/{req}", id, requirementId), """
                                {"title": "Senior Engineer", "requiredCount": 2,
                                 "skills": [{"skillId": "%s", "minimumProficiency": 5,
                                             "mandatory": false, "weight": 4}]}
                                """.formatted(EVA_SKILL_A)), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Senior Engineer"))
                .andExpect(jsonPath("$.skills.length()").value(1))
                .andExpect(jsonPath("$.skills[0].skillName").value("EVA Operations"))
                .andExpect(jsonPath("$.skills[0].minimumProficiency").value(5))
                .andExpect(jsonPath("$.skills[0].mandatory").value(false))
                .andExpect(jsonPath("$.skills[0].weight").value(4));
    }

    @Test
    @DisplayName("Resending a requirement unchanged is accepted, which is what the edit form does")
    void resubmittingTheSameSkillsIsAccepted() throws Exception {
        // The form pre-populates the existing skills, so changing only the title sends every one
        // of them straight back. That is the most ordinary edit there is and it has to work.
        String id = createMission(MISSION_LEAD_A, "Unchanged skills");
        String requirementId =
                addRequirement(id, "Engineer", EVA_SKILL_A + ":3", ROBOTICS_SKILL_A + ":2");

        mockMvc.perform(asUser(json(
                        patch("/api/missions/{id}/requirements/{req}", id, requirementId), """
                                {"title": "Renamed only", "requiredCount": 1,
                                 "skills": [{"skillId": "%s", "minimumProficiency": 3,
                                             "mandatory": true},
                                            {"skillId": "%s", "minimumProficiency": 2,
                                             "mandatory": true}]}
                                """.formatted(EVA_SKILL_A, ROBOTICS_SKILL_A)), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed only"))
                .andExpect(jsonPath("$.skills.length()").value(2));
    }

    @Test
    @DisplayName("An edit that keeps one skill, drops another and adds a third settles correctly")
    void aPartiallyOverlappingSkillSetReconciles() throws Exception {
        String id = createMission(MISSION_LEAD_A, "Overlapping skills");
        String requirementId =
                addRequirement(id, "Engineer", EVA_SKILL_A + ":3", ROBOTICS_SKILL_A + ":2");

        mockMvc.perform(asUser(json(
                        patch("/api/missions/{id}/requirements/{req}", id, requirementId), """
                                {"title": "Engineer", "requiredCount": 1,
                                 "skills": [{"skillId": "%s", "minimumProficiency": 4,
                                             "mandatory": true},
                                            {"skillId": "%s", "minimumProficiency": 1,
                                             "mandatory": false}]}
                                """.formatted(EVA_SKILL_A, LIFE_SUPPORT_SKILL_A)), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills.length()").value(2));

        // Read it back rather than trusting the write response: the rows are what the next request
        // will see, and a reconcile that went wrong can still return a plausible body.
        mockMvc.perform(asUser(get("/api/missions/{id}", id), MISSION_LEAD_A))
                .andExpect(jsonPath("$.requirements[0].skills.length()").value(2))
                .andExpect(jsonPath("$.requirements[0].skills[*].skillName")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(
                                "EVA Operations", "Life Support Systems")))
                .andExpect(jsonPath("$.requirements[0].skills[?(@.skillName=='EVA Operations')]"
                        + ".minimumProficiency").value(4));
    }

    @Test
    @DisplayName("A requirement can be edited twice in a row")
    void aRequirementCanBeEditedRepeatedly() throws Exception {
        String id = createMission(MISSION_LEAD_A, "Edited twice");
        String requirementId = addRequirement(id, "Engineer", EVA_SKILL_A + ":3");

        for (int proficiency : new int[]{4, 5}) {
            mockMvc.perform(asUser(json(
                            patch("/api/missions/{id}/requirements/{req}", id, requirementId), """
                                    {"title": "Engineer", "requiredCount": 1,
                                     "skills": [{"skillId": "%s", "minimumProficiency": %d,
                                                 "mandatory": true}]}
                                    """.formatted(EVA_SKILL_A, proficiency)), MISSION_LEAD_A))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.skills[0].minimumProficiency").value(proficiency));
        }
    }

    @Test
    @DisplayName("Clearing every skill from a requirement leaves it with none")
    void everySkillCanBeRemoved() throws Exception {
        String id = createMission(MISSION_LEAD_A, "Cleared skills");
        String requirementId = addRequirement(id, "Engineer", EVA_SKILL_A + ":3");

        mockMvc.perform(asUser(json(
                        patch("/api/missions/{id}/requirements/{req}", id, requirementId), """
                                {"title": "Engineer", "requiredCount": 1, "skills": []}
                                """), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills").isEmpty());

        mockMvc.perform(asUser(get("/api/missions/{id}", id), MISSION_LEAD_A))
                .andExpect(jsonPath("$.requirements[0].skills").isEmpty());
    }

    @Test
    @DisplayName("A director may edit the mission but not its requirements - BR-10")
    void directorsCannotTouchRequirements() throws Exception {
        String id = createMission(MISSION_LEAD_A, "Director and requirements");

        mockMvc.perform(asUser(json(post("/api/missions/{id}/requirements", id), """
                        {"title": "Engineer", "requiredCount": 1}
                        """), DIRECTOR_A))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:mission-control:forbidden"));
    }

    @Test
    @DisplayName("A requirement id from a different mission is reported as absent")
    void aRequirementFromAnotherMissionIsNotFound() throws Exception {
        String first = createMission(MISSION_LEAD_A, "Owns the requirement");
        String second = createMission(MISSION_LEAD_A, "Does not own it");
        MvcResult added = mockMvc.perform(asUser(
                        json(post("/api/missions/{id}/requirements", first),
                                "{\"title\":\"Engineer\",\"requiredCount\":1}"), MISSION_LEAD_A))
                .andReturn();
        String requirementId = json(added).get("id").asText();

        mockMvc.perform(asUser(
                        delete("/api/missions/{id}/requirements/{req}", second, requirementId),
                        MISSION_LEAD_A))
                .andExpect(status().isNotFound());
    }

    // --------------------------------------------------------------- lifecycle

    @Test
    @DisplayName("Starting a mission that is not APPROVED is rejected - M3")
    void startingAPlannedMissionIsRejected() throws Exception {
        String id = createMission(MISSION_LEAD_A, "Not approved");

        mockMvc.perform(asUser(post("/api/missions/{id}/start", id), MISSION_LEAD_A))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:invalid-transition"))
                .andExpect(jsonPath("$.currentStatus").value("PLAN"))
                .andExpect(jsonPath("$.attemptedTransition").value("ACTIVE"));
    }

    @Test
    @DisplayName("Closing from PLAN without a reason records ABORTED - BR-11")
    void closingFromPlanRecordsAborted() throws Exception {
        String id = createMission(MISSION_LEAD_A, "To be aborted");

        mockMvc.perform(asUser(json(post("/api/missions/{id}/close", id), """
                        {"comment": "Funding withdrawn."}
                        """), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closeReason").value("ABORTED"))
                .andExpect(jsonPath("$.closeComment").value("Funding withdrawn."));
    }

    @Test
    @DisplayName("A mission that was never rejected cannot be closed as REJECTED")
    void rejectedIsNotAFreelyChosenCloseReason() throws Exception {
        String id = createMission(MISSION_LEAD_A, "Not rejected");

        mockMvc.perform(asUser(json(post("/api/missions/{id}/close", id), """
                        {"closeReason": "REJECTED"}
                        """), MISSION_LEAD_A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"));
    }

    @Test
    @DisplayName("A closed mission rejects every further edit and transition - M3")
    void aClosedMissionIsTerminal() throws Exception {
        String id = createMission(MISSION_LEAD_A, "Terminal");
        mockMvc.perform(asUser(json(post("/api/missions/{id}/close", id), "{}"), MISSION_LEAD_A))
                .andExpect(status().isOk());

        mockMvc.perform(asUser(json(patch("/api/missions/{id}", id), "{\"name\":\"Nope\"}"),
                        MISSION_LEAD_A))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:invalid-transition"));

        mockMvc.perform(asUser(json(post("/api/missions/{id}/close", id), "{}"), MISSION_LEAD_A))
                .andExpect(status().isConflict());

        mockMvc.perform(asUser(post("/api/missions/{id}/start", id), MISSION_LEAD_A))
                .andExpect(status().isConflict());

        mockMvc.perform(asUser(json(post("/api/missions/{id}/requirements", id),
                        "{\"title\":\"Engineer\",\"requiredCount\":1}"), MISSION_LEAD_A))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:mission-not-editable"));
    }

    // ----------------------------------------------------------------- filters

    @Test
    @DisplayName("The status filter is repeatable, which is what the three board sections need")
    void theStatusFilterAcceptsSeveralValues() throws Exception {
        MvcResult result = mockMvc.perform(
                        asUser(get("/api/missions?status=ACTIVE&status=CLOSED&size=100"), DIRECTOR_A))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = json(result).get("content");
        assertThat(content).isNotEmpty();
        content.forEach(mission -> assertThat(mission.get("status").asText())
                .isIn("ACTIVE", "CLOSED"));
    }

    @Test
    void theListIsOrderedByStartDate() throws Exception {
        MvcResult result = mockMvc.perform(asUser(get("/api/missions?size=100"), DIRECTOR_A))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = json(result).get("content");
        String previous = null;
        for (JsonNode mission : content) {
            String startsAt = mission.get("startsAt").asText();
            if (previous != null) {
                assertThat(startsAt).isGreaterThanOrEqualTo(previous);
            }
            previous = startsAt;
        }
    }

    @Test
    void theSearchTermMatchesTheNameCaseInsensitively() throws Exception {
        mockMvc.perform(asUser(get("/api/missions?search=AURORA"), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Aurora Survey"));
    }

    @Test
    @DisplayName("A wildcard in the search term is escaped rather than honoured")
    void aWildcardInTheSearchTermMatchesNothing() throws Exception {
        mockMvc.perform(asUser(get("/api/missions?search=%25"), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("No response anywhere echoes the organisation back")
    void noResponseCarriesTheOrganisation() throws Exception {
        String body = mockMvc.perform(asUser(get("/api/missions?size=100"), DIRECTOR_A))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("organisationId");
    }

    private JsonNode withoutInstance(JsonNode problem) {
        return ((ObjectNode) problem).without("instance");
    }
}
