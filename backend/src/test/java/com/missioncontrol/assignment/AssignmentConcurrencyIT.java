package com.missioncontrol.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.missioncontrol.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * NFR-2 and NFR-3: two acceptances arriving at the same instant, and only one of them winning.
 *
 * <p>Two races, and they are genuinely different. In the first, two crew members go for the last
 * place on one requirement; the mission row is what serialises them. In the second, one crew member
 * accepts two overlapping missions at once; the mission row cannot help, because those are two
 * different rows, so the crew member's own open assignments are locked as well. A design that took
 * only the first lock would pass the first test and fail the second, which is exactly why both are
 * here.
 *
 * <p><strong>Deliberately not {@code Transactional}.</strong> A test-managed transaction binds one
 * connection to the test thread, so neither worker would ever commit and the race this exists to
 * provoke could not happen - the same reasoning {@code MissionApprovalConcurrencyIT} records.
 *
 * <p>Both workers dispatch through {@code MockMvc}, which runs the real {@code DispatcherServlet}:
 * each dispatch gets its own request context, its own security context, its own transaction and its
 * own connection. The {@code CyclicBarrier} is what makes the two arrive together rather than
 * merely near each other, and the timeout on {@code Future.get} means a deadlock fails the build in
 * seconds instead of hanging it.
 *
 * <p>The design under test is a pessimistic lock rather than a retry, so the outcome is
 * deterministic and one iteration would be enough. It repeats anyway: if someone removes a lock, a
 * single run could still pass on luck, and a concurrency test that passes by luck is worse than
 * none.
 */
class AssignmentConcurrencyIT extends AbstractIntegrationTest {

    private static final String EVA_SKILL = "a2000000-0000-0000-0000-000000000001";
    private static final int TIMEOUT_SECONDS = 15;

    /**
     * Crew reserved to this class alone, and the reservation matters twice over.
     *
     * <p>Not the ones {@code CrewAssignmentIT} uses: availability is organisation-wide, so if that
     * class left somebody holding an accepted mission over these dates, a race here would be
     * decided by the leftover rather than by the lock - and it would fail intermittently, which is
     * the worst way for a concurrency test to be wrong.
     *
     * <p>And not {@code CREW_A} either, which {@code LogoutRevocationIT} logs out. This harness
     * mints both tokens before releasing the barrier, so a token revoked between minting and
     * dispatch would turn a race into a pair of 401s. {@code NEVER_LOGGED_OUT_CREW} exists for
     * exactly this and Hugo Delacroix is the one account no other test touches.
     */
    private static final String RACER_ONE = NEVER_LOGGED_OUT_CREW;
    private static final String RACER_ONE_ID = "a3000000-0000-0000-0000-000000000002";
    private static final String RACER_TWO = "hugo.delacroix@orbitaldynamics.example";
    private static final String RACER_TWO_ID = "a3000000-0000-0000-0000-000000000008";

    @Autowired private JdbcTemplate jdbc;

    private final List<String> created = new ArrayList<>();

    @AfterEach
    void removeWhatThisTestCreated() {
        created.forEach(id -> {
            jdbc.update("DELETE FROM assignment WHERE mission_id = ?::uuid", id);
            jdbc.update("DELETE FROM mission WHERE id = ?::uuid", id);
        });
        created.clear();
    }

    @RepeatedTest(4)
    @DisplayName("Two crew members accepting the last place: one 200, one 409 - NFR-2")
    void twoCrewMembersForOneSeat() throws Exception {
        // Two seats to begin with, because invariant A2 caps offered plus accepted at
        // requiredCount - a single seat cannot legitimately carry two outstanding offers, so the
        // state this race needs cannot be reached one offer at a time.
        String mission = givenApprovedMission("Last seat race", 2, START, END);
        String requirement = requirementOf(mission);

        String first = offerTo(mission, requirement, RACER_ONE_ID);
        String second = offerTo(mission, requirement, RACER_TWO_ID);
        shrinkToOneSeat(requirement);

        List<Integer> statuses = raceOf(
                accept(first, RACER_ONE),
                accept(second, RACER_TWO));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(acceptedRows(requirement)).isEqualTo(1);
    }

    @RepeatedTest(4)
    @DisplayName("One crew member accepting two overlapping missions: one 200, one 409 - NFR-3")
    void oneCrewMemberTwoClashingMissions() throws Exception {
        String first = givenApprovedMission("Clash A", 1, START, END);
        String second = givenApprovedMission("Clash B", 1, START, END);

        String offerOne = offerTo(first, requirementOf(first), RACER_ONE_ID);
        String offerTwo = offerTo(second, requirementOf(second), RACER_ONE_ID);

        // The mission lock is no help here - these are two different missions and two different
        // rows. What serialises them is the lock on this crew member's own open assignments, taken
        // second and in a fixed order so the two transactions queue rather than deadlock.
        List<Integer> statuses = raceOf(
                accept(offerOne, RACER_ONE),
                accept(offerTwo, RACER_ONE));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(acceptedRowsFor(RACER_ONE_ID)).isEqualTo(1);
    }

