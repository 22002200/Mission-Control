package com.missioncontrol.mission.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A mission and the crew it calls for, flattened for a module that has to reason about staffing it.
 *
 * <p>A record rather than the entity. {@code MissionEntity} is internal and would drag a
 * transaction boundary and a lazy collection into code that has no idea it is holding one -
 * architecture.md's first rule about module boundaries.
 *
 * <p>The window is here because it is a hard filter, not decoration: a candidate is only available
 * if they hold no accepted assignment overlapping {@code startsAt} to {@code endsAt} - invariant
 * A3.
 *
 * <p>{@code status} and {@code missionLeadId} are here for feature 07 rather than for matching,
 * which reads neither. Offering a place is refused unless the mission is {@code APPROVED} - A1 -
 * and is the owning lead's alone rather than any director's - BR-9 - and both are questions
 * {@code assignment} has to answer about a mission it has just been handed. Making it ask twice
 * would be a second query for two fields the first one already had in hand.
 *
 * @param id             the mission
 * @param organisationId the tenant everything about this read was scoped to
 * @param status         where the mission is in its lifecycle
 * @param missionLeadId  the owning user, always a {@code MISSION_LEAD} in this organisation - M2
 * @param startsAt       inclusive start of the mission window, UTC
 * @param endsAt         end of the mission window, UTC, always after {@code startsAt} - M1
 * @param requirements   every staffing line, in the order the mission presents them
 */
public record MissionPlan(UUID id, UUID organisationId, MissionStatus status, UUID missionLeadId,
                          Instant startsAt, Instant endsAt, List<RequirementPlan> requirements) {

    /**
     * One requirement by id, if it is on this mission.
     *
     * <p>Empty covers both a requirement that does not exist and one that exists on a different
     * mission, and callers are expected to answer 404 for either. Telling them apart would let a
     * caller confirm that a requirement id is real by pairing it with a mission they can see,
     * which is the same leak the mission 404 exists to prevent.
     */
    public Optional<RequirementPlan> requirement(UUID requirementId) {
        return requirements.stream()
                .filter(requirement -> requirement.id().equals(requirementId))
                .findFirst();
    }

    /** Offers may only be made while the mission is {@code APPROVED} - invariant A1. */
    public boolean acceptsOffers() {
        return status == MissionStatus.APPROVED;
    }

    public boolean isOwnedBy(UUID userId) {
        return missionLeadId.equals(userId);
    }
}
