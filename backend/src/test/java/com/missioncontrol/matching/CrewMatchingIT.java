package com.missioncontrol.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.missioncontrol.support.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Crew matching end to end, over real HTTP against a real database and the real seed data.
 *
 * <p>The Orbital Dynamics roster was seeded specifically so the worked example in
 * {@code docs/features/06-crew-matching.md} reproduces exactly, and the crew changelog says as much
 * at the top. No seeded requirement asks for that combination though - Aurora Survey's Flight
 * Engineer wants Life Support and Propulsion - so these tests build the example requirement through
 * the real feature 04 endpoints rather than reaching into the database.
 *
 * <p>All integration tests share one database. This class creates its own missions and never logs
 * anybody out, so it can safely read the same seeded users other classes use.
 */
class CrewMatchingIT extends AbstractIntegrationTest {

    /** A second lead in organisation A, to show that one lead cannot match against another's work. */
    private static final String MISSION_LEAD_A2 = "priya.raman@orbitaldynamics.example";
    private static final String MISSION_LEAD_B = "sofia.mendes@heliosaero.example";

    private static final String EVA_SKILL_A = "a2000000-0000-0000-0000-000000000001";
    private static final String ROBOTICS_SKILL_A = "a2000000-0000-0000-0000-000000000002";
    private static final String LIFE_SUPPORT_SKILL_A = "a2000000-0000-0000-0000-000000000003";

    private static final String ADA = "a3000000-0000-0000-0000-000000000001";
    private static final String BRUNO = "a3000000-0000-0000-0000-000000000002";
    private static final String CHEN = "a3000000-0000-0000-0000-000000000003";
    private static final String DANA = "a3000000-0000-0000-0000-000000000004";

    /** Seeded, owned by Marcus Reyes, in PLAN. */
    private static final String SEEDED_MISSION_A = "a4000000-0000-0000-0000-000000000001";
    /** Seeded, in Helios Aerospace, so invisible to everyone in Orbital Dynamics. */
    private static final String SEEDED_MISSION_B = "b4000000-0000-0000-0000-000000000001";

    private static final String PROBLEM_JSON = "application/problem+json";

    private static final String MATCH_ALL = "/api/missions/%s/matches";
    private static final String MATCH_ONE = "/api/missions/%s/requirements/%s/matches";

