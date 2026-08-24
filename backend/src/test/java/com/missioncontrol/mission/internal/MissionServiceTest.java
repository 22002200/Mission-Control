package com.missioncontrol.mission.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.missioncontrol.identity.api.UserDirectory;
import com.missioncontrol.identity.api.UserSummary;
import com.missioncontrol.mission.api.StaffingReadModel;
import com.missioncontrol.platform.CurrentUser;
import com.missioncontrol.shared.UserRole;
import com.missioncontrol.skill.api.SkillCatalogue;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

/**
 * The business rules, away from HTTP and the database.
 *
 * <p>The staffing read model is a stub rather than the no-op the application ships with, which is
 * what lets this cover the one case the running application cannot reach yet: starting a mission
 * that really is fully crewed. Feature 07 supplies the real implementation and an integration test
 * to go with it.
 */
@ExtendWith(MockitoExtension.class)
class MissionServiceTest {

    private static final UUID ORG = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID LEAD = UUID.fromString("a1000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_LEAD = UUID.fromString("a1000000-0000-0000-0000-000000000003");
    private static final UUID CREW = UUID.fromString("a1000000-0000-0000-0000-000000000004");
    private static final UUID MISSION = UUID.fromString("a4000000-0000-0000-0000-000000000001");
    private static final UUID REQUIREMENT = UUID.fromString("a5000000-0000-0000-0000-000000000001");

    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-09-01T08:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-09-14T17:00:00Z");

    @Mock private MissionRepository missions;
    @Mock private MissionApprovalRepository approvalRepository;
    @Mock private SkillCatalogue skills;
    @Mock private UserDirectory users;
    @Mock private CurrentUser currentUser;
    @Mock private ObjectProvider<StaffingReadModel> staffingProvider;

    private MissionService service;
    private StubStaffing staffing;

    @BeforeEach
    void setUp() {
        staffing = new StubStaffing();
        lenient().when(staffingProvider.getIfAvailable(any())).thenReturn(staffing);
        lenient().when(currentUser.organisationId()).thenReturn(ORG);
        lenient().when(currentUser.userId()).thenReturn(LEAD);
        lenient().when(currentUser.role()).thenReturn(UserRole.MISSION_LEAD);
        lenient().when(users.findByIds(anyCollection(), any()))
                .thenReturn(Map.of(LEAD, new UserSummary(LEAD, "Marcus Reyes")));
        lenient().when(skills.findByIds(anyCollection(), any())).thenReturn(Map.of());
        lenient().when(approvalRepository.findByMissionIdAndOrganisationIdAndDecision(
                any(), any(), any())).thenReturn(Optional.empty());
        // The loader locks the bare row before reading the detail, so the two finders have to
        // agree. Delegating rather than stubbing twice means a test that arranges a mission gets
        // one that can be commanded as well as read.
        lenient().when(missions.lockByIdAndOrganisationId(any(), any())).thenAnswer(call ->
                missions.findDetailByIdAndOrganisationId(call.getArgument(0), call.getArgument(1)));

        MissionStaffing missionStaffing = new MissionStaffing(staffingProvider);
        MissionAccess access = new MissionAccess(currentUser, missionStaffing);
        // Real collaborators rather than mocks: they are the rules under test, and a mocked
        // loader would let a change to the lock-then-fetch order pass unnoticed here.
        service = new MissionService(
                missions,
                new MissionLoader(missions, access, currentUser),
                access,
                missionStaffing,
                new MissionApprovals(approvalRepository),
                new MissionDetailAssembler(missions, missionStaffing, skills, users, currentUser),
                currentUser,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("Creating")
    class Creating {

        @Test
        @DisplayName("A new mission starts in PLAN, owned by the caller - M2")
        void startsInPlanOwnedByTheCaller() {
            when(missions.save(any())).thenAnswer(call -> call.getArgument(0));

            MissionResponse created = service.create(
                    new CreateMissionRequest("Aurora Survey", "  spaced  ", STARTS, ENDS));

            assertThat(created.status()).isEqualTo(MissionStatus.PLAN);
            assertThat(created.missionLead().id()).isEqualTo(LEAD);
            assertThat(created.closeReason()).isNull();
            assertThat(created.description()).isEqualTo("spaced");
            assertThat(created.fullyStaffed()).isFalse();
        }

        @Test
        @DisplayName("endsAt must be strictly after startsAt - M1")
        void rejectsReversedDates() {
            assertThatThrownBy(() -> service.create(
                    new CreateMissionRequest("X", null, ENDS, STARTS)))
                    .isInstanceOf(MissionValidationException.class)
                    .hasMessageContaining("endsAt must be after startsAt");
        }

        @Test
        @DisplayName("A zero-length mission is rejected too - 'after', not 'not before'")
        void rejectsEqualDates() {
            assertThatThrownBy(() -> service.create(
                    new CreateMissionRequest("X", null, STARTS, STARTS)))
                    .isInstanceOf(MissionValidationException.class);
        }
    }

    @Nested
    @DisplayName("Visibility")
    class Visibility {

        @Test
        @DisplayName("A mission lead cannot see another lead's mission, and gets 404 not 403")
        void otherLeadsMissionIsAbsent() {
            givenMission(mission(MissionStatus.PLAN, OTHER_LEAD));

            assertThatThrownBy(() -> service.get(MISSION))
                    .isInstanceOf(MissionNotFoundException.class);
        }

        @Test
        @DisplayName("A director sees every mission in the organisation")
        void directorSeesEverything() {
            when(currentUser.role()).thenReturn(UserRole.DIRECTOR);
            givenMission(mission(MissionStatus.PLAN, OTHER_LEAD));

            // No user id is stubbed on purpose. A director's visibility must not depend on who
            // they are, and strict stubbing turns that into an assertion rather than a comment.
            assertThat(service.get(MISSION).id()).isEqualTo(MISSION);
        }

        @Test
        @DisplayName("A crew member sees only missions they hold an assignment on")
        void crewSeeOnlyTheirOwn() {
            when(currentUser.role()).thenReturn(UserRole.CREW_MEMBER);
            when(currentUser.userId()).thenReturn(CREW);
            givenMission(mission(MissionStatus.PLAN, LEAD));

            assertThatThrownBy(() -> service.get(MISSION))
                    .isInstanceOf(MissionNotFoundException.class);

            staffing.assignedMissions = Set.of(MISSION);
            assertThat(service.get(MISSION).id()).isEqualTo(MISSION);
        }

        @Test
        @DisplayName("A crew member on the crew may read but not edit - this is the 403 case")
        void assignedCrewCannotEdit() {
            when(currentUser.role()).thenReturn(UserRole.CREW_MEMBER);
            when(currentUser.userId()).thenReturn(CREW);
            staffing.assignedMissions = Set.of(MISSION);
            givenMission(mission(MissionStatus.PLAN, LEAD));

            assertThatThrownBy(() -> service.update(
                    MISSION, new UpdateMissionRequest("Nope", null, null, null)))
                    .isInstanceOf(MissionForbiddenException.class);
        }
    }

    @Nested
    @DisplayName("Editing")
    class Editing {

        @ParameterizedTest
        @EnumSource(value = MissionStatus.class, names = {"APPROVED", "ACTIVE"})
        @DisplayName("Editing an approved or active mission returns it to PLAN - M5")
        void editRevertsToPlan(MissionStatus from) {
            givenMission(mission(from, LEAD));

            MissionResponse updated = service.update(
                    MISSION, new UpdateMissionRequest("Renamed", null, null, null));

            assertThat(updated.status()).isEqualTo(MissionStatus.PLAN);
            assertThat(updated.name()).isEqualTo("Renamed");
        }

        @ParameterizedTest
        @EnumSource(value = MissionStatus.class,
                names = {"PLAN", "PENDING_APPROVAL", "REJECTED"})
        @DisplayName("Editing anything earlier leaves the status alone")
        void editKeepsEarlierStatuses(MissionStatus from) {
            givenMission(mission(from, LEAD));

            assertThat(service.update(MISSION, new UpdateMissionRequest("Renamed", null, null, null))
                    .status()).isEqualTo(from);
        }

        @Test
        @DisplayName("A closed mission rejects every edit - M3")
        void closedMissionIsTerminal() {
            givenMission(closedMission());

            assertThatThrownBy(() -> service.update(
                    MISSION, new UpdateMissionRequest("Nope", null, null, null)))
                    .isInstanceOf(InvalidMissionTransitionException.class);
        }

        @Test
        @DisplayName("Omitted fields are left alone; a blank description clears it")
        void partialUpdateSemantics() {
            MissionEntity existing = mission(MissionStatus.PLAN, LEAD);
            givenMission(existing);

            MissionResponse updated = service.update(
                    MISSION, new UpdateMissionRequest(null, "  ", null, null));

            assertThat(updated.name()).isEqualTo("Aurora Survey");
            assertThat(updated.description()).isNull();
            assertThat(updated.startsAt()).isEqualTo(STARTS);
        }

        @Test
        @DisplayName("A new endsAt is checked against the stored startsAt, not only a supplied one")
        void datesAreCheckedAgainstWhatIsStored() {
            givenMission(mission(MissionStatus.PLAN, LEAD));

            assertThatThrownBy(() -> service.update(MISSION,
                    new UpdateMissionRequest(null, null, null, STARTS.minusSeconds(1))))
                    .isInstanceOf(MissionValidationException.class);
        }

        @Test
        @DisplayName("A non-owning lead cannot edit, and is told the mission does not exist")
        void nonOwningLeadCannotEdit() {
            givenMission(mission(MissionStatus.PLAN, OTHER_LEAD));

            assertThatThrownBy(() -> service.update(
                    MISSION, new UpdateMissionRequest("Nope", null, null, null)))
                    .isInstanceOf(MissionNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Starting")
    class Starting {

        @ParameterizedTest
        @EnumSource(value = MissionStatus.class,
                names = {"PLAN", "PENDING_APPROVAL", "REJECTED", "ACTIVE", "CLOSED"})
        @DisplayName("Only an APPROVED mission may start - M3")
        void onlyApprovedMayStart(MissionStatus from) {
            givenMission(from == MissionStatus.CLOSED ? closedMission() : mission(from, LEAD));

            assertThatThrownBy(() -> service.start(MISSION))
                    .isInstanceOf(InvalidMissionTransitionException.class);
        }

        @Test
        @DisplayName("An under-staffed mission is refused, and the error names what is short")
        void understaffedMissionCannotStart() {
            givenMission(approvedWithRequirement(2));

            assertThatThrownBy(() -> service.start(MISSION))
                    .isInstanceOf(MissionUnderstaffedException.class)
                    .hasMessageContaining("One crew requirement");
        }

        @Test
        @DisplayName("A fully staffed APPROVED mission starts")
        void fullyStaffedMissionStarts() {
            givenMission(approvedWithRequirement(2));
            staffing.acceptedCounts = Map.of(REQUIREMENT, 2);

            MissionResponse started = service.start(MISSION);

            assertThat(started.status()).isEqualTo(MissionStatus.ACTIVE);
            assertThat(started.fullyStaffed()).isTrue();
        }

        @Test
        @DisplayName("A mission with no requirements cannot start, and says why")
        void emptyMissionCannotStart() {
            givenMission(mission(MissionStatus.APPROVED, LEAD));

            // There is no shortfall to list, so a literal reading of M11 is vacuously satisfied
            // and an empty mission would launch with nobody aboard. M12 closes this at submission
            // in feature 05; refusing it here as well keeps `fullyStaffed` and `start` agreeing.
            assertThatThrownBy(() -> service.start(MISSION))
                    .isInstanceOf(MissionUnderstaffedException.class)
                    .hasMessageContaining("nobody to fly it");
        }
    }

    @Nested
    @DisplayName("Closing")
    class Closing {

        @Test
        @DisplayName("Closing an ACTIVE mission without a reason records COMPLETED - BR-11")
        void activeClosesAsCompleted() {
            givenMission(mission(MissionStatus.ACTIVE, LEAD));

            assertThat(service.close(MISSION, new CloseMissionRequest(null, null)).closeReason())
                    .isEqualTo(MissionCloseReason.COMPLETED);
        }

        @ParameterizedTest
        @EnumSource(value = MissionStatus.class,
                names = {"PLAN", "PENDING_APPROVAL", "APPROVED", "REJECTED"})
        @DisplayName("Closing anything else without a reason records ABORTED - BR-11")
        void everythingElseClosesAsAborted(MissionStatus from) {
            givenMission(mission(from, LEAD));

            assertThat(service.close(MISSION, new CloseMissionRequest(null, null)).closeReason())
                    .isEqualTo(MissionCloseReason.ABORTED);
        }

        @Test
        @DisplayName("An explicit reason wins over the default")
        void explicitReasonWins() {
            givenMission(mission(MissionStatus.ACTIVE, LEAD));

            assertThat(service.close(MISSION,
                    new CloseMissionRequest(MissionCloseReason.ABORTED, "stood down"))
                    .closeReason()).isEqualTo(MissionCloseReason.ABORTED);
        }

        @Test
        @DisplayName("REJECTED is only accepted for a mission that really was rejected")
        void rejectedReasonNeedsARejectedMission() {
            givenMission(mission(MissionStatus.PLAN, LEAD));

            assertThatThrownBy(() -> service.close(MISSION,
                    new CloseMissionRequest(MissionCloseReason.REJECTED, null)))
                    .isInstanceOf(MissionValidationException.class);
        }

        @Test
        @DisplayName("A rejected mission may be closed as REJECTED - the path feature 05 uses")
        void rejectedMissionMayCloseAsRejected() {
            givenMission(mission(MissionStatus.REJECTED, LEAD));

            assertThat(service.close(MISSION,
                    new CloseMissionRequest(MissionCloseReason.REJECTED, "not viable"))
                    .closeReason()).isEqualTo(MissionCloseReason.REJECTED);
        }

        @Test
        @DisplayName("An already-closed mission cannot be closed again")
        void closedMissionCannotCloseAgain() {
            givenMission(closedMission());

            assertThatThrownBy(() -> service.close(MISSION, new CloseMissionRequest(null, null)))
                    .isInstanceOf(InvalidMissionTransitionException.class);
        }

        @Test
        @DisplayName("Closing a mission awaiting a decision cancels its open cycle - feature 05")
        void cancelsAnOpenApprovalCycle() {
            MissionEntity mission = mission(MissionStatus.PENDING_APPROVAL, LEAD);
            givenMission(mission);
            MissionApprovalEntity open = MissionApprovalEntity.builder()
                    .id(UUID.randomUUID())
                    .organisationId(ORG)
                    .missionId(MISSION)
                    .submittedBy(LEAD)
                    .submittedAt(NOW)
                    .decision(ApprovalDecision.PENDING)
                    .build();
            when(approvalRepository.findByMissionIdAndOrganisationIdAndDecision(
                    MISSION, ORG, ApprovalDecision.PENDING)).thenReturn(Optional.of(open));

            service.close(MISSION, new CloseMissionRequest(null, "Launch window missed."));

            // Left PENDING it would read on screen as still waiting for someone, and it would hold
            // the partial unique index behind M8 against a resubmission that can never come.
            assertThat(open.getDecision()).isEqualTo(ApprovalDecision.CANCELLED);
            assertThat(open.getDecidedBy()).isEqualTo(LEAD);
            assertThat(open.getComment()).isEqualTo("Launch window missed.");
        }

        @Test
        @DisplayName("Closing a mission with nothing open is not an error")
        void closingWithNoOpenCycleIsFine() {
            givenMission(mission(MissionStatus.PLAN, LEAD));

            assertThat(service.close(MISSION, new CloseMissionRequest(null, null)).status())
                    .isEqualTo(MissionStatus.CLOSED);
        }
    }

    private void givenMission(MissionEntity mission) {
        when(missions.findDetailByIdAndOrganisationId(MISSION, ORG)).thenReturn(Optional.of(mission));
    }

    private static MissionEntity mission(MissionStatus status, UUID leadId) {
        return MissionEntity.builder()
                .id(MISSION)
                .organisationId(ORG)
                .name("Aurora Survey")
                .description("Mapping auroral activity.")
                .status(status)
                .missionLeadId(leadId)
                .startsAt(STARTS)
                .endsAt(ENDS)
                .createdBy(leadId)
                .createdAt(NOW)
                .updatedAt(NOW)
                .requirements(new LinkedHashSet<>())
                .build();
    }

    private static MissionEntity closedMission() {
        MissionEntity mission = mission(MissionStatus.PLAN, LEAD);
        mission.close(MissionCloseReason.ABORTED, null, NOW);
        return mission;
    }

    private static MissionEntity approvedWithRequirement(int requiredCount) {
        MissionEntity mission = mission(MissionStatus.APPROVED, LEAD);
        CrewRequirementEntity requirement = CrewRequirementEntity.builder()
                .id(REQUIREMENT)
                .organisationId(ORG)
                .title("Flight Engineer")
                .requiredCount(requiredCount)
                .requiredSkills(new LinkedHashSet<>())
                .build();
        mission.addRequirement(requirement, NOW);
        return mission;
    }

    /**
     * A staffing read model whose answers the test controls.
     *
     * <p>A hand-written stub rather than a Mockito mock because two of its methods are called on
     * almost every path here, and stubbing both in every test would bury the rule each one is
     * about.
     */
    private static final class StubStaffing implements StaffingReadModel {

        private Map<UUID, Integer> acceptedCounts = Map.of();
        private Set<UUID> assignedMissions = Set.of();

        @Override
        public Map<UUID, Integer> acceptedCountsByRequirement(java.util.Collection<UUID> ids) {
            return acceptedCounts;
        }

        @Override
        public Set<UUID> missionIdsAssignedTo(UUID crewUserId, UUID organisationId) {
            return assignedMissions;
        }
    }
}
