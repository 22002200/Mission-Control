package com.missioncontrol.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Feature 07 end to end, over HTTP, against a real database.
 *
 * <p><strong>Not {@code Transactional}, and every fixture is built through the API.</strong> Both
 * matter, for the reasons {@code MissionApprovalIT} sets out: these tests are about what survives a
 * commit, and every integration test shares one database, so mutating a seeded row would break a
 * test in another file for reasons invisible from either.
 *
 * <p>The crew are reserved to this class - see the roster note on
 * {@code AbstractIntegrationTest}. Availability is organisation-wide under invariant A3, so
 * accepting a place on somebody's behalf changes what every other test can do with them; that is a
 * stronger reason to split the roster here than the token-revocation one the base class describes.
 */
class CrewAssignmentIT extends AbstractIntegrationTest {

    /** A seeded, active skill in organisation A - requirements have to name a real one. */
    private static final String EVA_SKILL = "a2000000-0000-0000-0000-000000000001";

    /** The window every mission here uses unless it is deliberately made to clash. */
    private static final String STARTS = "2027-05-01T08:00:00Z";
    private static final String ENDS = "2027-05-20T17:00:00Z";

    @Autowired private JdbcTemplate jdbc;

    private final List<String> created = new ArrayList<>();

    @AfterEach
    void removeWhatThisTestCreated() {
        // Assignments carry no foreign key to the mission - no constraint may cross a module
        // boundary - so unlike mission_approval they do not cascade and have to go first.
        created.forEach(id -> {
            jdbc.update("DELETE FROM assignment WHERE mission_id = ?::uuid", id);
            jdbc.update("DELETE FROM mission WHERE id = ?::uuid", id);
        });
        created.clear();
    }

