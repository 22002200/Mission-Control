package com.missioncontrol.assignment.internal;

import com.missioncontrol.mission.api.MissionPlan;
import com.missioncontrol.mission.api.MissionPlans;
import com.missioncontrol.mission.api.MissionWindow;
import com.missioncontrol.mission.api.MissionWindows;
import com.missioncontrol.mission.api.RequirementPlan;
import com.missioncontrol.mission.api.RequirementSeat;
import com.missioncontrol.platform.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Offering places on a mission, and what becomes of each offer.
 *
 * <p>The organisation comes from {@link CurrentUser} on every call and from nowhere else - no
 * method here takes one as an argument, so a controller has no way to pass the wrong one. That is
 * invariant T1 made structural rather than remembered, the same rule {@code MissionService}
 * follows.
 *
 * <p><strong>Every command takes the mission's write lock first.</strong> Offering and withdrawing
 * reach it through {@code MissionPlans.forStaffingUpdate}, which also applies the mission module's
 * own access rules; accepting and declining reach it through
 * {@code MissionWindows.lockForUpdate}, because their caller is a crew member and would fail those
 * rules by design. Either way the mission row is the first thing locked, which is what lets a close
 * and an acceptance race safely and what caps invariant A2 under load.
 *
 * <p>Accepting takes a second lock, on the crew member's own open assignments - see
 * {@code AssignmentRepository.lockOpenFor}. The mission row cannot serialise one person accepting
 * two overlapping offers, because those are two different missions.
 */
@Service
class AssignmentService {

    /** Offered and accepted together: what invariant A2 caps at {@code requiredCount}. */
    private static final Set<AssignmentStatus> OCCUPYING_A_SEAT =
            EnumSet.of(AssignmentStatus.OFFERED, AssignmentStatus.ACCEPTED);

    /**
     * No status filter means every status.
     *
     * <p>Spelled out as a full set rather than as an absent clause, because JPQL has no way to skip
     * an {@code in} and a filter that silently does nothing is worse than an explicit list. Same
     * reasoning as {@code MissionService.ALL_STATUSES}.
     */
    private static final Set<AssignmentStatus> ALL_STATUSES = EnumSet.allOf(AssignmentStatus.class);

    private final AssignmentRepository assignments;
    private final AssignmentAccess access;
    private final CrewIdentity crewIdentity;
    private final MissionPlans missionPlans;
    private final MissionWindows missionWindows;
    private final CurrentUser currentUser;
    private final Clock clock;