    @RepeatedTest(4)
    @DisplayName("Closing a mission while somebody accepts it leaves one coherent outcome")
    void closeRacingAnAcceptance() throws Exception {
        String mission = givenApprovedMission("Closed mid-acceptance", 1, START, END);
        String assignment = offerTo(mission, requirementOf(mission), RACER_TWO_ID);

        // Both take the mission row lock first, which is what makes this safe and what stops the
        // two deadlocking. Either the acceptance lands and the close leaves it alone - BR-8 keeps
        // acceptances - or the close wins and the offer is withdrawn before it can be answered.
        List<Integer> statuses = raceOf(
                accept(assignment, RACER_TWO),
                closeOf(mission));

        assertThat(statuses).contains(200);
        String finalStatus = statusOf(assignment);
        assertThat(finalStatus).isIn("ACCEPTED", "WITHDRAWN");
        // Whatever happened, the row was settled exactly once and carries the instant to prove it.
        assertThat(respondedAt(assignment)).isNotNull();
    }

    // --- the race harness ----------------------------------------------------------------------

    private static final String START = "2027-06-01T08:00:00Z";
    private static final String END = "2027-06-20T17:00:00Z";

    /** Runs two requests through the barrier and returns their statuses. */
    private List<Integer> raceOf(MockHttpServletRequestBuilder left,
                                 MockHttpServletRequestBuilder right) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> one = pool.submit(() -> dispatch(barrier, left));
            Future<Integer> two = pool.submit(() -> dispatch(barrier, right));
            return List.of(
                    one.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    two.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private int dispatch(CyclicBarrier barrier, MockHttpServletRequestBuilder request)
            throws Exception {
        barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        MvcResult result = mockMvc.perform(request).andReturn();
        return result.getResponse().getStatus();
    }

    private MockHttpServletRequestBuilder accept(String assignment, String crewEmail)
            throws Exception {
        return as(post("/api/assignments/{id}/accept", assignment), crewEmail);
    }

    private MockHttpServletRequestBuilder closeOf(String mission) throws Exception {
        return as(post("/api/missions/{id}/close", mission), MISSION_LEAD_A)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"Stood down.\"}");
    }

    // --- fixtures ------------------------------------------------------------------------------

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String email)
            throws Exception {
        return request.header("Authorization", bearer(tokenFor(email)));
    }

    private String givenApprovedMission(String name, int seats, String startsAt, String endsAt)
            throws Exception {
        String body = """
                {
                  "name": "%s",
                  "description": "Created by AssignmentConcurrencyIT.",
                  "startsAt": "%s",
                  "endsAt": "%s"
                }
                """.formatted(name, startsAt, endsAt);

        String id = json(mockMvc.perform(as(post("/api/missions"), MISSION_LEAD_A)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isCreated())
                        .andReturn())
                .get("id").asText();
        created.add(id);

        mockMvc.perform(as(post("/api/missions/{id}/requirements", id), MISSION_LEAD_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Flight Engineer",
                                  "requiredCount": %d,
                                  "skills": [{"skillId": "%s", "minimumProficiency": 1,
                                              "mandatory": true}]
                                }
                                """.formatted(seats, EVA_SKILL)))
                .andExpect(status().isCreated());

        mockMvc.perform(as(post("/api/missions/{id}/submit", id), MISSION_LEAD_A))
                .andExpect(status().isOk());
        mockMvc.perform(as(post("/api/missions/{id}/approve", id), DIRECTOR_A))
                .andExpect(status().isOk());
        return id;
    }

    private String requirementOf(String mission) throws Exception {
        return json(mockMvc.perform(as(get("/api/missions/{id}", mission), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andReturn())
                .get("requirements").get(0).get("id").asText();
    }

    private String offerTo(String mission, String requirement, String crewMemberId)
            throws Exception {
        JsonNode created = json(mockMvc.perform(
                        as(post("/api/missions/{id}/assignments", mission), MISSION_LEAD_A)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"crewRequirementId": "%s", "crewMemberId": "%s"}
                                        """.formatted(requirement, crewMemberId)))
                .andExpect(status().isCreated())
                .andReturn());
        return created.get("id").asText();
    }

    /**
     * Narrows the requirement to one seat after both offers are in.
     *
     * <p>Done in SQL rather than through the API on purpose, and it is the only honest way to
     * arrange this race. Invariant A2 forbids two outstanding offers against a single seat, so the
     * API cannot produce the state where two people are both entitled to accept the same last
     * place - which is precisely the state NFR-2 is about. Editing the requirement through the API
     * would also drop the mission back to PLAN under M5 and make the offers unanswerable.
     */
    private void shrinkToOneSeat(String requirement) {
        jdbc.update("UPDATE crew_requirement SET required_count = 1 WHERE id = ?::uuid",
                requirement);
    }

    private int acceptedRows(String requirement) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM assignment WHERE crew_requirement_id = ?::uuid AND status = 2",
                Integer.class, requirement);
        return count == null ? 0 : count;
    }

    private int acceptedRowsFor(String crewMemberId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM assignment WHERE crew_member_id = ?::uuid AND status = 2",
                Integer.class, crewMemberId);
        return count == null ? 0 : count;
    }

    private String statusOf(String assignment) {
        Integer code = jdbc.queryForObject(
                "SELECT status FROM assignment WHERE id = ?::uuid", Integer.class, assignment);
        return switch (code == null ? 0 : code) {
            case 1 -> "OFFERED";
            case 2 -> "ACCEPTED";
            case 3 -> "DECLINED";
            case 4 -> "WITHDRAWN";
            default -> "UNKNOWN(" + code + ")";
        };
    }

    private Object respondedAt(String assignment) {
        return jdbc.queryForObject(
                "SELECT responded_at FROM assignment WHERE id = ?::uuid", Object.class, assignment);
    }
}
