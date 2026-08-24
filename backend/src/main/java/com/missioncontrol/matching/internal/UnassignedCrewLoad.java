package com.missioncontrol.matching.internal;

import com.missioncontrol.matching.api.CrewLoadReadModel;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What assignment data looks like before the assignment module exists: there is none.
 *
 * <p>Nobody is unavailable, nobody is already on the mission, and every count is zero. Unlike
 * {@code UnstaffedReadModel} on the mission side - which makes a mission read as un-startable and
 * is therefore a deliberate refusal - answering nothing here is simply the truth. With no
 * assignments in the system there is nobody to exclude and no load to penalise, so matching ranks
 * on skills and availability and the ranking is correct rather than provisional.
 *
 * <p>Not a bean. {@link CrewLoad} resolves the real implementation through an
 * {@code ObjectProvider} and falls back to this, the same shape {@code MissionStaffing} uses. A
 * conditional bean would be the obvious alternative, but {@code ConditionalOnMissingBean} is only
 * reliable inside auto-configuration - in an ordinary configuration class it depends on which
 * definition Spring happens to register first, and a feature-07 bean losing that race would fail
 * silently and look like a data bug.
 */
class UnassignedCrewLoad implements CrewLoadReadModel {

    @Override
    public Set<UUID> crewUnavailableBetween(UUID organisationId, Instant startsAt, Instant endsAt) {
        return Set.of();
    }

    @Override
    public Set<UUID> crewAlreadyOnMission(UUID missionId) {
        return Set.of();
    }

    @Override
    public Map<UUID, Integer> completedMissionCounts(UUID organisationId,
                                                     Collection<UUID> crewMemberIds) {
        return Map.of();
    }

    @Override
    public Map<UUID, Integer> recentAssignmentCounts(UUID organisationId,
                                                     Collection<UUID> crewMemberIds,
                                                     Instant since) {
        return Map.of();
    }
}