    AssignmentService(AssignmentRepository assignments,
                      AssignmentAccess access,
                      CrewIdentity crewIdentity,
                      MissionPlans missionPlans,
                      MissionWindows missionWindows,
                      CurrentUser currentUser,
                      Clock clock) {
        this.assignments = assignments;
        this.access = access;
        this.crewIdentity = crewIdentity;
        this.missionPlans = missionPlans;
        this.missionWindows = missionWindows;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * Offers one crew member one place - FR-1.
     *
     * <p>The order of the checks is the rule. The mission is loaded and locked first, because it
     * decides both whether the caller may be here at all and whether the capacity count that
     * follows can be trusted. Everything after that is cheap.
     */
    @Transactional
    AssignmentResponse offer(UUID missionId, OfferAssignmentRequest request) {
        MissionPlan mission = missionPlans.forStaffingUpdate(missionId);
        access.requireCanOffer(mission);

        if (!mission.acceptsOffers()) {
            throw InvalidAssignmentTransitionException.mission(mission.status());
        }

        RequirementPlan requirement = mission.requirement(request.crewRequirementId())
                .orElseThrow(AssignmentNotFoundException::requirement);

        // BR-10. A crew member from another organisation is reported absent rather than refused,
        // so this cannot be used to discover that another tenant employs a given id.
        if (!crewIdentity.existsInOrganisation(request.crewMemberId(), mission.organisationId())) {
            throw AssignmentNotFoundException.crewMember();
        }

        requireOpenSeat(requirement.id(), requirement.requiredCount(), mission.organisationId());
        requireNotAlreadyOnMission(missionId, request.crewMemberId(), mission.organisationId());

        Instant now = clock.instant();
        AssignmentEntity assignment = assignments.save(AssignmentEntity.builder()
                .id(UUID.randomUUID())
                .organisationId(mission.organisationId())
                .missionId(missionId)
                .crewRequirementId(requirement.id())
                .crewMemberId(request.crewMemberId())
                .status(AssignmentStatus.OFFERED)
                .offeredAt(now)
                .build());

        return respond(assignment);
    }

    /**
     * A mission's crew, grouped by requirement - FR-2.
     *
     * <p>Four queries for the whole mission however many people are on it: the plan, the
     * assignments, the crew profiles and the names. The requirements come from the plan rather than
     * from the assignments, so a line nobody has been offered still appears - that is the line a
     * lead most needs to see.
     */
    @Transactional(readOnly = true)
    MissionAssignmentsResponse forMission(UUID missionId, AssignmentStatus status) {
        MissionPlan mission = missionPlans.forStaffing(missionId);

        List<AssignmentEntity> rows = assignments.findForMission(
                missionId, mission.organisationId(), statuses(status));
        Map<UUID, CrewMemberRef> names = crewIdentity.namesFor(
                rows.stream().map(AssignmentEntity::getCrewMemberId).distinct().toList(),
                mission.organisationId());

        Map<UUID, List<AssignmentEntity>> byRequirement = rows.stream()
                .collect(Collectors.groupingBy(AssignmentEntity::getCrewRequirementId));

        List<RequirementAssignmentsResponse> requirements = mission.requirements().stream()
                .map(requirement -> toResponse(
                        requirement, byRequirement.getOrDefault(requirement.id(), List.of()), names))
                .toList();

        return new MissionAssignmentsResponse(missionId, requirements);
    }

    /**
     * The caller's own assignments - FR-3, filtered and paged per FR-9.
     *
     * <p>Three queries: the rows, the missions they are on, and the requirement titles. The
     * timeframe filter and the paging are both applied in memory afterwards, because timeframe is a
     * predicate on mission dates and the missions belong to another module - see
     * {@code AssignmentRepository.findForCrewMember}. That is a deliberate trade: a crew member has
     * as many assignments as they have had missions, which is a number this can hold.
     *
     * <p>A caller with no crew profile - a lead or a director who somehow reached here - gets an
     * empty page rather than an error. The endpoint is already role-restricted, so this is
     * belt-and-braces for a state that should not arise.
     */
    @Transactional(readOnly = true)
    MyAssignmentPage mine(AssignmentStatus status, Timeframe timeframe, int page, int size) {
        UUID organisationId = currentUser.organisationId();
        Optional<UUID> crewMemberId = crewIdentity.crewMemberIdOf(currentUser.userId(), organisationId);
        if (crewMemberId.isEmpty()) {
            return new MyAssignmentPage(List.of(), page, size, 0, 0);
        }

        List<AssignmentEntity> rows = assignments.findForCrewMember(
                crewMemberId.get(), organisationId, statuses(status));

        Map<UUID, MissionWindow> missions = missionWindows.findByIds(
                rows.stream().map(AssignmentEntity::getMissionId).distinct().toList(),
                organisationId);
        Map<UUID, RequirementSeat> seats = missionWindows.findRequirements(
                rows.stream().map(AssignmentEntity::getCrewRequirementId).distinct().toList(),
                organisationId);

        Instant now = clock.instant();
        List<MyAssignmentResponse> matching = rows.stream()
                .filter(row -> missions.containsKey(row.getMissionId()))
                .filter(row -> timeframe == null
                        || timeframe.matches(missions.get(row.getMissionId()), now))
                .map(row -> toMine(row, missions.get(row.getMissionId()),
                        seats.get(row.getCrewRequirementId())))
                .toList();

        return paged(matching, page, size);
    }

    /**
     * Accepting - FR-4, and the only command in this module with two locks.
     *
     * <p>The mission row goes first, then the crew member's own open assignments, and that order is
     * never varied. Closing a mission takes the mission row and then touches only that mission's
     * rows, so it can never hold one of ours while waiting for a mission we hold - which is what
     * keeps close and accept from deadlocking.
     *
     * <p>Capacity is re-checked here even though it was checked when the offer was made. A seat can
     * fill in between: an offer is not a reservation of the seat against acceptance, it is a
     * reservation against further offers, and the two are only the same thing when nobody declines.
     */
    @Transactional
    AssignmentResponse accept(UUID assignmentId) {
        UUID organisationId = currentUser.organisationId();
        AssignmentEntity assignment = load(assignmentId, organisationId);

        UUID callerCrewMemberId = requireCrewProfile(organisationId);
        access.requireIsTheCrewMember(assignment, callerCrewMemberId, "accept");

        MissionWindow mission = missionWindows.lockForUpdate(
                assignment.getMissionId(), organisationId);
        // Second lock, always after the mission. Everything below reads this crew member's other
        // commitments, and without it two overlapping acceptances would each read a set that did
        // not yet contain the other.
        assignments.lockOpenFor(callerCrewMemberId, organisationId, OCCUPYING_A_SEAT);

        requireTransition(assignment, AssignmentStatus.ACCEPTED);
        requireSeatStillOpen(assignment, organisationId);
        requireNoScheduleConflict(assignment, mission, callerCrewMemberId, organisationId);

        assignment.accept(clock.instant());
        return respond(assignment);
    }

    /** Declining - FR-5. Frees the place immediately, which is FR-7. */
    @Transactional
    AssignmentResponse decline(UUID assignmentId) {
        UUID organisationId = currentUser.organisationId();
        AssignmentEntity assignment = load(assignmentId, organisationId);

        UUID callerCrewMemberId = requireCrewProfile(organisationId);
        access.requireIsTheCrewMember(assignment, callerCrewMemberId, "decline");

        // No capacity or schedule check - declining only ever gives a seat back. The mission lock
        // is still taken, so a decline and a close cannot interleave and leave the row in a state
        // neither of them intended.
        missionWindows.lockForUpdate(assignment.getMissionId(), organisationId);
        requireTransition(assignment, AssignmentStatus.DECLINED);

        assignment.decline(clock.instant());
        return respond(assignment);
    }

    /**
     * Withdrawing - FR-6, and the owning mission lead's alone under BR-9.
     *
     * <p>Reached through {@code forStaffingUpdate} rather than the bare window lock, because unlike
     * accept and decline this caller must satisfy the mission module's own access rules before this
     * module narrows them further. A director passes those and is refused here; a lead who owns
     * nothing never gets past them.
     *
     * <p>Withdrawing from a running mission does not send it backwards - BR-11. M11 is a
     * precondition of starting, not a standing invariant, so nothing here touches the mission's
     * status.
     */
    @Transactional
    AssignmentResponse withdraw(UUID assignmentId) {
        UUID organisationId = currentUser.organisationId();
        AssignmentEntity assignment = load(assignmentId, organisationId);

        MissionPlan mission = missionPlans.forStaffingUpdate(assignment.getMissionId());
        access.requireCanWithdraw(toWindowOwner(mission));
        requireTransition(assignment, AssignmentStatus.WITHDRAWN);

        assignment.withdraw(clock.instant());
        return respond(assignment);
    }

    private AssignmentEntity load(UUID assignmentId, UUID organisationId) {
        return assignments.findByIdAndOrganisationId(assignmentId, organisationId)
                .orElseThrow(AssignmentNotFoundException::assignment);
    }

    /**
     * The caller's crew profile, or 403.
     *
     * <p>A caller with no profile is not crew, so they cannot be the crew member named on any
     * assignment. Reported as the same forbidden answer a different crew member gets, because the
     * distinction between 'you are not this person' and 'you are not a crew member at all' is of no
     * use to the caller and of some use to anyone probing.
     */
    private UUID requireCrewProfile(UUID organisationId) {
        return crewIdentity.crewMemberIdOf(currentUser.userId(), organisationId)
                .orElseThrow(() -> AssignmentForbiddenException.notTheCrewMember("answer"));
    }

    /** Invariant A2, before an offer. */
    private void requireOpenSeat(UUID requirementId, int requiredCount, UUID organisationId) {
        long taken = assignments.countByRequirement(requirementId, organisationId, OCCUPYING_A_SEAT);
        if (taken >= requiredCount) {
            throw fullRequirement(requirementId, requiredCount, organisationId);
        }
    }

    /**
     * Invariant A2, before an acceptance.
     *
     * <p>The assignment being accepted is itself {@code OFFERED} and therefore already counted, so
     * the comparison is against {@code requiredCount} rather than one less than it - accepting
     * converts a seat rather than taking a new one. What this catches is the case where offers were
     * made, some were accepted, and the line filled up in between.
     */
    private void requireSeatStillOpen(AssignmentEntity assignment, UUID organisationId) {
        RequirementSeat seat = missionWindows
                .findRequirements(List.of(assignment.getCrewRequirementId()), organisationId)
                .get(assignment.getCrewRequirementId());
        if (seat == null) {
            // The requirement was deleted out from under an open offer. Nothing to accept.
            throw AssignmentNotFoundException.requirement();
        }

        long accepted = assignments.countByRequirement(
                seat.id(), organisationId, EnumSet.of(AssignmentStatus.ACCEPTED));
        if (accepted >= seat.requiredCount()) {
            throw fullRequirement(seat.id(), seat.requiredCount(), organisationId);
        }
    }

    /**
     * Invariants A3 and A4: the overlap is checked here, at acceptance, and never at offer time.
     *
     * <p>Two leads may legitimately offer the same person clashing dates and neither is wrong. This
     * is where that resolves, and only the first acceptance survives it.
     *
     * <p>A closed mission does not occupy the calendar - A8 - so an accepted place on an aborted
     * mission blocks nothing. That is what makes aborting a mission free its crew immediately.
     */
    private void requireNoScheduleConflict(AssignmentEntity assignment, MissionWindow mission,
                                           UUID crewMemberId, UUID organisationId) {
        List<UUID> otherMissionIds = assignments
                .crewAndMissionsFor(organisationId, List.of(crewMemberId), AssignmentStatus.ACCEPTED)
                .stream()
                .map(row -> (UUID) row[1])
                .filter(missionId -> !missionId.equals(assignment.getMissionId()))
                .distinct()
                .toList();

        missionWindows.findByIds(otherMissionIds, organisationId).values().stream()
                .filter(MissionWindow::occupiesCalendar)
                .filter(other -> other.overlaps(mission.startsAt(), mission.endsAt()))
                .findFirst()
                .ifPresent(conflict -> {
                    throw new ScheduleConflictException(conflict);
                });
    }

    /** Invariant A5, checked here so the common case reads as a sentence rather than a constraint. */
    private void requireNotAlreadyOnMission(UUID missionId, UUID crewMemberId, UUID organisationId) {
        if (assignments.crewOnMission(missionId, OCCUPYING_A_SEAT).contains(crewMemberId)) {
            throw new DuplicateAssignmentException();
        }
    }

    private void requireTransition(AssignmentEntity assignment, AssignmentStatus target) {
        if (!assignment.getStatus().canTransitionTo(target)) {
            throw InvalidAssignmentTransitionException.assignment(assignment.getStatus(), target);
        }
    }

    /** Builds the full error, counting both halves so the message can say which one is the problem. */
    private RequirementFullException fullRequirement(UUID requirementId, int requiredCount,
                                                     UUID organisationId) {
        int accepted = (int) assignments.countByRequirement(
                requirementId, organisationId, EnumSet.of(AssignmentStatus.ACCEPTED));
        int offered = (int) assignments.countByRequirement(
                requirementId, organisationId, EnumSet.of(AssignmentStatus.OFFERED));
        return new RequirementFullException(requirementId, requiredCount, accepted, offered);
    }

    private AssignmentResponse respond(AssignmentEntity assignment) {
        return AssignmentResponse.from(assignment,
                crewIdentity.nameFor(assignment.getCrewMemberId(), assignment.getOrganisationId()));
    }

    /**
     * A plan seen as a window, so one ownership rule serves both the locked reads.
     *
     * <p>Only the fields {@code AssignmentAccess} looks at are real here. Duplicating the ownership
     * comparison instead - once against a plan and once against a window - is exactly the drift
     * {@code MissionAccess} was created to stop.
     */
    private static MissionWindow toWindowOwner(MissionPlan mission) {
        return new MissionWindow(mission.id(), mission.organisationId(), "", mission.status(),
                mission.missionLeadId(), mission.startsAt(), mission.endsAt(), false);
    }

    private static Collection<AssignmentStatus> statuses(AssignmentStatus status) {
        return status == null ? ALL_STATUSES : EnumSet.of(status);
    }

    private static RequirementAssignmentsResponse toResponse(RequirementPlan requirement,
                                                             List<AssignmentEntity> rows,
                                                             Map<UUID, CrewMemberRef> names) {
        return new RequirementAssignmentsResponse(
                requirement.id(),
                requirement.title(),
                requirement.requiredCount(),
                requirement.acceptedCount(),
                requirement.offeredCount(),
                rows.stream()
                        .map(row -> AssignmentResponse.from(row, names.get(row.getCrewMemberId())))
                        .toList());
    }

    private static MyAssignmentResponse toMine(AssignmentEntity assignment, MissionWindow mission,
                                               RequirementSeat seat) {
        return new MyAssignmentResponse(
                assignment.getId(),
                assignment.getStatus(),
                assignment.getOfferedAt(),
                assignment.getRespondedAt(),
                new MissionRef(mission.id(), mission.name(), mission.status(),
                        mission.startsAt(), mission.endsAt()),
                // A requirement deleted while an offer stood leaves the assignment readable rather
                // than breaking the page. The offer is still real and still answerable.
                seat == null ? "Withdrawn requirement" : seat.title());
    }

    /**
     * The page slice, taken after filtering.
     *
     * <p>An index past the end yields an empty page rather than an error, which is how every other
     * paged endpoint here behaves and what a client asking for page 5 of 3 deserves.
     */
    private static MyAssignmentPage paged(List<MyAssignmentResponse> all, int page, int size) {
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        int totalPages = (int) Math.ceil((double) all.size() / size);
        return new MyAssignmentPage(all.subList(from, to), page, size, all.size(), totalPages);
    }
}
