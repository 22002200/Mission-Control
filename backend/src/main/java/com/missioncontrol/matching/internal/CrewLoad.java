package com.missioncontrol.matching.internal;

import com.missioncontrol.matching.api.CrewLoadReadModel;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * This module's single point of contact with assignment data.
 *
 * <p>Wraps the optional {@link CrewLoadReadModel} so the engine can ask about availability and load
 * without every call site having to remember that there may be no implementation yet. Resolving
 * through an {@code ObjectProvider} rather than injecting the interface is what makes the
 * dependency genuinely optional - the application starts with no assignment module at all.
 *
 * <p>The provider is consulted per call rather than cached in the constructor, so a bean
 * contributed later in the context lifecycle is picked up rather than missed. That mirrors
 * {@code MissionStaffing}, which made the same choice for the same reason.
 *
 * <p>Empty inputs never reach the read model. An organisation with no crew at all should not cost
 * a query to establish that it also has no assignments.
 */
@Component
class CrewLoad {

    private static final CrewLoadReadModel NONE = new UnassignedCrewLoad();

    private final ObjectProvider<CrewLoadReadModel> readModel;

    CrewLoad(ObjectProvider<CrewLoadReadModel> readModel) {
        this.readModel = readModel;
    }

    Set<UUID> unavailableBetween(UUID organisationId, Instant startsAt, Instant endsAt) {
        return current().crewUnavailableBetween(organisationId, startsAt, endsAt);
    }

    Set<UUID> alreadyOnMission(UUID missionId) {
        return current().crewAlreadyOnMission(missionId);
    }

    Map<UUID, Integer> completedMissionCounts(UUID organisationId, Collection<UUID> crewMemberIds) {
        if (crewMemberIds.isEmpty()) {
            return Map.of();
        }
        return current().completedMissionCounts(organisationId, crewMemberIds);
    }

    Map<UUID, Integer> recentAssignmentCounts(UUID organisationId, Collection<UUID> crewMemberIds,
                                              Instant since) {
        if (crewMemberIds.isEmpty()) {
            return Map.of();
        }
        return current().recentAssignmentCounts(organisationId, crewMemberIds, since);
    }

    private CrewLoadReadModel current() {
        return readModel.getIfAvailable(() -> NONE);
    }
}
