package com.missioncontrol.mission.internal;

import com.missioncontrol.mission.api.MissionStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.missioncontrol.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transitions that begin somewhere past {@code PLAN}.
 *
 * <p>Lives in {@code internal} and writes through the repository. Feature 05 made
 * {@code PENDING_APPROVAL} and {@code APPROVED} reachable over HTTP, and
 * {@code MissionApprovalIT} arranges them that way; what is left here is the states that still have
 * no route through the API - {@code ACTIVE}, which needs crew nobody can accept until feature 07 -
 * and the transitions out of a state where going through the API would test the arrangement rather
 * than the rule. M5 and M11 are the substance.
 *
 * <p>{@code Transactional}, so each fixture rolls back. Every other integration test counts the
 * seeded rows, and missions left behind here would make those counts drift.
 */
@Transactional
class MissionLifecycleIT extends AbstractIntegrationTest {

    private static final UUID ORG_A = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID MARCUS = UUID.fromString("a1000000-0000-0000-0000-000000000002");
    private static final UUID EVA_SKILL = UUID.fromString("a2000000-0000-0000-0000-000000000001");

    private static final Instant STARTS = Instant.parse("2027-05-01T08:00:00Z");
    private static final Instant ENDS = Instant.parse("2027-05-20T17:00:00Z");

    @Autowired private MissionRepository missions;

