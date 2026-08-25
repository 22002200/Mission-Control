package com.missioncontrol.assignment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.missioncontrol.crew.api.CrewDirectory;
import com.missioncontrol.identity.api.UserDirectory;
import com.missioncontrol.identity.api.UserSummary;
import com.missioncontrol.mission.api.MissionPlan;
import com.missioncontrol.mission.api.MissionPlans;
import com.missioncontrol.mission.api.MissionStatus;
import com.missioncontrol.mission.api.MissionWindow;
import com.missioncontrol.mission.api.MissionWindows;
import com.missioncontrol.mission.api.RequirementPlan;
import com.missioncontrol.mission.api.RequirementSeat;
import com.missioncontrol.platform.ApiProblemException;
import com.missioncontrol.platform.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * The business rules of feature 07, without a database.
 *
 * <p>The ports are mocked because they belong to other modules and their behaviour is settled by
 * those modules' own tests; the repository is mocked because what is under test here is the order
 * of the checks and which error each one produces, not SQL. The two collaborators that <em>are</em>
 * real - {@link AssignmentAccess} and {@link CrewIdentity} - are the rules themselves, and mocking
 * them would leave nothing worth asserting.
 *
 * <p>What this deliberately cannot prove is the concurrency. That the mission row is locked before
 * anything is counted, and that a crew member's own rows are locked after it, only means something
 * against a real database with two real transactions - {@code AssignmentConcurrencyIT} does that.
 * Here the locking calls are asserted as calls, which at least catches their being dropped.
 */