    @Test
    @DisplayName("A lead offers a place, the crew member sees it and accepts - FR-1, FR-3, FR-4")
    void offerAndAccept() throws Exception {
        String mission = givenApprovedMission("Offer and accept", 2);
        String requirement = requirementOf(mission);

        String assignment = json(offer(mission, requirement, ASSIGNMENT_CREW_A_ID, MISSION_LEAD_A)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OFFERED"))
                .andExpect(jsonPath("$.respondedAt").doesNotExist())
                .andReturn())
                .get("id").asText();

        // FR-3: the offer is on the crew member's own list, with the mission named rather than
        // left as an id they would have to look up.
        JsonNode mine = mine(ASSIGNMENT_CREW_A);
        assertThat(mine.get("content")).hasSize(1);
        assertThat(mine.get("content").get(0).get("status").asText()).isEqualTo("OFFERED");
        assertThat(mine.get("content").get(0).get("mission").get("name").asText())
                .isEqualTo("Offer and accept");
        assertThat(mine.get("content").get(0).get("requirementTitle").asText())
                .isEqualTo("Flight Engineer");

        mockMvc.perform(as(post("/api/assignments/{id}/accept", assignment), ASSIGNMENT_CREW_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.respondedAt").exists());

        // The requirement's accepted count rises, which is what invariant M11 measures.
        assertThat(acceptedCount(mission, requirement)).isEqualTo(1);
    }

    @Test
    @DisplayName("Offering is refused on any mission that is not APPROVED - BR-1")
    void onlyApprovedMissionsTakeOffers() throws Exception {
        String planning = givenMission("Still planning");
        addRequirement(planning);

        offer(planning, requirementOf(planning), ASSIGNMENT_CREW_A_ID, MISSION_LEAD_A)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:invalid-transition"))
                .andExpect(jsonPath("$.currentStatus").value("PLAN"));

        mockMvc.perform(as(post("/api/missions/{id}/submit", planning), MISSION_LEAD_A))
                .andExpect(status().isOk());

        offer(planning, requirementOf(planning), ASSIGNMENT_CREW_A_ID, MISSION_LEAD_A)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("Offering beyond requiredCount is refused - BR-2")
    void cannotOverfillARequirement() throws Exception {
        String mission = givenApprovedMission("One seat only", 1);
        String requirement = requirementOf(mission);

        offer(mission, requirement, ASSIGNMENT_CREW_A_ID, MISSION_LEAD_A)
                .andExpect(status().isCreated());

        // The seat is taken by an outstanding offer, not an acceptance. An offer reserves the seat
        // even though A4 says it reserves nobody's calendar.
        offer(mission, requirement, ASSIGNMENT_CREW_B_ID, MISSION_LEAD_A)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:requirement-full"))
                .andExpect(jsonPath("$.offeredCount").value(1));
    }

    @Test
    @DisplayName("Offering the same crew member twice on one mission is refused - BR-5")
    void cannotOfferTheSamePersonTwice() throws Exception {
        String mission = givenApprovedMission("Once each", 3);
        String requirement = requirementOf(mission);

        offer(mission, requirement, ASSIGNMENT_CREW_A_ID, MISSION_LEAD_A)
                .andExpect(status().isCreated());

        offer(mission, requirement, ASSIGNMENT_CREW_A_ID, MISSION_LEAD_A)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:duplicate-assignment"));
    }

    @Test
    @DisplayName("Offering a crew member from another organisation is 404, never 403 - BR-10")
    void cannotOfferAnotherTenantsCrew() throws Exception {
        String mission = givenApprovedMission("Our crew only", 1);

        // 404 rather than 403 so this cannot be used to discover that another tenant employs a
        // given id - the same rule that makes a cross-tenant mission indistinguishable from one
        // that was never created.
        offer(mission, requirementOf(mission), OTHER_ORG_CREW_ID, MISSION_LEAD_A)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:mission-control:not-found"));
    }

    @Test
    @DisplayName("A director may read the crew but not offer or withdraw - BR-9")
    void directorsReadOnly() throws Exception {
        String mission = givenApprovedMission("Directors watch", 1);
        String requirement = requirementOf(mission);

        offer(mission, requirement, OTHER_ORG_CREW_ID, DIRECTOR_A)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:mission-control:forbidden"));

        String assignment = offeredTo(mission, requirement, ASSIGNMENT_CREW_A_ID);

        // Reading is fine and is the point of their oversight; acting on one person's place is not.
        mockMvc.perform(as(get("/api/missions/{id}/assignments", mission), DIRECTOR_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirements[0].assignments[0].crewMember.fullName")
                        .value("Dana Osei"));

        mockMvc.perform(as(post("/api/assignments/{id}/withdraw", assignment), DIRECTOR_A))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:mission-control:forbidden"));
    }

    @Test
    @DisplayName("Only the named crew member can answer an offer - BR-6")
    void onlyTheNamedCrewMemberAnswers() throws Exception {
        String mission = givenApprovedMission("Answer for yourself", 1);
        String assignment = offeredTo(mission, requirementOf(mission), ASSIGNMENT_CREW_A_ID);

        mockMvc.perform(as(post("/api/assignments/{id}/accept", assignment), ASSIGNMENT_CREW_B))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:mission-control:forbidden"));

        // A lead accepting on somebody's behalf could crew a whole mission without anybody having
        // agreed to fly it, so the role check refuses them before the row is even read.
        mockMvc.perform(as(post("/api/assignments/{id}/accept", assignment), MISSION_LEAD_A))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/assignments/{id}/decline", assignment), DIRECTOR_A))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Two leads may offer the same crew member clashing dates; the second acceptance fails")
    void offersDoNotReserveButAcceptancesDo() throws Exception {
        String first = givenApprovedMission("Clash one", 1);
        String second = givenApprovedMissionOwnedBy("Clash two", OTHER_MISSION_LEAD_A);

        // BR-4: neither offer is wrong, and neither is refused. An offer reserves the seat, not
        // the person.
        String offerOne = offeredTo(first, requirementOf(first), ASSIGNMENT_CREW_C_ID);
        // Read as its own lead: the second mission belongs to Priya, and Marcus cannot see it at
        // all - which is the visibility rule this feature depends on rather than works around.
        String offerTwo = json(offer(second, requirementOf(second, OTHER_MISSION_LEAD_A),
                        ASSIGNMENT_CREW_C_ID, OTHER_MISSION_LEAD_A)
                .andExpect(status().isCreated())
                .andReturn())
                .get("id").asText();

        mockMvc.perform(as(post("/api/assignments/{id}/accept", offerOne), ASSIGNMENT_CREW_C))
                .andExpect(status().isOk());

        // BR-3: the clash surfaces here, at the second acceptance, and names what is in the way.
        mockMvc.perform(as(post("/api/assignments/{id}/accept", offerTwo), ASSIGNMENT_CREW_C))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:schedule-conflict"))
                .andExpect(jsonPath("$.conflictingMissionName").value("Clash one"))
                .andExpect(jsonPath("$.conflictingStartsAt").value(STARTS));
    }

    @Test
    @DisplayName("Missions that do not overlap can both be accepted")
    void nonOverlappingMissionsAreFine() throws Exception {
        String spring = givenApprovedMission("Spring window", 1);
        String autumn = givenApprovedMission(
                "Autumn window", 1, "2027-09-01T08:00:00Z", "2027-09-20T17:00:00Z");

        accept(offeredTo(spring, requirementOf(spring), ASSIGNMENT_CREW_D_ID), ASSIGNMENT_CREW_D);
        accept(offeredTo(autumn, requirementOf(autumn), ASSIGNMENT_CREW_D_ID), ASSIGNMENT_CREW_D);

        assertThat(mine(ASSIGNMENT_CREW_D).get("content")).hasSize(2);
    }

    @Test
    @DisplayName("An overlapping place on a CLOSED mission does not block - A8")
    void aClosedMissionFreesItsCrew() throws Exception {
        String aborted = givenApprovedMission("Stood down", 1);
        accept(offeredTo(aborted, requirementOf(aborted), ASSIGNMENT_CREW_A_ID), ASSIGNMENT_CREW_A);

        close(aborted, "{\"comment\":\"Launch window missed.\"}");

        // Aborting a mission has to free its crew immediately, otherwise a cancelled flight would
        // keep somebody booked for dates on which nothing will now happen.
        String replacement = givenApprovedMission("Same dates, different mission", 1);
        accept(offeredTo(replacement, requirementOf(replacement), ASSIGNMENT_CREW_A_ID),
                ASSIGNMENT_CREW_A);
    }

    @Test
    @DisplayName("Declining frees the place, and cannot be undone - FR-5, FR-7, BR-7")
    void decliningFreesThePlace() throws Exception {
        String mission = givenApprovedMission("Somebody else then", 1);
        String requirement = requirementOf(mission);
        String declined = offeredTo(mission, requirement, ASSIGNMENT_CREW_A_ID);

        mockMvc.perform(as(post("/api/assignments/{id}/decline", declined), ASSIGNMENT_CREW_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));

        // FR-7: the seat is open again, so the lead can go to their second choice.
        String reoffered = offeredTo(mission, requirement, ASSIGNMENT_CREW_B_ID);
        accept(reoffered, ASSIGNMENT_CREW_B);

        mockMvc.perform(as(post("/api/assignments/{id}/accept", declined), ASSIGNMENT_CREW_A))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:invalid-transition"))
                .andExpect(jsonPath("$.currentStatus").value("DECLINED"));
    }

    @Test
    @DisplayName("A crew member cannot decline a place they have accepted - once accepted, assigned")
    void acceptingIsNotReversibleByTheCrewMember() throws Exception {
        String mission = givenApprovedMission("Committed", 1);
        String assignment = offeredTo(mission, requirementOf(mission), ASSIGNMENT_CREW_A_ID);
        accept(assignment, ASSIGNMENT_CREW_A);

        // A7 allows ACCEPTED to WITHDRAWN and nothing else, and BR-9 puts that verb in the owning
        // lead's hands. Being let off a mission is a decision, not a self-service action.
        mockMvc.perform(as(post("/api/assignments/{id}/decline", assignment), ASSIGNMENT_CREW_A))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("ACCEPTED"));
    }

    @Test
    @DisplayName("The owning lead can withdraw an acceptance, and the accepted count falls - FR-6")
    void leadWithdrawsAnAcceptance() throws Exception {
        String mission = givenApprovedMission("Reconsidered", 2);
        String requirement = requirementOf(mission);
        String assignment = offeredTo(mission, requirement, ASSIGNMENT_CREW_A_ID);
        accept(assignment, ASSIGNMENT_CREW_A);
        assertThat(acceptedCount(mission, requirement)).isEqualTo(1);

        mockMvc.perform(as(post("/api/assignments/{id}/withdraw", assignment), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.respondedAt").exists());

        assertThat(acceptedCount(mission, requirement)).isZero();
    }

    @Test
    @DisplayName("A fully crewed mission can start, and withdrawing does not send it back - M11, BR-11")
    void startingAndThenLosingCrew() throws Exception {
        String mission = givenApprovedMission("Ready to fly", 1);
        String requirement = requirementOf(mission);

        // Before feature 07 this always failed, and correctly so: nothing could report a mission
        // as crewed. It is the first time POST /start can succeed in a running application.
        mockMvc.perform(as(post("/api/missions/{id}/start", mission), MISSION_LEAD_A))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:mission-understaffed"));

        String assignment = offeredTo(mission, requirement, ASSIGNMENT_CREW_A_ID);
        accept(assignment, ASSIGNMENT_CREW_A);

        mockMvc.perform(as(post("/api/missions/{id}/start", mission), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.fullyStaffed").value(true));

        mockMvc.perform(as(post("/api/assignments/{id}/withdraw", assignment), MISSION_LEAD_A))
                .andExpect(status().isOk());

        // BR-11: M11 is a precondition of starting, not a standing invariant. A mission does not
        // fall out of the sky because somebody left it.
        mockMvc.perform(as(get("/api/missions/{id}", mission), MISSION_LEAD_A))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.fullyStaffed").value(false));
    }

    @Test
    @DisplayName("Closing withdraws outstanding offers and leaves acceptances alone - FR-8, BR-8")
    void closingSweepsOffersButNotAcceptances() throws Exception {
        String mission = givenApprovedMission("Closed with crew aboard", 3);
        String requirement = requirementOf(mission);

        String accepted = offeredTo(mission, requirement, ASSIGNMENT_CREW_A_ID);
        accept(accepted, ASSIGNMENT_CREW_A);
        String outstanding = offeredTo(mission, requirement, ASSIGNMENT_CREW_B_ID);

        close(mission, "{\"comment\":\"Programme cancelled.\"}");

        assertThat(statusOf(outstanding)).isEqualTo("WITHDRAWN");
        // Withdrawing the acceptance too would erase the crew member's history, which is derived
        // from exactly these rows.
        assertThat(statusOf(accepted)).isEqualTo("ACCEPTED");
        assertThat(respondedAtOf(outstanding)).isNotNull();
    }

    @Test
    @DisplayName("A completed mission's acceptances become the crew member's history")
    void completedMissionsBecomeHistory() throws Exception {
        String mission = givenApprovedMission("Flown and finished", 1);
        String assignment = offeredTo(mission, requirementOf(mission), ASSIGNMENT_CREW_A_ID);
        accept(assignment, ASSIGNMENT_CREW_A);

        mockMvc.perform(as(post("/api/missions/{id}/start", mission), MISSION_LEAD_A))
                .andExpect(status().isOk());
        close(mission, "{\"closeReason\":\"COMPLETED\"}");

        // History is derived rather than stored: the row is still ACCEPTED and the mission is
        // closed as COMPLETED, and those two facts together are the whole definition.
        assertThat(statusOf(assignment)).isEqualTo("ACCEPTED");

        // The crew member still sees it, now against a CLOSED mission. Not filtered by timeframe:
        // these fixtures fly in 2027, so by the calendar the mission is still UPCOMING even though
        // it has been flown and closed. That is the distinction the timeframe filter is measuring
        // and TimeframeTest pins down - status and dates are different questions.
        JsonNode mine = mine(ASSIGNMENT_CREW_A);
        assertThat(mine.get("content")).isNotEmpty();
        assertThat(mine.get("content").get(0).get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(mine.get("content").get(0).get("mission").get("status").asText())
                .isEqualTo("CLOSED");

        // And the matching engine can now see it, which is the point of the CrewLoadReadModel port
        // - before this module existed every candidate's experience was zero.
        JsonNode candidate = candidateFor(ASSIGNMENT_CREW_A_ID);
        assertThat(candidate).isNotNull();
        assertThat(candidate.get("breakdown").get("completedMissions").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("A crew member can see the mission they were offered, and no other - visibility")
    void offersMakeAMissionVisible() throws Exception {
        String mission = givenApprovedMission("Now you can see it", 1);

        // Before the offer the mission is invisible to them, exactly as another tenant's is.
        mockMvc.perform(as(get("/api/missions/{id}", mission), ASSIGNMENT_CREW_A))
                .andExpect(status().isNotFound());

        offeredTo(mission, requirementOf(mission), ASSIGNMENT_CREW_A_ID);

        // Being asked is reason enough to see what you are being asked to join - without this the
        // offer would be unanswerable from the mission page it appears on.
        mockMvc.perform(as(get("/api/missions/{id}", mission), ASSIGNMENT_CREW_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Now you can see it"));
    }

    @Test
    @DisplayName("The mission view lists every requirement, including the unstaffed ones - FR-2")
    void missionViewKeepsEmptyRequirements() throws Exception {
        // Both lines have to exist before the mission is approved: changing the crew a mission
        // needs is PLAN-only and owner-only, because it would otherwise invalidate the approval.
        String mission = givenMission("Two lines, one filled");
        addRequirement(mission, "Flight Engineer", 1);
        addRequirement(mission, "Science Officer", 2);
        approve(mission, MISSION_LEAD_A);

        offeredTo(mission, requirementOf(mission, MISSION_LEAD_A, "Flight Engineer"),
                ASSIGNMENT_CREW_A_ID);

        mockMvc.perform(as(get("/api/missions/{id}/assignments", mission), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirements.length()").value(2))
                .andExpect(jsonPath(
                        "$.requirements[?(@.title == 'Science Officer')].assignments[0]")
                        .doesNotExist());
    }

    @Test
    @DisplayName("Another tenant sees nothing of any of this - T1, T2")
    void crossTenantIsInvisible() throws Exception {
        String mission = givenApprovedMission("Ours alone", 1);
        String assignment = offeredTo(mission, requirementOf(mission), ASSIGNMENT_CREW_A_ID);

        mockMvc.perform(as(get("/api/missions/{id}/assignments", mission), DIRECTOR_B))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(post("/api/assignments/{id}/withdraw", assignment),
                        "sofia.mendes@heliosaero.example"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("The seeded Helios assignments are a state the application could have produced")
    void seedIsConsistent() throws Exception {
        // Zenith Station Run is seeded ACTIVE, and M11 says a mission cannot start unless every
        // requirement is filled. If the seed did not fill it, the seed would describe a state no
        // sequence of API calls could reach.
        mockMvc.perform(as(get("/api/missions/{id}", "b4000000-0000-0000-0000-000000000004"),
                        DIRECTOR_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.fullyStaffed").value(true));

        // And Ines Varga has an offer waiting, so a crew member logging in for the first time has
        // something to do rather than an empty screen.
        JsonNode mine = mine("ines.varga@heliosaero.example");
        assertThat(mine.get("content")).hasSize(1);
        assertThat(mine.get("content").get(0).get("status").asText()).isEqualTo("OFFERED");
    }

    // --- helpers ------------------------------------------------------------------------------

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String email)
            throws Exception {
        return request.header("Authorization", bearer(tokenFor(email)));
    }

    private String givenMission(String name) throws Exception {
        return givenMission(name, STARTS, ENDS, MISSION_LEAD_A);
    }

    private String givenMission(String name, String startsAt, String endsAt,
                                String lead) throws Exception {
        String body = """
                {
                  "name": "%s",
                  "description": "Created by CrewAssignmentIT.",
                  "startsAt": "%s",
                  "endsAt": "%s"
                }
                """.formatted(name, startsAt, endsAt);

        String id = json(mockMvc.perform(as(post("/api/missions"), lead)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isCreated())
                        .andReturn())
                .get("id").asText();

        created.add(id);
        return id;
    }

    /** A mission in APPROVED with one requirement, which is the only state offers are taken in. */
    private String givenApprovedMission(String name, int seats) throws Exception {
        return givenApprovedMission(name, seats, STARTS, ENDS);
    }

    private String givenApprovedMission(String name, int seats, String startsAt, String endsAt)
            throws Exception {
        return approve(givenStaffedMission(name, seats, startsAt, endsAt, MISSION_LEAD_A),
                MISSION_LEAD_A);
    }

    private String givenApprovedMissionOwnedBy(String name, String lead) throws Exception {
        return approve(givenStaffedMission(name, 1, STARTS, ENDS, lead), lead);
    }

    private String givenStaffedMission(String name, int seats, String startsAt, String endsAt,
                                       String lead) throws Exception {
        String id = givenMission(name, startsAt, endsAt, lead);
        addRequirement(id, "Flight Engineer", seats, lead);
        return id;
    }

    private String approve(String mission, String lead) throws Exception {
        mockMvc.perform(as(post("/api/missions/{id}/submit", mission), lead))
                .andExpect(status().isOk());
        mockMvc.perform(as(post("/api/missions/{id}/approve", mission), DIRECTOR_A))
                .andExpect(status().isOk());
        return mission;
    }

    private void addRequirement(String mission) throws Exception {
        addRequirement(mission, "Flight Engineer", 1, MISSION_LEAD_A);
    }

    private void addRequirement(String mission, String title, int seats) throws Exception {
        addRequirement(mission, title, seats, MISSION_LEAD_A);
    }

    private void addRequirement(String mission, String title, int seats, String lead)
            throws Exception {
        String body = """
                {
                  "title": "%s",
                  "requiredCount": %d,
                  "skills": [{"skillId": "%s", "minimumProficiency": 1, "mandatory": true}]
                }
                """.formatted(title, seats, EVA_SKILL);

        mockMvc.perform(as(post("/api/missions/{id}/requirements", mission), lead)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    /** The first requirement on a mission, which every fixture here creates as Flight Engineer. */
    private String requirementOf(String mission) throws Exception {
        return requirementOf(mission, MISSION_LEAD_A);
    }

    /** The first requirement, read as somebody who can see the mission - requirements sort by title. */
    private String requirementOf(String mission, String lead) throws Exception {
        return json(mockMvc.perform(as(get("/api/missions/{id}", mission), lead))
                .andExpect(status().isOk())
                .andReturn())
                .get("requirements").get(0).get("id").asText();
    }

    /** One named requirement, for a mission that has more than one. */
    private String requirementOf(String mission, String lead, String title) throws Exception {
        JsonNode requirements = json(mockMvc.perform(as(get("/api/missions/{id}", mission), lead))
                .andExpect(status().isOk())
                .andReturn())
                .get("requirements");
        for (JsonNode requirement : requirements) {
            if (requirement.get("title").asText().equals(title)) {
                return requirement.get("id").asText();
            }
        }
        throw new AssertionError("No requirement titled " + title + " on mission " + mission);
    }

    private org.springframework.test.web.servlet.ResultActions offer(
            String mission, String requirement, String crewMemberId, String lead) throws Exception {
        String body = """
                {"crewRequirementId": "%s", "crewMemberId": "%s"}
                """.formatted(requirement, crewMemberId);

        return mockMvc.perform(as(post("/api/missions/{id}/assignments", mission), lead)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String offeredTo(String mission, String requirement, String crewMemberId)
            throws Exception {
        return json(offer(mission, requirement, crewMemberId, MISSION_LEAD_A)
                .andExpect(status().isCreated())
                .andReturn())
                .get("id").asText();
    }

    private void accept(String assignment, String crewEmail) throws Exception {
        mockMvc.perform(as(post("/api/assignments/{id}/accept", assignment), crewEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    private void close(String mission, String body) throws Exception {
        mockMvc.perform(as(post("/api/missions/{id}/close", mission), MISSION_LEAD_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private JsonNode mine(String crewEmail) throws Exception {
        return json(mockMvc.perform(as(get("/api/assignments/me"), crewEmail))
                .andExpect(status().isOk())
                .andReturn());
    }

    /**
     * The accepted count as the mission's own staffing view reports it.
     *
     * <p>Read through the API rather than counted in SQL on purpose: what matters is that the
     * figure {@code mission} publishes moves, and that figure travels through the read model this
     * feature implements. A direct count would pass even if the port were unwired.
     */
    private int acceptedCount(String mission, String requirement) throws Exception {
        JsonNode requirements = json(mockMvc.perform(
                        as(get("/api/missions/{id}/assignments", mission), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andReturn())
                .get("requirements");

        for (JsonNode line : requirements) {
            if (line.get("requirementId").asText().equals(requirement)) {
                return line.get("acceptedCount").asInt();
            }
        }
        throw new AssertionError("No requirement " + requirement + " on mission " + mission);
    }

    /** One candidate out of a match run, so the load read model is exercised through matching. */
    private JsonNode candidateFor(String crewMemberId) throws Exception {
        String mission = givenApprovedMission("Matching probe", 1);
        JsonNode candidates = json(mockMvc.perform(as(get(
                        "/api/missions/{missionId}/requirements/{requirementId}/matches?limit=10",
                        mission, requirementOf(mission)), MISSION_LEAD_A))
                .andExpect(status().isOk())
                .andReturn())
                .get("candidates");

        for (JsonNode candidate : candidates) {
            if (candidate.get("crewMemberId").asText().equals(crewMemberId)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * The stored status, read straight out of the table.
     *
     * <p>SQL rather than the API here, deliberately, and it is the one place this class does that.
     * Closing a mission withdraws its offers through a domain event, and an event that never fired
     * would leave the row untouched - so the assertion has to look at the row rather than at a
     * response the closing call built before the listener ran.
     *
     * <p>The codes are spelled out rather than mapped through the enum, which is internal to the
     * module and rightly invisible from here. They are pinned and append-only, so writing them
     * down is safe in a way that reordering them would not be.
     */
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

    private Object respondedAtOf(String assignment) {
        return jdbc.queryForObject(
                "SELECT responded_at FROM assignment WHERE id = ?::uuid", Object.class, assignment);
    }
}
