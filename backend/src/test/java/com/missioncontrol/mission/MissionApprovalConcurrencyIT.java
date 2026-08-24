package com.missioncontrol.mission;

import static org.assertj.core.api.Assertions.assertThat;
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
 * NFR-2: two directors deciding the same mission at the same moment, and only one of them winning.
 *
 * <p><strong>Deliberately not {@code Transactional}.</strong> A test-managed transaction binds one
 * connection to the test thread, so neither worker would ever commit and the race this exists to
 * provoke could not happen. That is also why the cancellation cases live in {@code
 * MissionLifecycleIT} and this one is on its own.
 *
 * <p>Both workers dispatch through {@code MockMvc}, which runs the real {@code DispatcherServlet}:
 * each dispatch gets its own request context, its own security context, its own transaction and its
 * own connection. The {@code CyclicBarrier} is what makes the two arrive together rather than merely
 * near each other, and the timeout on {@code Future.get} means a deadlock fails the build in
 * seconds instead of hanging it.
 *
 * <p>The design under test is a pessimistic row lock, not a retry, so the outcome is deterministic
 * and one iteration would be enough. It repeats anyway: if someone removes the lock, a single run
 * could still pass on luck, and a concurrency test that passes by luck is worse than none.
 */
class MissionApprovalConcurrencyIT extends AbstractIntegrationTest {

    private static final String EVA_SKILL = "a2000000-0000-0000-0000-000000000001";
    private static final int TIMEOUT_SECONDS = 15;

    @Autowired private JdbcTemplate jdbc;

    private final List<String> created = new ArrayList<>();

    @AfterEach
    void removeWhatThisTestCreated() {
        created.forEach(id -> jdbc.update("DELETE FROM mission WHERE id = ?::uuid", id));
        created.clear();
    }

    @RepeatedTest(3)
    @DisplayName("Two concurrent approvals produce exactly one success and one 409")
    void concurrentApprovals() throws Exception {
        String mission = givenSubmittedMission();
        String director = bearer(tokenFor(DIRECTOR_A));

        List<Integer> statuses = raceTwo(() -> mockMvc.perform(
                        post("/api/missions/{id}/approve", mission)
                                .header("Authorization", director)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"comment\":\"Cleared.\"}"))
                .andReturn());

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);

        // The assertion that earns this test. A naive implementation gives two 200s and, worse, two
        // decision rows on one cycle - or one cycle settled twice. Exactly one settled cycle with
        // exactly one decider is what the lock is for.
        assertThat(cycleCount(mission)).isEqualTo(1);
        assertThat(decidedCycleCount(mission)).isEqualTo(1);
        assertThat(statusOf(mission)).isEqualTo("APPROVED");
    }

    @RepeatedTest(3)
    @DisplayName("Approve racing reject settles the cycle once, and the mission agrees with it")
    void approveRacingReject() throws Exception {
        String mission = givenSubmittedMission();
        String director = bearer(tokenFor(DIRECTOR_A));

        List<Integer> statuses = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier gate = new CyclicBarrier(2);
        try {
            Future<Integer> approving = pool.submit(() -> {
                gate.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/missions/{id}/approve", mission)
                                .header("Authorization", director))
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> rejecting = pool.submit(() -> {
                gate.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/missions/{id}/reject", mission)
                                .header("Authorization", director)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"comment\":\"Not this cycle.\"}"))
                        .andReturn().getResponse().getStatus();
            });
            statuses.add(approving.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            statuses.add(rejecting.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(decidedCycleCount(mission)).isEqualTo(1);

        // Whichever won, the ledger and the mission must tell the same story - that is NFR-1, and
        // it is the part an unlocked close would break by overwriting a decision it read stale.
        String recorded = jdbc.queryForObject(
                "SELECT decision FROM mission_approval WHERE mission_id = ?::uuid",
                String.class, mission);
        String expected = "2".equals(recorded) ? "APPROVED" : "REJECTED";
        assertThat(statusOf(mission)).isEqualTo(expected);
    }

    @RepeatedTest(3)
    @DisplayName("Two concurrent submissions cannot both open a cycle - M8 under load")
    void concurrentSubmissions() throws Exception {
        String mission = givenSubmittableMission();
        String lead = bearer(tokenFor(MISSION_LEAD_A));

        List<Integer> statuses = raceTwo(() -> mockMvc.perform(
                        post("/api/missions/{id}/submit", mission)
                                .header("Authorization", lead))
                .andReturn());

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(pendingCycleCount(mission)).isEqualTo(1);
        assertThat(statusOf(mission)).isEqualTo("PENDING_APPROVAL");
    }

    // --- helpers ------------------------------------------------------------------------------

    /** Runs the same call twice at once, released together, and returns both response statuses. */
    private List<Integer> raceTwo(Call call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier gate = new CyclicBarrier(2);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    gate.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    return call.perform().getResponse().getStatus();
                }));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private interface Call {
        MvcResult perform() throws Exception;
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String email)
            throws Exception {
        return request.header("Authorization", bearer(tokenFor(email)));
    }

    private String givenSubmittableMission() throws Exception {
        String body = """
                {
                  "name": "Concurrency fixture",
                  "startsAt": "2027-06-01T08:00:00Z",
                  "endsAt": "2027-06-20T17:00:00Z"
                }
                """;
        JsonNode mission = json(mockMvc.perform(as(post("/api/missions"), MISSION_LEAD_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn());

        String id = mission.get("id").asText();
        created.add(id);

        mockMvc.perform(as(post("/api/missions/{id}/requirements", id), MISSION_LEAD_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Flight Engineer",
                                  "requiredCount": 1,
                                  "skills": [{"skillId": "%s", "minimumProficiency": 3,
                                              "mandatory": true}]
                                }
                                """.formatted(EVA_SKILL)))
                .andExpect(status().isCreated());

        return id;
    }

    private String givenSubmittedMission() throws Exception {
        String id = givenSubmittableMission();
        // Arranged and committed on the test thread before the barrier is released, so both workers
        // genuinely start from PENDING_APPROVAL.
        mockMvc.perform(as(post("/api/missions/{id}/submit", id), MISSION_LEAD_A))
                .andExpect(status().isOk());
        return id;
    }

    private String statusOf(String mission) {
        Short code = jdbc.queryForObject("SELECT status FROM mission WHERE id = ?::uuid",
                Short.class, mission);
        return switch (code == null ? -1 : code) {
            case 1 -> "PLAN";
            case 2 -> "PENDING_APPROVAL";
            case 3 -> "APPROVED";
            case 4 -> "REJECTED";
            case 5 -> "ACTIVE";
            case 6 -> "CLOSED";
            default -> "UNKNOWN";
        };
    }

    private int cycleCount(String mission) {
        return count("SELECT count(*) FROM mission_approval WHERE mission_id = ?::uuid", mission);
    }

    private int decidedCycleCount(String mission) {
        return count("SELECT count(*) FROM mission_approval WHERE mission_id = ?::uuid "
                + "AND decision <> 1", mission);
    }

    private int pendingCycleCount(String mission) {
        return count("SELECT count(*) FROM mission_approval WHERE mission_id = ?::uuid "
                + "AND decision = 1", mission);
    }

    private int count(String sql, String mission) {
        Integer count = jdbc.queryForObject(sql, Integer.class, mission);
        return count == null ? 0 : count;
    }
}