    private MockHttpServletRequestBuilder asLead(MockHttpServletRequestBuilder request)
            throws Exception {
        return request.header("Authorization", bearer(tokenFor(MISSION_LEAD_A)));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** A mission put straight into the given state, as feature 05 eventually will. */
    private MissionEntity given(MissionStatus status) {
        Instant now = Instant.parse("2026-06-01T10:00:00Z");
        return missions.saveAndFlush(MissionEntity.builder()
                .id(UUID.randomUUID())
                .organisationId(ORG_A)
                .name("Lifecycle fixture")
                .status(status)
                .missionLeadId(MARCUS)
                .startsAt(STARTS)
                .endsAt(ENDS)
                .createdBy(MARCUS)
                .createdAt(now)
                .updatedAt(now)
                .requirements(new LinkedHashSet<>())
                .build());
    }

    private MissionEntity givenApprovedNeeding(int crew) {
        MissionEntity mission = given(MissionStatus.APPROVED);
        CrewRequirementEntity requirement = CrewRequirementEntity.builder()
                .id(UUID.randomUUID())
                .organisationId(ORG_A)
                .title("Flight Engineer")
                .requiredCount(crew)
                .requiredSkills(new LinkedHashSet<>())
                .build();
        requirement.replaceWith("Flight Engineer", null, crew, java.util.List.of(
                new RequiredSkillValues(EVA_SKILL, (short) 3, true, 1)));
        mission.addRequirement(requirement, Instant.parse("2026-06-01T10:00:00Z"));
        return missions.saveAndFlush(mission);
    }

    @Test
    @DisplayName("Editing an APPROVED mission returns it to PLAN - M5")
    void editingAnApprovedMissionRevertsItToPlan() throws Exception {
        MissionEntity mission = given(MissionStatus.APPROVED);

        mockMvc.perform(asLead(json(patch("/api/missions/{id}", mission.getId()), """
                        {"name": "Renamed after approval"}
                        """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLAN"))
                .andExpect(jsonPath("$.name").value("Renamed after approval"));
    }

    @Test
    @DisplayName("Editing an ACTIVE mission returns it to PLAN - M5")
    void editingAnActiveMissionRevertsItToPlan() throws Exception {
        MissionEntity mission = given(MissionStatus.ACTIVE);

        mockMvc.perform(asLead(json(patch("/api/missions/{id}", mission.getId()), """
                        {"description": "Amended mid-flight."}
                        """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLAN"));
    }

    @Test
    @DisplayName("Changing only the dates also reverts an approved mission")
    void anyEditRevertsNotJustARename() throws Exception {
        MissionEntity mission = given(MissionStatus.APPROVED);

        mockMvc.perform(asLead(json(patch("/api/missions/{id}", mission.getId()), """
                        {"endsAt": "2027-06-30T17:00:00Z"}
                        """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLAN"));
    }

    @Test
    @DisplayName("Requirements cannot be added once the mission has left PLAN - BR-10")
    void requirementsAreFrozenOutsidePlan() throws Exception {
        MissionEntity mission = given(MissionStatus.PENDING_APPROVAL);

        mockMvc.perform(asLead(json(post("/api/missions/{id}/requirements", mission.getId()), """
                        {"title": "Engineer", "requiredCount": 1}
                        """)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:mission-not-editable"))
                .andExpect(jsonPath("$.currentStatus").value("PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("Starting an under-staffed mission is refused, and the response says which line")
    void startingAnUnderstaffedMissionNamesTheShortfall() throws Exception {
        MissionEntity mission = givenApprovedNeeding(2);

        mockMvc.perform(asLead(post("/api/missions/{id}/start", mission.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:mission-understaffed"))
                .andExpect(jsonPath("$.requirements[0].title").value("Flight Engineer"))
                .andExpect(jsonPath("$.requirements[0].requiredCount").value(2))
                .andExpect(jsonPath("$.requirements[0].acceptedCount").value(0));
    }

    @Test
    @DisplayName("An APPROVED mission with no requirements cannot start either")
    void anEmptyApprovedMissionCannotStart() throws Exception {
        MissionEntity mission = given(MissionStatus.APPROVED);

        mockMvc.perform(asLead(post("/api/missions/{id}/start", mission.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:mission-control:mission-understaffed"))
                .andExpect(jsonPath("$.requirements").isEmpty());
    }

    @Test
    @DisplayName("Closing from ACTIVE without a reason records COMPLETED - BR-11")
    void closingAnActiveMissionRecordsCompleted() throws Exception {
        MissionEntity mission = given(MissionStatus.ACTIVE);

        mockMvc.perform(asLead(json(post("/api/missions/{id}/close", mission.getId()), "{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closeReason").value("COMPLETED"));
    }

    @Test
    @DisplayName("A rejected mission may be closed as REJECTED - the path feature 05 will use")
    void aRejectedMissionClosesAsRejected() throws Exception {
        MissionEntity mission = given(MissionStatus.REJECTED);

        mockMvc.perform(asLead(json(post("/api/missions/{id}/close", mission.getId()), """
                        {"closeReason": "REJECTED", "comment": "Not viable this cycle."}
                        """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closeReason").value("REJECTED"))
                .andExpect(jsonPath("$.closeComment").value("Not viable this cycle."));
    }

    @Test
    @DisplayName("A mission can be closed from PLAN, PENDING_APPROVAL, APPROVED and ACTIVE")
    void everyNonTerminalStatusCanBeClosed() throws Exception {
        for (MissionStatus status : new MissionStatus[]{
                MissionStatus.PLAN, MissionStatus.PENDING_APPROVAL,
                MissionStatus.APPROVED, MissionStatus.ACTIVE}) {

            MissionEntity mission = given(status);

            mockMvc.perform(asLead(json(post("/api/missions/{id}/close", mission.getId()), "{}")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CLOSED"));
        }
    }

    @Test
    @DisplayName("The detail read stays one query for the mission however many requirements it has")
    void readingAMissionWithItsRequirementsIsBounded() throws Exception {
        MissionEntity mission = givenApprovedNeeding(2);

        mockMvc.perform(asLead(get("/api/missions/{id}", mission.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirements[0].skills[0].skillName")
                        .value("EVA Operations"));

        // The fetch join is what makes that one query rather than one per requirement. If it were
        // ever dropped this still passes, so the guarantee is stated where it can be seen.
        assertThat(missions.findDetailByIdAndOrganisationId(mission.getId(), ORG_A))
                .get()
                .satisfies(loaded -> assertThat(loaded.getRequirements()).hasSize(1));
    }
}
