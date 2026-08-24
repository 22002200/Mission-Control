package com.missioncontrol.mission.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.missioncontrol.identity.api.UserDirectory;
import com.missioncontrol.identity.api.UserSummary;
import com.missioncontrol.mission.api.StaffingReadModel;
import com.missioncontrol.platform.CurrentUser;
import com.missioncontrol.shared.UserRole;
import com.missioncontrol.skill.api.SkillCatalogue;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

/**
 * The approval rules, away from HTTP and the database.
 *
 * <p>{@code MissionAccess}, {@code MissionLoader}, {@code MissionApprovals} and
 * {@code MissionDetailAssembler} are real here, only the repositories and the security context are
 * mocked. Those four types <em>are</em> the rules under test - the order of the visibility,
 * ownership and state checks especially - and mocking them would leave the test asserting that the
 * service calls things, rather than that a mission lead cannot submit someone else's plan.
 *
 * <p>The one rule deliberately absent is BR-3, that only a director may approve or reject. That is
 * a pure role check carried by {@code PreAuthorize} on the controller, so it is covered by
 * {@code MissionApprovalRoleApiTest} where the filter chain is real. A service test asserting it
 * would be asserting something the service does not do.
 */
@ExtendWith(MockitoExtension.class)
class MissionApprovalServiceTest {

