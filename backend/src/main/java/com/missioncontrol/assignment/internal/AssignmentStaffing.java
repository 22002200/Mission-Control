package com.missioncontrol.assignment.internal;

import com.missioncontrol.crew.api.CrewDirectory;
import com.missioncontrol.mission.api.StaffingReadModel;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The real answer to {@code mission}'s staffing questions, replacing {@code UnstaffedReadModel}.
 *
 * <p>Registering this bean is what makes invariant M11 satisfiable: until it existed, every mission
 * read as unstaffed and {@code POST /start} was always refused, which the mission module documented
 * as honest rather than broken. The no-op stays in place as the fallback, because
 * {@code MissionStaffing} resolves through an {@code ObjectProvider} precisely so the application
 * still starts if this module is ever removed.
 *
 * <p>Nothing here needs mission data, which is why it is three plain queries over this module's own
 * table. That is not true of {@link AssignmentCrewLoad} next door, and the difference is worth
 * noticing: counting places filled is an assignment fact, while deciding who is free over a date
 * range is an assignment fact crossed with a mission fact.
 *
 * <p>Absent rather than zero for a requirement nobody holds an assignment against - the contract
 * {@code StaffingReadModel} states, and what lets a grouped count answer without a row per
 * requirement asked about.
 */
@Component
class AssignmentStaffing implements StaffingReadModel {

    private final AssignmentRepository assignments;
    private final CrewDirectory crew;

    AssignmentStaffing(AssignmentRepository assignments, CrewDirectory crew) {
        this.assignments = assignments;
        this.crew = crew;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Integer> acceptedCountsByRequirement(Collection<UUID> requirementIds) {
        return countsIn(requirementIds, AssignmentStatus.ACCEPTED);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Integer> offeredCountsByRequirement(Collection<UUID> requirementIds) {
        return countsIn(requirementIds, AssignmentStatus.OFFERED);
    }

    /**
     * Which missions a crew member may see - the whole of a crew member's mission visibility.
     *
     * <p>Takes the <strong>account</strong> id, because that is what the mission module has in
     * hand from the security context, and this module stores the crew profile id. Crossing between
     * them costs one lookup, which is why the parameter is not simply changed to a crew member id:
     * doing that would push the crossing into {@code mission}, and {@code mission} has no business
     * knowing that a crew profile is a separate thing from a user.
     *
     * <p>Offers count, not only acceptances. Being asked is reason enough to see what you are being
     * asked to join - without that, an offer would be invisible on the very screen it has to be
     * answered from. Declined and withdrawn count too: a mission you turned down is one you may
     * still want to look back at, and hiding it the instant you decline would make the list flicker
     * rather than settle.
     *
     * <p>A user with no crew profile sees nothing, which is correct rather than defensive - a
     * director or a lead reaches this only if their role check has already let them through, and
     * their visibility comes from owning missions instead.
     */
    @Override
    @Transactional(readOnly = true)
    public Set<UUID> missionIdsAssignedTo(UUID crewUserId, UUID organisationId) {
        Optional<UUID> crewMemberId = crew.crewMemberIdOf(crewUserId, organisationId);
        return crewMemberId
                .map(id -> Set.copyOf(assignments.missionIdsFor(id, organisationId)))
                .orElseGet(Set::of);
    }

    private Map<UUID, Integer> countsIn(Collection<UUID> requirementIds, AssignmentStatus status) {
        if (requirementIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Integer> counts = new HashMap<>();
        assignments.countsByRequirement(requirementIds, status)
                .forEach(row -> counts.put((UUID) row[0], ((Number) row[1]).intValue()));
        return Map.copyOf(counts);
    }
}