    // ------------------------------------------------------------------ setup

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request,
                                                 String email) throws Exception {
        return request.header("Authorization", bearer(tokenFor(email)));
    }

    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, String json) {
        return request.contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private String createMission(String email, String name) throws Exception {
        MvcResult result = mockMvc.perform(asUser(body(post("/api/missions"), """
                        {"name": "%s", "description": "Created by CrewMatchingIT.",
                         "startsAt": "2027-05-01T08:00:00Z", "endsAt": "2027-05-20T17:00:00Z"}
                        """.formatted(name)), email))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asText();
    }

    private String addRequirement(String email, String missionId, String body) throws Exception {
        MvcResult result = mockMvc.perform(
                        asUser(body(post("/api/missions/" + missionId + "/requirements"), body),
                                email))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asText();
    }

    /**
     * The spec's worked example: EVA Operations mandatory at 3, Robotics preferred at 4, both at
     * weight 1. Against the seeded roster that is Ada 1.000, Chen 0.750, Bruno 0.500, and Dana
     * excluded.
     */
    private String workedExampleRequirement(String missionId, int requiredCount) throws Exception {
        return addRequirement(MISSION_LEAD_A, missionId, """
                {"title": "EVA Specialist", "requiredCount": %d,
                 "skills": [
                   {"skillId": "%s", "minimumProficiency": 3, "mandatory": true, "weight": 1},
                   {"skillId": "%s", "minimumProficiency": 4, "mandatory": false, "weight": 1}]}
                """.formatted(requiredCount, EVA_SKILL_A, ROBOTICS_SKILL_A));
    }

    private JsonNode matchOne(String email, String missionId, String requirementId,
                              String... params) throws Exception {
        MockHttpServletRequestBuilder request =
                get(MATCH_ONE.formatted(missionId, requirementId));
        for (int index = 0; index < params.length; index += 2) {
            request = request.param(params[index], params[index + 1]);
        }
        return json(mockMvc.perform(asUser(request, email)).andExpect(status().isOk()).andReturn());
    }

    private static List<String> crewIdsIn(JsonNode candidates) {
        return candidates.findValuesAsText("crewMemberId");
    }

    // --------------------------------------------------------------- ranking

    @Test
    @DisplayName("The worked example ranks Ada, Chen, Bruno and excludes Dana")
    void reproducesTheWorkedExample() throws Exception {
        String mission = createMission(MISSION_LEAD_A, "Worked Example");
        String requirement = workedExampleRequirement(mission, 2);

        JsonNode response = matchOne(MISSION_LEAD_A, mission, requirement, "limit", "10");
        JsonNode candidates = response.get("candidates");

        assertThat(crewIdsIn(candidates)).containsExactly(ADA, CHEN, BRUNO);
        assertThat(candidates.get(0).get("score").asDouble()).isEqualTo(1.0);
        assertThat(candidates.get(1).get("score").asDouble()).isEqualTo(0.75);
        assertThat(candidates.get(2).get("score").asDouble()).isEqualTo(0.5);
        assertThat(crewIdsIn(candidates)).doesNotContain(DANA);
    }

    @Test
    @DisplayName("A candidate exactly at a mandatory minimum outranks an over-qualified one")
    void exactFitBeatsOverQualified() throws Exception {
        String mission = createMission(MISSION_LEAD_A, "Exact Fit");
        String requirement = workedExampleRequirement(mission, 1);

        JsonNode candidates = matchOne(MISSION_LEAD_A, mission, requirement, "limit", "10")
                .get("candidates");

        assertThat(crewIdsIn(candidates).indexOf(ADA))
                .isLessThan(crewIdsIn(candidates).indexOf(BRUNO));
    }

    @Test
    @DisplayName("The breakdown names every required skill, what was held, and the terms")
    void explainsItself() throws Exception {
        String mission = createMission(MISSION_LEAD_A, "Explain Yourself");
        String requirement = workedExampleRequirement(mission, 1);

        JsonNode ada = matchOne(MISSION_LEAD_A, mission, requirement, "limit", "10")
                .get("candidates").get(0);

        assertThat(ada.get("fullName").asText()).isEqualTo("Ada Kowalski");
        assertThat(ada.get("breakdown").get("skillScore").asDouble()).isEqualTo(1.0);
        // Nothing is assigned until feature 07, so both secondary terms are zero. That is the
        // correct answer rather than a stub - there are no assignments to count.
        assertThat(ada.get("breakdown").get("experienceBonus").asDouble()).isZero();
        assertThat(ada.get("breakdown").get("completedMissions").asInt()).isZero();
        assertThat(ada.get("breakdown").get("loadPenalty").asDouble()).isZero();
        assertThat(ada.get("breakdown").get("recentAssignments").asInt()).isZero();
        assertThat(ada.get("skills").findValuesAsText("skillName"))
                .containsExactlyInAnyOrder("EVA Operations", "Robotics");
        assertThat(ada.get("shortfalls")).isEmpty();
    }

    @Test
    @DisplayName("A candidate short on a preferred skill reports it as a shortfall")
    void reportsShortfalls() throws Exception {
        String mission = createMission(MISSION_LEAD_A, "Shortfalls");
        String requirement = workedExampleRequirement(mission, 1);

        JsonNode chen = matchOne(MISSION_LEAD_A, mission, requirement, "limit", "10")
                .get("candidates").get(1);

        assertThat(chen.get("fullName").asText()).isEqualTo("Chen Ibarra");
        assertThat(chen.get("shortfalls").findValuesAsText("skillName")).containsExactly("Robotics");
    }

    @Test
    @DisplayName("A requirement with no skills returns every crew member at a skill score of 1.0")
    void noSkillsMatchesEveryone() throws Exception {
        String mission = createMission(MISSION_LEAD_A, "Anybody Will Do");
        String requirement = addRequirement(MISSION_LEAD_A, mission, """
                {"title": "General Crew", "requiredCount": 1, "skills": []}
                """);

        JsonNode response = matchOne(MISSION_LEAD_A, mission, requirement, "limit", "10");

        // Eight crew members are seeded for Orbital Dynamics, and none of them can be excluded by
        // a requirement that asks for nothing.
        assertThat(response.get("candidates").size() + response.get("remainingCount").asInt())
                .isEqualTo(8);
        assertThat(response.get("candidates").get(0).get("breakdown").get("skillScore").asDouble())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("Nobody eligible is a 200 with an empty list, not a 404")
    void noCandidatesIsNotAnError() throws Exception {
        String mission = createMission(MISSION_LEAD_A, "Impossible Standards");
        // Nobody in the seeded roster holds Life Support at 5.
        String requirement = addRequirement(MISSION_LEAD_A, mission, """
                {"title": "Chief Engineer", "requiredCount": 1,
                 "skills": [{"skillId": "%s", "minimumProficiency": 5, "mandatory": true,
                             "weight": 1}]}
                """.formatted(LIFE_SUPPORT_SKILL_A));

        mockMvc.perform(asUser(get(MATCH_ONE.formatted(mission, requirement)), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates").isEmpty())
                .andExpect(jsonPath("$.remainingCount").value(0));
    }

    @Test
    @DisplayName("Two calls on unchanged data return an identical ordering")
    void isDeterministic() throws Exception {
        String mission = createMission(MISSION_LEAD_A, "Twice The Same");
        String requirement = workedExampleRequirement(mission, 2);

        JsonNode first = matchOne(MISSION_LEAD_A, mission, requirement, "limit", "10");
        JsonNode second = matchOne(MISSION_LEAD_A, mission, requirement, "limit", "10");

        assertThat(first).isEqualTo(second);
    }

    // ------------------------------------------------------------- limit and exclude

    @Test
    @DisplayName("The default limit is three")
    void defaultsToThreeCandidates() throws Exception {
        String mission = createMission(MISSION_LEAD_A, "Default Limit");
        String requirement = addRequirement(MISSION_LEAD_A, mission, """
                {"title": "General Crew", "requiredCount": 1, "skills": []}
                """);

        mockMvc.perform(asUser(get(MATCH_ONE.formatted(mission, requirement)), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates.length()").value(3))
                // Eight seeded crew, three shown, so five are still unseen.
                .andExpect(jsonPath("$.remainingCount").value(5));
    }

    @Test
    @DisplayName("Excluding the first batch returns the next one - a rematch")
    void rematchReturnsTheNextBatch() throws Exception {
        String mission = createMission(MISSION_LEAD_A, "Rematch");
        String requirement = workedExampleRequirement(mission, 1);

        JsonNode first = matchOne(MISSION_LEAD_A, mission, requirement, "limit", "1");
        assertThat(crewIdsIn(first.get("candidates"))).containsExactly(ADA);

        JsonNode second = matchOne(MISSION_LEAD_A, mission, requirement,
                "limit", "1", "exclude", ADA);
        assertThat(crewIdsIn(second.get("candidates"))).containsExactly(CHEN);

        JsonNode third = matchOne(MISSION_LEAD_A, mission, requirement,
                "limit", "1", "exclude", ADA, "exclude", CHEN);
        assertThat(crewIdsIn(third.get("candidates"))).containsExactly(BRUNO);
        assertThat(third.get("remainingCount").asInt()).isZero();
    }

    @Test
    @DisplayName("Exclusions are applied before the limit, so a rematch still returns a full list")
    void exclusionsApplyBeforeTheLimit() throws Exception {
        String mission = createMission(MISSION_LEAD_A, "Full Page");
        String requirement = workedExampleRequirement(mission, 1);

        JsonNode response = matchOne(MISSION_LEAD_A, mission, requirement,
                "limit", "2", "exclude", ADA);

        assertThat(crewIdsIn(response.get("candidates"))).containsExactly(CHEN, BRUNO);
    }

    @Test
    @DisplayName("An unknown or another organisation's id in exclude is ignored, not rejected")
    void unknownExclusionsAreIgnored() throws Exception {
        String mission = createMission(MISSION_LEAD_A, "Stale Draft");
        String requirement = workedExampleRequirement(mission, 1);

        JsonNode response = matchOne(MISSION_LEAD_A, mission, requirement,
                "limit", "10",
                "exclude", UUID.randomUUID().toString(),
                "exclude", "b3000000-0000-0000-0000-000000000001");

        assertThat(crewIdsIn(response.get("candidates"))).containsExactly(ADA, CHEN, BRUNO);
    }

    @Test
    @DisplayName("A limit outside one to ten is a 400")
    void rejectsAnOutOfRangeLimit() throws Exception {
        String mission = createMission(MISSION_LEAD_A, "Bad Limit");
        String requirement = workedExampleRequirement(mission, 1);

        mockMvc.perform(asUser(get(MATCH_ONE.formatted(mission, requirement))
                        .param("limit", "11"), MISSION_LEAD_A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:mission-control:validation-failed"));
    }

    // ---------------------------------------------------------------- match all

    @Test
    @DisplayName("Match all drafts a candidate for every open seat on the mission")
    void matchAllFillsEverySeat() throws Exception {
        String mission = createMission(MISSION_LEAD_A, "Draft Everything");
        workedExampleRequirement(mission, 2);
        addRequirement(MISSION_LEAD_A, mission, """
                {"title": "Systems Engineer", "requiredCount": 1,
                 "skills": [{"skillId": "%s", "minimumProficiency": 3, "mandatory": true,
                             "weight": 1}]}
                """.formatted(LIFE_SUPPORT_SKILL_A));

        JsonNode response = json(mockMvc.perform(
                        asUser(get(MATCH_ALL.formatted(mission)), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(response.get("requirements").size()).isEqualTo(2);
        for (JsonNode requirement : response.get("requirements")) {
            assertThat(requirement.get("candidates").size())
                    .isEqualTo(requirement.get("openSeats").asInt());
        }
    }

    @Test
    @DisplayName("No crew member is drafted twice across a mission's requirements")
    void matchAllNeverRepeatsACrewMember() throws Exception {
        String mission = createMission(MISSION_LEAD_A, "No Duplicates");
        // Two lines asking for the same thing, so every candidate is contested.
        workedExampleRequirement(mission, 2);
        workedExampleRequirement(mission, 2);

        JsonNode response = json(mockMvc.perform(
                        asUser(get(MATCH_ALL.formatted(mission)), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(crewIdsIn(response.get("requirements"))).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("A requirement whose seats are all taken is listed with an empty candidate list")
    void matchAllListsFullyStaffedRequirements() throws Exception {
        String mission = createMission(MISSION_LEAD_A, "Nothing To Do");
        workedExampleRequirement(mission, 1);

        JsonNode response = json(mockMvc.perform(
                        asUser(get(MATCH_ALL.formatted(mission)), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andReturn());

        // Every requirement is present whether or not it needs anybody, so one response renders
        // the whole mission.
        assertThat(response.get("requirements").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("Match all on a mission with no requirements is a 200 with an empty list")
    void matchAllOnAnEmptyMission() throws Exception {
        String mission = createMission(MISSION_LEAD_A, "Nothing Planned");

        mockMvc.perform(asUser(get(MATCH_ALL.formatted(mission)), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirements").isEmpty());
    }

    @Test
    @DisplayName("Two match all calls on unchanged data return an identical draft")
    void matchAllIsDeterministic() throws Exception {
        String mission = createMission(MISSION_LEAD_A, "Same Draft Twice");
        workedExampleRequirement(mission, 2);
        workedExampleRequirement(mission, 1);

        JsonNode first = json(mockMvc.perform(
                asUser(get(MATCH_ALL.formatted(mission)), MISSION_LEAD_A)).andReturn());
        JsonNode second = json(mockMvc.perform(
                asUser(get(MATCH_ALL.formatted(mission)), MISSION_LEAD_A)).andReturn());

        assertThat(first).isEqualTo(second);
    }

    // ------------------------------------------------------------- access and tenancy

    @Test
    @DisplayName("A director can match on any mission in their organisation")
    void aDirectorCanMatch() throws Exception {
        String mission = createMission(MISSION_LEAD_A, "Director Reads");
        String requirement = workedExampleRequirement(mission, 1);

        mockMvc.perform(asUser(get(MATCH_ONE.formatted(mission, requirement)), DIRECTOR_A))
                .andExpect(status().isOk());
        mockMvc.perform(asUser(get(MATCH_ALL.formatted(mission)), DIRECTOR_A))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A mission lead who does not own the mission receives 403")
    void anotherLeadIsForbidden() throws Exception {
        String mission = createMission(MISSION_LEAD_A, "Not Yours");
        String requirement = workedExampleRequirement(mission, 1);

        mockMvc.perform(asUser(get(MATCH_ONE.formatted(mission, requirement)), MISSION_LEAD_A2))
                .andExpect(status().isNotFound());
        mockMvc.perform(asUser(get(MATCH_ALL.formatted(mission)), MISSION_LEAD_A2))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A crew member cannot run matching, even on a mission they can see")
    void aCrewMemberIsForbidden() throws Exception {
        // A crew member has no visibility of a mission they hold no assignment on, and there are no
        // assignments until feature 07, so this is a 404 rather than a 403 today. Either way they
        // cannot reach it, and the 404 is the stricter of the two.
        String mission = createMission(MISSION_LEAD_A, "Crew Cannot Match");
        String requirement = workedExampleRequirement(mission, 1);

        mockMvc.perform(asUser(get(MATCH_ONE.formatted(mission, requirement)),
                        NEVER_LOGGED_OUT_CREW))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:mission-control:not-found"));
    }

    @Test
    @DisplayName("Another organisation's mission is reported as absent, never as forbidden")
    void anotherTenantsMissionIsNotFound() throws Exception {
        mockMvc.perform(asUser(get(MATCH_ALL.formatted(SEEDED_MISSION_B)), MISSION_LEAD_A))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:mission-control:not-found"));
    }

    @Test
    @DisplayName("Crew from another organisation never appear as candidates")
    void neverSuggestsAnotherTenantsCrew() throws Exception {
        String mission = createMission(MISSION_LEAD_B, "Helios Staffing");
        String requirement = addRequirement(MISSION_LEAD_B, mission, """
                {"title": "EVA Specialist", "requiredCount": 2,
                 "skills": [{"skillId": "b2000000-0000-0000-0000-000000000001",
                             "minimumProficiency": 3, "mandatory": true, "weight": 1}]}
                """);

        JsonNode candidates = matchOne(MISSION_LEAD_B, mission, requirement, "limit", "10")
                .get("candidates");

        assertThat(crewIdsIn(candidates)).isNotEmpty();
        assertThat(crewIdsIn(candidates)).allMatch(id -> id.startsWith("b3000000"));
    }

    @Test
    @DisplayName("A requirement belonging to a different mission is reported as absent")
    void requirementFromAnotherMissionIsNotFound() throws Exception {
        String mission = createMission(MISSION_LEAD_A, "Mismatched Ids");
        String other = createMission(MISSION_LEAD_A, "Owner Of The Requirement");
        String requirement = workedExampleRequirement(other, 1);

        mockMvc.perform(asUser(get(MATCH_ONE.formatted(mission, requirement)), MISSION_LEAD_A))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:mission-control:not-found"))
                .andExpect(jsonPath("$.detail")
                        .value("No such crew requirement on this mission."));
    }

    @Test
    @DisplayName("An unknown requirement id is reported as absent")
    void unknownRequirementIsNotFound() throws Exception {
        String mission = createMission(MISSION_LEAD_A, "No Such Requirement");

        mockMvc.perform(asUser(get(MATCH_ONE.formatted(mission, UUID.randomUUID())),
                        MISSION_LEAD_A))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Matching is available while a mission is still in PLAN")
    void worksBeforeApproval() throws Exception {
        // Seeded and left in PLAN. Sizing a plan before submitting it is the point.
        mockMvc.perform(asUser(get(MATCH_ALL.formatted(SEEDED_MISSION_A)), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirements.length()").value(2));
    }

    @Test
    @DisplayName("An unauthenticated caller is refused")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(MATCH_ALL.formatted(SEEDED_MISSION_A)))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ read-only

    @Test
    @DisplayName("Matching writes nothing - the mission is byte for byte unchanged afterwards")
    void writesNothing() throws Exception {
        String mission = createMission(MISSION_LEAD_A, "Untouched");
        String requirement = workedExampleRequirement(mission, 2);

        JsonNode before = json(mockMvc.perform(
                asUser(get("/api/missions/" + mission), MISSION_LEAD_A)).andReturn());

        mockMvc.perform(asUser(get(MATCH_ALL.formatted(mission)), MISSION_LEAD_A))
                .andExpect(status().isOk());
        mockMvc.perform(asUser(get(MATCH_ONE.formatted(mission, requirement)), MISSION_LEAD_A))
                .andExpect(status().isOk());

        JsonNode after = json(mockMvc.perform(
                asUser(get("/api/missions/" + mission), MISSION_LEAD_A)).andReturn());

        // updatedAt is in this payload, so an incidental write would show up here even if it
        // changed nothing a person would notice.
        assertThat(after).isEqualTo(before);
    }

    // ---------------------------------------------------------------- open seats

    @Test
    @DisplayName("Before feature 07 every seat reads as open, because nothing can be offered yet")
    void everySeatIsOpenBeforeAssignments() throws Exception {
        String mission = createMission(MISSION_LEAD_A, "Nothing Offered");
        String requirement = workedExampleRequirement(mission, 2);

        mockMvc.perform(asUser(get(MATCH_ONE.formatted(mission, requirement)), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredCount").value(2))
                .andExpect(jsonPath("$.acceptedCount").value(0))
                .andExpect(jsonPath("$.offeredCount").value(0))
                .andExpect(jsonPath("$.openSeats").value(2));
    }
}