    private static final UUID ORG = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID DIRECTOR = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID LEAD = UUID.fromString("a1000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_LEAD = UUID.fromString("a1000000-0000-0000-0000-000000000003");
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

    private MissionApprovalService service;
    private TickingClock clock;

    @BeforeEach
    void setUp() {
        clock = new TickingClock(NOW);
        lenient().when(staffingProvider.getIfAvailable(any())).thenReturn(new UnstaffedReadModel());
        lenient().when(currentUser.organisationId()).thenReturn(ORG);
        lenient().when(currentUser.userId()).thenReturn(LEAD);
        lenient().when(currentUser.role()).thenReturn(UserRole.MISSION_LEAD);
        lenient().when(skills.findByIds(anyCollection(), any())).thenReturn(Map.of());
        lenient().when(users.findByIds(anyCollection(), any())).thenReturn(Map.of(
                LEAD, new UserSummary(LEAD, "Marcus Reyes"),
                DIRECTOR, new UserSummary(DIRECTOR, "Vera Lindholm")));
        lenient().when(approvalRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        MissionStaffing staffing = new MissionStaffing(staffingProvider);
        MissionAccess access = new MissionAccess(currentUser, staffing);
        service = new MissionApprovalService(
                new MissionLoader(missions, access, currentUser),
                access,
                new MissionApprovals(approvalRepository),
                new MissionDetailAssembler(missions, staffing, skills, users, currentUser),
                currentUser,
                clock);
    }

    @Nested
    @DisplayName("Submitting")
    class Submitting {

        @Test
        @DisplayName("The owning lead moves a staffed PLAN mission to PENDING_APPROVAL - FR-1")
        void ownerSubmitsAPlannedMission() {
            given(planWithRequirement());

            MissionResponse submitted = service.submit(MISSION);

            assertThat(submitted.status()).isEqualTo(MissionStatus.PENDING_APPROVAL);

            ArgumentCaptor<MissionApprovalEntity> opened =
                    ArgumentCaptor.forClass(MissionApprovalEntity.class);
            verify(approvalRepository).save(opened.capture());
            assertThat(opened.getValue().getDecision()).isEqualTo(ApprovalDecision.PENDING);
            assertThat(opened.getValue().getSubmittedBy()).isEqualTo(LEAD);
            assertThat(opened.getValue().getDecidedBy()).isNull();
            assertThat(opened.getValue().getDecidedAt()).isNull();
            assertThat(opened.getValue().getOrganisationId()).isEqualTo(ORG);
        }

        @Test
        @DisplayName("A mission with no crew requirements is refused - M12, BR-5")
        void refusesAMissionWithNothingToStaff() {
            given(mission(MissionStatus.PLAN, LEAD));

            assertThatThrownBy(() -> service.submit(MISSION))
                    .isInstanceOf(MissionHasNoRequirementsException.class);

            // Nothing half-applied: no cycle opened for a submission that did not happen.
            verify(approvalRepository, never()).save(any());
        }

        @Test
        @DisplayName("A director cannot submit, and is told so rather than told it is missing")
        void directorIsForbidden() {
            when(currentUser.userId()).thenReturn(DIRECTOR);
            when(currentUser.role()).thenReturn(UserRole.DIRECTOR);
            given(planWithRequirement());

            assertThatThrownBy(() -> service.submit(MISSION))
                    .isInstanceOf(MissionForbiddenException.class)
                    .hasMessageContaining("submit this mission for approval");
        }

        @Test
        @DisplayName("A lead who does not own it gets 404, not 403 - it is not theirs to see")
        void nonOwningLeadSeesNothing() {
            when(currentUser.userId()).thenReturn(OTHER_LEAD);
            given(planWithRequirement());

            assertThatThrownBy(() -> service.submit(MISSION))
                    .isInstanceOf(MissionNotFoundException.class);
        }

        @ParameterizedTest
        @EnumSource(value = MissionStatus.class, names = "PLAN", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Only PLAN may be submitted - BR-1, M3")
        void refusesEveryOtherStatus(MissionStatus status) {
            given(mission(status, LEAD));

            assertThatThrownBy(() -> service.submit(MISSION))
                    .isInstanceOf(InvalidMissionTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Deciding")
    class Deciding {

        @Test
        @DisplayName("A director approves a pending mission and the cycle records them - FR-2")
        void directorApproves() {
            asDirector();
            MissionEntity mission = given(mission(MissionStatus.PENDING_APPROVAL, LEAD));
            MissionApprovalEntity cycle = givenOpenCycle(mission);

            MissionResponse approved = service.approve(MISSION, new ApproveMissionRequest("Cleared."));

            assertThat(approved.status()).isEqualTo(MissionStatus.APPROVED);
            assertThat(cycle.getDecision()).isEqualTo(ApprovalDecision.APPROVED);
            assertThat(cycle.getDecidedBy()).isEqualTo(DIRECTOR);
            assertThat(cycle.getDecidedAt()).isNotNull();
            assertThat(cycle.getComment()).isEqualTo("Cleared.");
        }

        @Test
        @DisplayName("Approving without a note leaves the comment null rather than blank")
        void approvingWithoutANote() {
            asDirector();
            MissionEntity mission = given(mission(MissionStatus.PENDING_APPROVAL, LEAD));
            MissionApprovalEntity cycle = givenOpenCycle(mission);

            service.approve(MISSION, new ApproveMissionRequest("   "));

            assertThat(cycle.getComment()).isNull();
        }

        @Test
        @DisplayName("A director rejects with a reason - FR-3, BR-6")
        void directorRejects() {
            asDirector();
            MissionEntity mission = given(mission(MissionStatus.PENDING_APPROVAL, LEAD));
            MissionApprovalEntity cycle = givenOpenCycle(mission);

            MissionResponse rejected =
                    service.reject(MISSION, new RejectMissionRequest("Timeline clashes."));

            assertThat(rejected.status()).isEqualTo(MissionStatus.REJECTED);
            assertThat(cycle.getDecision()).isEqualTo(ApprovalDecision.REJECTED);
            assertThat(cycle.getComment()).isEqualTo("Timeline clashes.");
        }

        @Test
        @DisplayName("An already-approved mission cannot be approved again, and says what it is now")
        void refusesASecondDecision() {
            asDirector();
            given(mission(MissionStatus.APPROVED, LEAD));

            assertThatThrownBy(() -> service.approve(MISSION, new ApproveMissionRequest(null)))
                    .isInstanceOf(InvalidMissionTransitionException.class)
                    .hasMessageContaining("APPROVED");
        }

        @ParameterizedTest
        @EnumSource(value = MissionStatus.class, names = "PENDING_APPROVAL",
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Approve and reject are valid only from PENDING_APPROVAL - BR-3, M7")
        void refusesEveryOtherStatus(MissionStatus status) {
            asDirector();
            given(mission(status, LEAD));

            assertThatThrownBy(() -> service.approve(MISSION, new ApproveMissionRequest(null)))
                    .isInstanceOf(InvalidMissionTransitionException.class);
            assertThatThrownBy(() -> service.reject(MISSION, new RejectMissionRequest("No.")))
                    .isInstanceOf(InvalidMissionTransitionException.class);
        }

        @Test
        @DisplayName("A pending mission with no open cycle is a bug, not a client error")
        void missingCycleFailsLoudly() {
            asDirector();
            given(mission(MissionStatus.PENDING_APPROVAL, LEAD));
            // No cycle arranged. The ledger contradicting the mission's own status cannot happen
            // through the API - submit opens the cycle in the same transaction, and the seed data
            // carries one for every mission it puts past PLAN - so this must not be dressed up as
            // a tidy 409 that hides it.
            assertThatThrownBy(() -> service.approve(MISSION, new ApproveMissionRequest(null)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no open approval cycle");
        }
    }

    @Nested
    @DisplayName("Returning to plan")
    class Replanning {

        @Test
        @DisplayName("The owning lead sends a rejected mission back to PLAN - FR-4")
        void ownerReplans() {
            given(mission(MissionStatus.REJECTED, LEAD));

            assertThat(service.replan(MISSION).status()).isEqualTo(MissionStatus.PLAN);
        }

        @Test
        @DisplayName("The history is left untouched - BR-9")
        void keepsTheHistory() {
            given(mission(MissionStatus.REJECTED, LEAD));

            service.replan(MISSION);

            verify(approvalRepository, never()).save(any());
        }

        @Test
        @DisplayName("A director cannot replan - owner-only, narrower than M6 on purpose")
        void directorIsForbidden() {
            asDirector();
            given(mission(MissionStatus.REJECTED, LEAD));

            assertThatThrownBy(() -> service.replan(MISSION))
                    .isInstanceOf(MissionForbiddenException.class)
                    .hasMessageContaining("return this mission to planning");
        }

        @ParameterizedTest
        @EnumSource(value = MissionStatus.class, names = "REJECTED", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Only a REJECTED mission may go back to PLAN - BR-7, M3")
        void refusesEveryOtherStatus(MissionStatus status) {
            given(mission(status, LEAD));

            assertThatThrownBy(() -> service.replan(MISSION))
                    .isInstanceOf(InvalidMissionTransitionException.class);
        }
    }

    @Nested
    @DisplayName("History")
    class History {

        @Test
        @DisplayName("Cycles come back in the repository's order, with both names resolved")
        void resolvesNames() {
            MissionEntity mission = given(mission(MissionStatus.REJECTED, LEAD));
            MissionApprovalEntity decided = cycle(mission, ApprovalDecision.REJECTED, "Too tight.");
            when(approvalRepository.findHistory(MISSION, ORG)).thenReturn(List.of(decided));

            List<MissionApprovalResponse> history = service.history(MISSION);

            assertThat(history).hasSize(1);
            assertThat(history.getFirst().submittedBy().fullName()).isEqualTo("Marcus Reyes");
            assertThat(history.getFirst().decidedBy().fullName()).isEqualTo("Vera Lindholm");
            assertThat(history.getFirst().comment()).isEqualTo("Too tight.");
        }

        @Test
        @DisplayName("A pending cycle reports no decider at all, rather than an unknown one")
        void pendingCycleHasNoDecider() {
            MissionEntity mission = given(mission(MissionStatus.PENDING_APPROVAL, LEAD));
            when(approvalRepository.findHistory(MISSION, ORG))
                    .thenReturn(List.of(openCycle(mission)));

            MissionApprovalResponse only = service.history(MISSION).getFirst();

            assertThat(only.decision()).isEqualTo(ApprovalDecision.PENDING);
            assertThat(only.decidedBy()).isNull();
            assertThat(only.decidedAt()).isNull();
        }

        @Test
        @DisplayName("A mission that was never submitted has an empty history, not an error")
        void plannedMissionHasNoHistory() {
            given(mission(MissionStatus.PLAN, LEAD));
            when(approvalRepository.findHistory(MISSION, ORG)).thenReturn(List.of());

            assertThat(service.history(MISSION)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Across a whole reject-and-resubmit loop")
    class FullLoop {

        @Test
        @DisplayName("Each cycle is its own record and the mission never has two pending - FR-7, M8")
        void twoCyclesInOrder() {
            MissionEntity mission = given(planWithRequirement());

            service.submit(MISSION);
            MissionApprovalEntity first = savedCycles().getFirst();
            openCycleIs(mission, first);

            asDirector();
            service.reject(MISSION, new RejectMissionRequest("Reshape the timeline."));
            assertThat(first.getDecision()).isEqualTo(ApprovalDecision.REJECTED);
            openCycleIs(mission, null);

            asLead();
            service.replan(MISSION);
            service.submit(MISSION);

            List<MissionApprovalEntity> cycles = savedCycles();
            assertThat(cycles).hasSize(2);
            assertThat(cycles.get(1).getDecision()).isEqualTo(ApprovalDecision.PENDING);
            // The second cycle is a new row, not the first one reopened - that is BR-9.
            assertThat(cycles.get(1).getId()).isNotEqualTo(first.getId());
            assertThat(cycles.get(1).getSubmittedAt()).isAfter(first.getSubmittedAt());
        }
    }

    // --- fixtures -----------------------------------------------------------------------------

    private void asDirector() {
        lenient().when(currentUser.userId()).thenReturn(DIRECTOR);
        lenient().when(currentUser.role()).thenReturn(UserRole.DIRECTOR);
    }

    private void asLead() {
        lenient().when(currentUser.userId()).thenReturn(LEAD);
        lenient().when(currentUser.role()).thenReturn(UserRole.MISSION_LEAD);
    }

    /** Arranges the mission so that both the locking read and the detail read find it. */
    private MissionEntity given(MissionEntity mission) {
        lenient().when(missions.lockByIdAndOrganisationId(MISSION, ORG))
                .thenReturn(Optional.of(mission));
        lenient().when(missions.findDetailByIdAndOrganisationId(MISSION, ORG))
                .thenReturn(Optional.of(mission));
        lenient().when(missions.findByIdAndOrganisationId(MISSION, ORG))
                .thenReturn(Optional.of(mission));
        return mission;
    }

    private MissionApprovalEntity givenOpenCycle(MissionEntity mission) {
        MissionApprovalEntity cycle = openCycle(mission);
        openCycleIs(mission, cycle);
        return cycle;
    }

    private void openCycleIs(MissionEntity mission, MissionApprovalEntity cycle) {
        lenient().when(approvalRepository.findByMissionIdAndOrganisationIdAndDecision(
                        mission.getId(), ORG, ApprovalDecision.PENDING))
                .thenReturn(Optional.ofNullable(cycle));
    }

    private List<MissionApprovalEntity> savedCycles() {
        ArgumentCaptor<MissionApprovalEntity> saved =
                ArgumentCaptor.forClass(MissionApprovalEntity.class);
        verify(approvalRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        return saved.getAllValues();
    }

    private MissionApprovalEntity openCycle(MissionEntity mission) {
        return MissionApprovalEntity.builder()
                .id(UUID.randomUUID())
                .organisationId(ORG)
                .missionId(mission.getId())
                .submittedBy(LEAD)
                .submittedAt(NOW)
                .decision(ApprovalDecision.PENDING)
                .build();
    }

    private MissionApprovalEntity cycle(MissionEntity mission, ApprovalDecision decision,
                                        String comment) {
        MissionApprovalEntity open = openCycle(mission);
        open.settle(decision, DIRECTOR, comment, NOW.plusSeconds(60));
        return open;
    }

    private static MissionEntity mission(MissionStatus status, UUID leadId) {
        return MissionEntity.builder()
                .id(MISSION)
                .organisationId(ORG)
                .name("Aurora Survey")
                .status(status)
                .closeReason(status == MissionStatus.CLOSED ? MissionCloseReason.ABORTED : null)
                .missionLeadId(leadId)
                .startsAt(STARTS)
                .endsAt(ENDS)
                .createdBy(leadId)
                .createdAt(NOW)
                .updatedAt(NOW)
                .requirements(new LinkedHashSet<>())
                .build();
    }

    private static MissionEntity planWithRequirement() {
        MissionEntity mission = mission(MissionStatus.PLAN, LEAD);
        CrewRequirementEntity requirement = CrewRequirementEntity.builder()
                .id(REQUIREMENT)
                .organisationId(ORG)
                .title("Flight Engineer")
                .requiredCount(1)
                .build();
        mission.addRequirement(requirement, NOW);
        return mission;
    }

    /**
     * A clock that advances a second each time it is read.
     *
     * <p>{@code Clock.fixed} cannot be used here. Two cycles created under it share a
     * {@code submittedAt} to the microsecond, which makes 'the history holds two cycles in order'
     * an assertion about nothing. The integration test gets its separation from the wall clock;
     * this is how the unit test gets its own without becoming timing-dependent.
     */
    private static final class TickingClock extends Clock {

        private Instant now;

        private TickingClock(Instant start) {
            this.now = start;
        }

        @Override
        public Instant instant() {
            now = now.plus(Duration.ofSeconds(1));
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