@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID MISSION = UUID.randomUUID();
    private static final UUID REQUIREMENT = UUID.randomUUID();
    private static final UUID LEAD = UUID.randomUUID();
    private static final UUID OTHER_LEAD = UUID.randomUUID();
    private static final UUID DIRECTOR = UUID.randomUUID();

    private static final UUID CREW_USER = UUID.randomUUID();
    private static final UUID CREW = UUID.randomUUID();
    private static final UUID OTHER_CREW = UUID.randomUUID();

    private static final Instant STARTS = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-10-31T00:00:00Z");

    @Mock private AssignmentRepository assignments;
    @Mock private MissionPlans missionPlans;
    @Mock private MissionWindows missionWindows;
    @Mock private CrewDirectory crew;
    @Mock private UserDirectory users;
    @Mock private CurrentUser currentUser;

    private AssignmentService service;

    /** Rows the service saved, so an offer can be inspected without a database. */
    private final List<AssignmentEntity> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        saved.clear();
        lenient().when(currentUser.organisationId()).thenReturn(ORG);
        lenient().when(currentUser.userId()).thenReturn(LEAD);
        lenient().when(assignments.save(any())).thenAnswer(call -> {
            AssignmentEntity entity = call.getArgument(0);
            saved.add(entity);
            return entity;
        });
        lenient().when(crew.userIdsByCrewMemberId(anyCollection(), any()))
                .thenAnswer(call -> namesFor(call.getArgument(0)));
        lenient().when(users.findByIds(anyCollection(), any()))
                .thenReturn(Map.of(CREW_USER, new UserSummary(CREW_USER, "Ines Varga")));

        service = new AssignmentService(
                assignments,
                new AssignmentAccess(currentUser),
                new CrewIdentity(crew, users),
                missionPlans,
                missionWindows,
                currentUser,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("Offering a place")
    class Offering {

        @Test
        @DisplayName("The owning lead can offer on an APPROVED mission - FR-1")
        void ownerCanOffer() {
            givenApprovedMission();
            givenSeatsTaken(0);

            AssignmentResponse response = service.offer(MISSION, offer());

            assertThat(response.status()).isEqualTo(AssignmentStatus.OFFERED);
            assertThat(response.crewMember().id()).isEqualTo(CREW);
            assertThat(response.crewMember().fullName()).isEqualTo("Ines Varga");
            assertThat(response.offeredAt()).isEqualTo(NOW);
            assertThat(response.respondedAt()).isNull();
            assertThat(saved).singleElement()
                    .satisfies(row -> assertThat(row.getOrganisationId()).isEqualTo(ORG));
        }

        @Test
        @DisplayName("The mission row is locked before anything is counted")
        void locksBeforeCounting() {
            givenApprovedMission();
            givenSeatsTaken(0);

            service.offer(MISSION, offer());

            // forStaffingUpdate rather than forStaffing. The capacity check below it is a count,
            // and a count read without the lock is one two callers can both act on.
            verify(missionPlans).forStaffingUpdate(MISSION);
            verify(missionPlans, never()).forStaffing(any());
        }

        @ParameterizedTest
        @EnumSource(value = MissionStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "APPROVED")
        @DisplayName("Every status but APPROVED refuses an offer with 409 - BR-1")
        void onlyApprovedTakesOffers(MissionStatus status) {
            givenMission(status, LEAD);

            assertProblem(() -> service.offer(MISSION, offer()), HttpStatus.CONFLICT)
                    .hasMessageContaining("APPROVED");
        }

        @Test
        @DisplayName("An ACTIVE mission refuses an offer too - the seat has to be re-planned")
        void activeIsNotOfferable() {
            givenMission(MissionStatus.ACTIVE, LEAD);

            // The consequence of A1 read strictly, and it is deliberate: a place vacated after
            // launch is dealt with by editing the mission, which sends it back to PLAN under M5.
            assertProblem(() -> service.offer(MISSION, offer()), HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("A director is refused with 403, not 404 - BR-9")
        void directorCannotOffer() {
            when(currentUser.userId()).thenReturn(DIRECTOR);
            givenApprovedMission();

            // 403 rather than 404 because they can see the mission - MissionPlans already let them
            // through. The refusal is about the verb, and hiding the mission would be a lie.
            assertProblem(() -> service.offer(MISSION, offer()), HttpStatus.FORBIDDEN)
                    .hasMessageContaining("mission lead who owns");
        }

        @Test
        @DisplayName("A requirement on another mission is 404, not 400")
        void unknownRequirementIsNotFound() {
            givenApprovedMission();

            assertProblem(
                    () -> service.offer(MISSION,
                            new OfferAssignmentRequest(UUID.randomUUID(), CREW)),
                    HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("A crew member from another organisation is 404 - BR-10")
        void foreignCrewMemberIsNotFound() {
            givenApprovedMission();
            when(crew.userIdsByCrewMemberId(anyCollection(), any())).thenReturn(Map.of());

            // Absent rather than forbidden, so this cannot be used to discover that another tenant
            // employs a given id.
            assertProblem(() -> service.offer(MISSION, offer()), HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Offering beyond requiredCount is refused - BR-2")
        void requirementFull() {
            givenApprovedMission();
            givenSeatsTaken(2);

            assertProblem(() -> service.offer(MISSION, offer()), HttpStatus.CONFLICT)
                    .hasMessageContaining("taken");
        }

        @Test
        @DisplayName("Offering the same crew member twice on one mission is refused - BR-5")
        void duplicateOnMission() {
            givenApprovedMission();
            givenSeatsTaken(0);
            when(assignments.crewOnMission(any(), anyCollection())).thenReturn(List.of(CREW));

            assertProblem(() -> service.offer(MISSION, offer()), HttpStatus.CONFLICT)
                    .hasMessageContaining("already holds a place");
        }
    }

    @Nested
    @DisplayName("Accepting")
    class Accepting {

        @Test
        @DisplayName("The named crew member can accept - FR-4")
        void namedCrewMemberAccepts() {
            givenCaller(CREW_USER, CREW);
            AssignmentEntity assignment = givenAssignment(AssignmentStatus.OFFERED, CREW);
            givenAcceptableMission();

            AssignmentResponse response = service.accept(assignment.getId());

            assertThat(response.status()).isEqualTo(AssignmentStatus.ACCEPTED);
            assertThat(response.respondedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("Both locks are taken, mission first")
        void takesBothLocks() {
            givenCaller(CREW_USER, CREW);
            AssignmentEntity assignment = givenAssignment(AssignmentStatus.OFFERED, CREW);
            givenAcceptableMission();

            service.accept(assignment.getId());

            // The mission row serialises two people racing for one seat; the crew member's own
            // rows serialise one person accepting two overlapping offers. Neither substitutes for
            // the other, and the order between them is what keeps close and accept deadlock-free.
            verify(missionWindows).lockForUpdate(MISSION, ORG);
            verify(assignments).lockOpenFor(eq(CREW), eq(ORG), anyCollection());
        }

        @Test
        @DisplayName("A different crew member gets 403 - BR-6")
        void anotherCrewMemberIsForbidden() {
            givenCaller(CREW_USER, OTHER_CREW);
            AssignmentEntity assignment = givenAssignment(AssignmentStatus.OFFERED, CREW);

            assertProblem(() -> service.accept(assignment.getId()), HttpStatus.FORBIDDEN)
                    .hasMessageContaining("crew member offered this place");
        }

        @Test
        @DisplayName("A caller with no crew profile gets 403, not an error")
        void nonCrewCallerIsForbidden() {
            when(crew.crewMemberIdOf(any(), any())).thenReturn(Optional.empty());
            AssignmentEntity assignment = givenAssignment(AssignmentStatus.OFFERED, CREW);

            assertProblem(() -> service.accept(assignment.getId()), HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("A declined offer cannot be accepted afterwards - BR-7")
        void declinedCannotBeAccepted() {
            givenCaller(CREW_USER, CREW);
            AssignmentEntity assignment = givenAssignment(AssignmentStatus.DECLINED, CREW);
            givenMissionWindow(MissionStatus.APPROVED);

            assertProblem(() -> service.accept(assignment.getId()), HttpStatus.CONFLICT)
                    .hasMessageContaining("DECLINED");
        }

        @Test
        @DisplayName("A requirement that filled up since the offer refuses the acceptance - BR-2")
        void seatFilledSinceTheOffer() {
            givenCaller(CREW_USER, CREW);
            AssignmentEntity assignment = givenAssignment(AssignmentStatus.OFFERED, CREW);
            givenMissionWindow(MissionStatus.APPROVED);
            givenSeat(1);
            // The one seat was taken by somebody else accepting first. Stubbed per status rather
            // than as one number, so the error can tell a line full of acceptances - which is
            // finished - from one full of offers, which a withdrawal would free.
            givenCounts(1, 0);

            // An offer reserves the seat against further offers, not against another acceptance -
            // and the two are only the same thing when nobody declines.
            assertProblem(() -> service.accept(assignment.getId()), HttpStatus.CONFLICT)
                    .hasMessageContaining("already filled");
        }

        @Test
        @DisplayName("An overlapping accepted mission blocks the acceptance - BR-3")
        void scheduleConflict() {
            givenCaller(CREW_USER, CREW);
            AssignmentEntity assignment = givenAssignment(AssignmentStatus.OFFERED, CREW);
            givenAcceptableMission();
            givenAcceptedElsewhere(clashing(MissionStatus.ACTIVE));

            assertProblem(() -> service.accept(assignment.getId()), HttpStatus.CONFLICT)
                    .hasMessageContaining("Zenith Station Run");
        }

        @Test
        @DisplayName("An overlapping mission that is CLOSED does not block - A8")
        void closedMissionDoesNotBlock() {
            givenCaller(CREW_USER, CREW);
            AssignmentEntity assignment = givenAssignment(AssignmentStatus.OFFERED, CREW);
            givenAcceptableMission();
            givenAcceptedElsewhere(clashing(MissionStatus.CLOSED));

            // Aborting a mission frees its crew immediately, which is only true if a closed
            // mission stops occupying the calendar.
            assertThat(service.accept(assignment.getId()).status())
                    .isEqualTo(AssignmentStatus.ACCEPTED);
        }

        @Test
        @DisplayName("Two missions that do not overlap can both be accepted")
        void nonOverlappingIsFine() {
            givenCaller(CREW_USER, CREW);
            AssignmentEntity assignment = givenAssignment(AssignmentStatus.OFFERED, CREW);
            givenAcceptableMission();
            givenAcceptedElsewhere(new MissionWindow(UUID.randomUUID(), ORG, "Halcyon Trial",
                    MissionStatus.ACTIVE, OTHER_LEAD, Instant.parse("2026-01-01T00:00:00Z"),
                    Instant.parse("2026-02-01T00:00:00Z"), false));

            assertThat(service.accept(assignment.getId()).status())
                    .isEqualTo(AssignmentStatus.ACCEPTED);
        }
    }

    @Nested
    @DisplayName("Declining and withdrawing")
    class Settling {

        @Test
        @DisplayName("The named crew member can decline - FR-5")
        void crewMemberDeclines() {
            givenCaller(CREW_USER, CREW);
            AssignmentEntity assignment = givenAssignment(AssignmentStatus.OFFERED, CREW);
            givenMissionWindow(MissionStatus.APPROVED);

            AssignmentResponse response = service.decline(assignment.getId());

            assertThat(response.status()).isEqualTo(AssignmentStatus.DECLINED);
            assertThat(response.respondedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("A crew member cannot decline an acceptance - once accepted, they are assigned")
        void acceptedCannotBeDeclined() {
            givenCaller(CREW_USER, CREW);
            AssignmentEntity assignment = givenAssignment(AssignmentStatus.ACCEPTED, CREW);
            givenMissionWindow(MissionStatus.APPROVED);

            // A7 allows ACCEPTED to WITHDRAWN and nothing else, and BR-9 puts that verb in the
            // mission lead's hands. Releasing somebody who has committed is the lead's decision.
            assertProblem(() -> service.decline(assignment.getId()), HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("The owning lead can withdraw an acceptance - FR-6")
        void leadWithdraws() {
            AssignmentEntity assignment = givenAssignment(AssignmentStatus.ACCEPTED, CREW);
            givenPlan(MissionStatus.ACTIVE, LEAD);
            when(missionPlans.forStaffingUpdate(MISSION)).thenReturn(givenPlan(MissionStatus.ACTIVE, LEAD));

            AssignmentResponse response = service.withdraw(assignment.getId());

            assertThat(response.status()).isEqualTo(AssignmentStatus.WITHDRAWN);
            assertThat(response.respondedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("A director cannot withdraw - BR-9")
        void directorCannotWithdraw() {
            when(currentUser.userId()).thenReturn(DIRECTOR);
            AssignmentEntity assignment = givenAssignment(AssignmentStatus.ACCEPTED, CREW);
            when(missionPlans.forStaffingUpdate(MISSION)).thenReturn(givenPlan(MissionStatus.ACTIVE, LEAD));

            // Narrower than invariant M6 allows, on purpose. A director's lever on a mission they
            // disagree with is closing it, which withdraws its outstanding offers anyway.
            assertProblem(() -> service.withdraw(assignment.getId()), HttpStatus.FORBIDDEN)
                    .hasMessageContaining("mission lead who owns");
        }

        @Test
        @DisplayName("Withdrawing does not touch the mission's status - BR-11")
        void withdrawingLeavesTheMissionAlone() {
            AssignmentEntity assignment = givenAssignment(AssignmentStatus.ACCEPTED, CREW);
            when(missionPlans.forStaffingUpdate(MISSION)).thenReturn(givenPlan(MissionStatus.ACTIVE, LEAD));

            service.withdraw(assignment.getId());

            // M11 is a precondition of starting, not a standing invariant. Nothing in this module
            // can send a mission backwards, and the only way to check that is that it never asks.
            verify(missionPlans, never()).forStaffing(any());
            assertThat(saved).isEmpty();
        }

        @Test
        @DisplayName("An already-withdrawn assignment cannot be withdrawn again")
        void terminalCannotBeWithdrawn() {
            AssignmentEntity assignment = givenAssignment(AssignmentStatus.WITHDRAWN, CREW);
            when(missionPlans.forStaffingUpdate(MISSION)).thenReturn(givenPlan(MissionStatus.ACTIVE, LEAD));

            assertProblem(() -> service.withdraw(assignment.getId()), HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("An assignment in another organisation is 404")
        void crossTenantIsNotFound() {
            when(assignments.findByIdAndOrganisationId(any(), any())).thenReturn(Optional.empty());

            assertProblem(() -> service.withdraw(UUID.randomUUID()), HttpStatus.NOT_FOUND);
        }
    }

    // --- arrangement helpers -------------------------------------------------------------------

    private static OfferAssignmentRequest offer() {
        return new OfferAssignmentRequest(REQUIREMENT, CREW);
    }

    private void givenApprovedMission() {
        givenMission(MissionStatus.APPROVED, LEAD);
    }

    private void givenMission(MissionStatus status, UUID owner) {
        when(missionPlans.forStaffingUpdate(MISSION)).thenReturn(givenPlan(status, owner));
    }

    private static MissionPlan givenPlan(MissionStatus status, UUID owner) {
        return new MissionPlan(MISSION, ORG, status, owner, STARTS, ENDS,
                List.of(new RequirementPlan(REQUIREMENT, "Thermal Engineer", 2, 0, 0, List.of())));
    }

    private void givenSeatsTaken(long taken) {
        lenient().when(assignments.countByRequirement(any(), any(), anyCollection()))
                .thenReturn(taken);
        lenient().when(assignments.crewOnMission(any(), anyCollection())).thenReturn(List.of());
    }

    private void givenCaller(UUID userId, UUID crewMemberId) {
        when(currentUser.userId()).thenReturn(userId);
        when(crew.crewMemberIdOf(userId, ORG)).thenReturn(Optional.of(crewMemberId));
    }

    private AssignmentEntity givenAssignment(AssignmentStatus status, UUID crewMemberId) {
        AssignmentEntity assignment = AssignmentEntity.builder()
                .id(UUID.randomUUID())
                .organisationId(ORG)
                .missionId(MISSION)
                .crewRequirementId(REQUIREMENT)
                .crewMemberId(crewMemberId)
                .status(status)
                .offeredAt(NOW.minusSeconds(3600))
                .respondedAt(status == AssignmentStatus.OFFERED ? null : NOW.minusSeconds(60))
                .build();
        lenient().when(assignments.findByIdAndOrganisationId(assignment.getId(), ORG))
                .thenReturn(Optional.of(assignment));
        return assignment;
    }

    private void givenMissionWindow(MissionStatus status) {
        lenient().when(missionWindows.lockForUpdate(MISSION, ORG))
                .thenReturn(new MissionWindow(MISSION, ORG, "Perihelion Watch", status, LEAD,
                        STARTS, ENDS, false));
    }

    /** An offer that will accept cleanly: mission open, seat free, nothing else booked. */
    private void givenAcceptableMission() {
        givenMissionWindow(MissionStatus.APPROVED);
        givenSeat(2);
        lenient().when(assignments.countByRequirement(any(), any(), anyCollection())).thenReturn(0L);
        lenient().when(assignments.crewAndMissionsFor(any(), anyCollection(), any()))
                .thenReturn(List.of());
    }

    /** Accepted and offered counts, answered separately so the error can distinguish them. */
    private void givenCounts(long accepted, long offered) {
        lenient().when(assignments.countByRequirement(any(), any(), anyCollection()))
                .thenAnswer(call -> {
                    Collection<?> statuses = call.getArgument(2);
                    if (statuses.contains(AssignmentStatus.OFFERED)
                            && statuses.contains(AssignmentStatus.ACCEPTED)) {
                        return accepted + offered;
                    }
                    return statuses.contains(AssignmentStatus.ACCEPTED) ? accepted : offered;
                });
    }

    private void givenSeat(int requiredCount) {
        lenient().when(missionWindows.findRequirements(anyCollection(), any()))
                .thenReturn(Map.of(REQUIREMENT,
                        new RequirementSeat(REQUIREMENT, MISSION, "Thermal Engineer", requiredCount)));
    }

    /** The crew member already holds an accepted place on this other mission. */
    private void givenAcceptedElsewhere(MissionWindow other) {
        when(assignments.crewAndMissionsFor(any(), anyCollection(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{CREW, other.id()}));
        when(missionWindows.findByIds(anyCollection(), any())).thenReturn(Map.of(other.id(), other));
    }

    private static MissionWindow clashing(MissionStatus status) {
        return new MissionWindow(UUID.randomUUID(), ORG, "Zenith Station Run", status, OTHER_LEAD,
                Instant.parse("2026-10-15T00:00:00Z"), Instant.parse("2026-12-05T00:00:00Z"),
                false);
    }

    private static Map<UUID, UUID> namesFor(Collection<UUID> crewMemberIds) {
        return crewMemberIds.contains(CREW) ? Map.of(CREW, CREW_USER) : Map.of();
    }

    private static org.assertj.core.api.AbstractThrowableAssert<?, ?> assertProblem(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus expected) {
        return assertThatThrownBy(call)
                .isInstanceOfSatisfying(ApiProblemException.class,
                        problem -> assertThat(problem.getStatus()).isEqualTo(expected));
    }
}
