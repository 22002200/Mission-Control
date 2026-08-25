package com.missioncontrol.assignment.internal;

import com.missioncontrol.matching.api.CrewLoadReadModel;
import com.missioncontrol.mission.api.MissionWindow;
import com.missioncontrol.mission.api.MissionWindows;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The real answer to {@code matching}'s questions about availability and load.
 *
 * <p>Replaces {@code UnassignedCrewLoad}, whose documentation was careful to say that answering
 * nothing was simply true rather than provisional: with no assignments there was genuinely nobody
 * to exclude and no load to penalise. Now there is, so feature 06's experience and load terms stop
 * being zero and its rankings change. The no-op remains as the {@code ObjectProvider} fallback.
 *
 * <p><strong>Every method here is the same two-step.</strong> Query this module's own rows for the
 * crew-and-mission pairs, then ask {@code mission} in one bulk call which of those missions matter,
 * then count or collect in memory. Two queries per method, never one per candidate, which is
 * feature 06's NFR-2 stated as an outright prohibition.
 *
 * <p>It has to be that way round rather than a join, and that is the interesting constraint. The
 * predicates are all about mission dates and mission status - overlapping the window, not closed,
 * closed as completed, starting since a cutoff - and those columns belong to another module's
 * tables. A SQL join would be quicker and would also be the boundary violation the whole
 * architecture is arranged to prevent, and one no test could catch: {@code ModularityTests}
 * analyses bytecode, and a table name in a query string is invisible to it. The cost is loading
 * assignment rows that the mission filter then discards, bounded by the size of the organisation's
 * assignment history.
 */
@Component
class AssignmentCrewLoad implements CrewLoadReadModel {

    private static final Set<AssignmentStatus> NON_TERMINAL =
            EnumSet.of(AssignmentStatus.OFFERED, AssignmentStatus.ACCEPTED);

    private final AssignmentRepository assignments;
    private final MissionWindows missions;

    AssignmentCrewLoad(AssignmentRepository assignments, MissionWindows missions) {
        this.assignments = assignments;
        this.missions = missions;
    }

    /**
     * Crew already committed over the mission's dates - invariant A3.
     *
     * <p>Accepted only. An offer reserves nobody, per A4, so somebody holding two clashing offers
     * is still a legitimate candidate for a third - the clash is settled when one is accepted.
     *
     * <p>Closed missions do not occupy the calendar - A8 - which is what frees a crew member the
     * moment an aborted mission is closed rather than leaving them booked for dates on which
     * nothing will now happen.
     */
    @Override
    @Transactional(readOnly = true)
    public Set<UUID> crewUnavailableBetween(UUID organisationId, Instant startsAt, Instant endsAt) {
        List<Object[]> accepted = assignments.crewAndMissionsByStatus(
                organisationId, AssignmentStatus.ACCEPTED);
        return crewMatching(accepted, organisationId,
                mission -> mission.occupiesCalendar() && mission.overlaps(startsAt, endsAt));
    }

    /**
     * Crew who already hold an open place on this mission - invariant A5.
     *
     * <p>The one method here that needs no mission lookup: the mission is the parameter, so its
     * dates and status are nobody's business. Declined and withdrawn do not count, because someone
     * who turned a place down is not barred from being asked again once the plan changes.
     */
    @Override
    @Transactional(readOnly = true)
    public Set<UUID> crewAlreadyOnMission(UUID missionId) {
        return Set.copyOf(assignments.crewOnMission(missionId, NON_TERMINAL));
    }

    /**
     * How many missions each crew member has actually seen through.
     *
     * <p>Accepted assignments on missions closed as {@code COMPLETED} - the data model's definition
     * of assignment history, and the reason {@code MissionWindow} carries a {@code completed}
     * boolean at all. An aborted mission does not count: being assigned to something that never ran
     * is not experience of it.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Integer> completedMissionCounts(UUID organisationId,
                                                     Collection<UUID> crewMemberIds) {
        return countPerCrewMember(organisationId, crewMemberIds, MissionWindow::completed);
    }

    /**
     * How much each crew member is carrying right now.
     *
     * <p>Accepted assignments on missions starting on or after the cutoff. That single comparison
     * covers both halves of feature 06's rule - within the recent window, or in the future -
     * because anything ahead of now is necessarily ahead of a cutoff behind it.
     *
     * <p>The cutoff is the caller's, derived from the organisation's own median mission length, so
     * a body running three-day sorties and one running six-month expeditions are measured on their
     * own timescales. Nothing here second-guesses it.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Integer> recentAssignmentCounts(UUID organisationId,
                                                     Collection<UUID> crewMemberIds,
                                                     Instant since) {
        return countPerCrewMember(organisationId, crewMemberIds,
                mission -> !mission.startsAt().isBefore(since));
    }

    /** Crew whose accepted missions pass the filter, as a set. */
    private Set<UUID> crewMatching(List<Object[]> pairs, UUID organisationId,
                                   Predicate<MissionWindow> wanted) {
        Map<UUID, MissionWindow> windows = windowsFor(pairs, organisationId);
        Set<UUID> crew = new HashSet<>();
        for (Object[] pair : pairs) {
            MissionWindow mission = windows.get((UUID) pair[1]);
            if (mission != null && wanted.test(mission)) {
                crew.add((UUID) pair[0]);
            }
        }
        return Set.copyOf(crew);
    }

    /** Accepted missions passing the filter, counted per crew member; absent means zero. */
    private Map<UUID, Integer> countPerCrewMember(UUID organisationId,
                                                  Collection<UUID> crewMemberIds,
                                                  Predicate<MissionWindow> wanted) {
        if (crewMemberIds.isEmpty()) {
            return Map.of();
        }

        List<Object[]> pairs = assignments.crewAndMissionsFor(
                organisationId, crewMemberIds, AssignmentStatus.ACCEPTED);
        Map<UUID, MissionWindow> windows = windowsFor(pairs, organisationId);

        Map<UUID, Integer> counts = new HashMap<>();
        for (Object[] pair : pairs) {
            MissionWindow mission = windows.get((UUID) pair[1]);
            if (mission != null && wanted.test(mission)) {
                counts.merge((UUID) pair[0], 1, Integer::sum);
            }
        }
        return Map.copyOf(counts);
    }

    /** The one bulk mission lookup each method above is allowed. */
    private Map<UUID, MissionWindow> windowsFor(List<Object[]> pairs, UUID organisationId) {
        return missions.findByIds(
                pairs.stream().map(pair -> (UUID) pair[1]).distinct().toList(), organisationId);
    }
}
