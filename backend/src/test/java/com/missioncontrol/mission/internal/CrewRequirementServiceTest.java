package com.missioncontrol.mission.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.missioncontrol.identity.api.UserDirectory;
import com.missioncontrol.mission.api.StaffingReadModel;
import com.missioncontrol.platform.CurrentUser;
import com.missioncontrol.shared.UserRole;
import com.missioncontrol.skill.api.SkillCatalogue;
import com.missioncontrol.skill.api.SkillSummary;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

/** The rules around describing the crew a mission needs: BR-7, BR-8 and BR-10. */
@ExtendWith(MockitoExtension.class)
class CrewRequirementServiceTest {

    private static final UUID ORG = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID LEAD = UUID.fromString("a1000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_LEAD = UUID.fromString("a1000000-0000-0000-0000-000000000003");
    private static final UUID DIRECTOR = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID MISSION = UUID.fromString("a4000000-0000-0000-0000-000000000001");
    private static final UUID EVA = UUID.fromString("a2000000-0000-0000-0000-000000000001");
    private static final UUID ROBOTICS = UUID.fromString("a2000000-0000-0000-0000-000000000002");
    private static final UUID RETIRED = UUID.fromString("a2000000-0000-0000-0000-000000000009");

    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

    @Mock private MissionRepository missions;
    @Mock private SkillCatalogue skills;
    @Mock private UserDirectory users;
    @Mock private CurrentUser currentUser;
    @Mock private ObjectProvider<StaffingReadModel> staffingProvider;

    private CrewRequirementService service;

    @BeforeEach
    void setUp() {
        lenient().when(staffingProvider.getIfAvailable(any())).thenReturn(new UnstaffedReadModel());
        lenient().when(currentUser.organisationId()).thenReturn(ORG);
        lenient().when(currentUser.userId()).thenReturn(LEAD);
        lenient().when(currentUser.role()).thenReturn(UserRole.MISSION_LEAD);
        lenient().when(missions.save(any())).thenAnswer(call -> call.getArgument(0));
        // The loader locks the bare row before reading the detail, so the two finders have to
        // agree. Delegating rather than stubbing twice means a test that arranges a mission gets
        // one that can be commanded as well as read.
        lenient().when(missions.lockByIdAndOrganisationId(any(), any())).thenAnswer(call ->
                missions.findDetailByIdAndOrganisationId(call.getArgument(0), call.getArgument(1)));
        lenient().when(skills.findByIds(anyCollection(), any())).thenAnswer(call -> {
            Collection<UUID> asked = call.getArgument(0);
            return asked.stream()
                    .filter(id -> id.equals(EVA) || id.equals(ROBOTICS) || id.equals(RETIRED))
                    .collect(java.util.stream.Collectors.toMap(
                            id -> id,
                            id -> new SkillSummary(id, "Skill " + id, !id.equals(RETIRED))));
        });

        MissionStaffing staffing = new MissionStaffing(staffingProvider);
        MissionAccess access = new MissionAccess(currentUser, staffing);
        service = new CrewRequirementService(
                missions,
                new MissionLoader(missions, access, currentUser),
                access,
                new MissionDetailAssembler(missions, staffing, skills, users, currentUser),
                skills,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("A requirement is added with its skills inline - FR-8")
    void addsARequirementWithItsSkills() {
        givenMission(MissionStatus.PLAN, LEAD);

        CrewRequirementResponse added = service.add(MISSION, new CrewRequirementRequest(
                "  Flight Engineer  ", "Repairs.", 2,
                List.of(new RequiredSkillRequest(EVA, 3, true, 2),
                        new RequiredSkillRequest(ROBOTICS, 2, false, null))));

        assertThat(added.title()).isEqualTo("Flight Engineer");
        assertThat(added.requiredCount()).isEqualTo(2);
        assertThat(added.acceptedCount()).isZero();
        assertThat(added.skills()).hasSize(2);
        assertThat(added.skills()).extracting(RequiredSkillResponse::weight).contains(2, 1);
    }

    @Test
    @DisplayName("An omitted weight defaults to 1 rather than to zero")
    void weightDefaultsToOne() {
        givenMission(MissionStatus.PLAN, LEAD);

        CrewRequirementResponse added = service.add(MISSION, new CrewRequirementRequest(
                "Engineer", null, 1, List.of(new RequiredSkillRequest(EVA, 3, true, null))));

        assertThat(added.skills()).singleElement()
                .extracting(RequiredSkillResponse::weight).isEqualTo(1);
    }

    @Test
    @DisplayName("The same skill twice is rejected before it reaches the primary key - M10")
    void duplicateSkillsAreRejected() {
        givenMission(MissionStatus.PLAN, LEAD);

        assertThatThrownBy(() -> service.add(MISSION, new CrewRequirementRequest(
                "Engineer", null, 1,
                List.of(new RequiredSkillRequest(EVA, 3, true, null),
                        new RequiredSkillRequest(EVA, 4, false, null)))))
                .isInstanceOf(DuplicateSkillException.class);
    }

    @Test
    @DisplayName("An unknown skill is rejected")
    void unknownSkillIsRejected() {
        givenMission(MissionStatus.PLAN, LEAD);

        assertThatThrownBy(() -> service.add(MISSION, new CrewRequirementRequest(
                "Engineer", null, 1,
                List.of(new RequiredSkillRequest(UUID.randomUUID(), 3, true, null)))))
                .isInstanceOf(InvalidSkillException.class);
    }

    @Test
    @DisplayName("A retired skill cannot be chosen, though existing ones still render - S2")
    void retiredSkillIsRejected() {
        givenMission(MissionStatus.PLAN, LEAD);

        assertThatThrownBy(() -> service.add(MISSION, new CrewRequirementRequest(
                "Engineer", null, 1,
                List.of(new RequiredSkillRequest(RETIRED, 3, true, null)))))
                .isInstanceOf(InvalidSkillException.class);
    }

    @ParameterizedTest
    @EnumSource(value = MissionStatus.class,
            names = {"PENDING_APPROVAL", "APPROVED", "REJECTED", "ACTIVE"})
    @DisplayName("Requirements cannot be touched once the mission has left PLAN - BR-10")
    void requirementsAreFrozenOutsidePlan(MissionStatus status) {
        givenMission(status, LEAD);

        assertThatThrownBy(() -> service.add(MISSION,
                new CrewRequirementRequest("Engineer", null, 1, null)))
                .isInstanceOf(MissionNotEditableException.class);
    }

    @Test
    @DisplayName("A director may not add requirements - that is planning work, BR-10")
    void directorsCannotAddRequirements() {
        when(currentUser.role()).thenReturn(UserRole.DIRECTOR);
        when(currentUser.userId()).thenReturn(DIRECTOR);
        givenMission(MissionStatus.PLAN, LEAD);

        assertThatThrownBy(() -> service.add(MISSION,
                new CrewRequirementRequest("Engineer", null, 1, null)))
                .isInstanceOf(MissionForbiddenException.class);
    }

    @Test
    @DisplayName("A non-owning lead is told the mission does not exist, matching what GET says")
    void nonOwningLeadGetsNotFound() {
        givenMission(MissionStatus.PLAN, OTHER_LEAD);

        assertThatThrownBy(() -> service.add(MISSION,
                new CrewRequirementRequest("Engineer", null, 1, null)))
                .isInstanceOf(MissionNotFoundException.class);
    }

    @Test
    @DisplayName("An update replaces the skills wholesale, dropping the ones left out")
    void updateReplacesSkills() {
        MissionEntity mission = givenMission(MissionStatus.PLAN, LEAD);
        CrewRequirementResponse added = service.add(MISSION, new CrewRequirementRequest(
                "Engineer", null, 1,
                List.of(new RequiredSkillRequest(EVA, 3, true, null),
                        new RequiredSkillRequest(ROBOTICS, 2, false, null))));

        CrewRequirementResponse updated = service.update(MISSION, added.id(),
                new CrewRequirementRequest("Engineer", null, 3,
                        List.of(new RequiredSkillRequest(ROBOTICS, 5, true, 4))));

        assertThat(updated.requiredCount()).isEqualTo(3);
        assertThat(updated.skills()).singleElement()
                .extracting(RequiredSkillResponse::skillId).isEqualTo(ROBOTICS);
        assertThat(mission.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("A requirement id from another mission is reported as absent")
    void unknownRequirementIsNotFound() {
        givenMission(MissionStatus.PLAN, LEAD);

        assertThatThrownBy(() -> service.update(MISSION, UUID.randomUUID(),
                new CrewRequirementRequest("Engineer", null, 1, null)))
                .isInstanceOf(RequirementNotFoundException.class);
    }

    @Test
    @DisplayName("Deleting removes it from the mission")
    void deleteRemovesTheRequirement() {
        MissionEntity mission = givenMission(MissionStatus.PLAN, LEAD);
        CrewRequirementResponse added = service.add(MISSION,
                new CrewRequirementRequest("Engineer", null, 1, null));

        service.delete(MISSION, added.id());

        assertThat(mission.getRequirements()).isEmpty();
    }

    private MissionEntity givenMission(MissionStatus status, UUID leadId) {
        MissionEntity mission = MissionEntity.builder()
                .id(MISSION)
                .organisationId(ORG)
                .name("Aurora Survey")
                .status(status)
                .missionLeadId(leadId)
                .startsAt(Instant.parse("2026-09-01T08:00:00Z"))
                .endsAt(Instant.parse("2026-09-14T17:00:00Z"))
                .createdBy(leadId)
                .createdAt(NOW)
                .updatedAt(NOW)
                .requirements(new LinkedHashSet<>())
                .build();

        lenient().when(missions.findDetailByIdAndOrganisationId(MISSION, ORG))
                .thenReturn(Optional.of(mission));
        return mission;
    }

}
