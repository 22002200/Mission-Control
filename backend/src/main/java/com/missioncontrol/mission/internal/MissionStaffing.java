package com.missioncontrol.mission.internal;

import com.missioncontrol.mission.api.StaffingReadModel;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * The module's single point of contact with assignment data.
 *
 * <p>Wraps the optional {@link StaffingReadModel} so the rest of the module can ask for staffing
 * without every caller having to remember that there may not be an implementation yet. Resolving
 * through an {@code ObjectProvider} rather than injecting the interface directly is what makes the
 * dependency genuinely optional - the application starts with no assignment module at all.
 *
 * <p>The provider is consulted per call rather than cached in the constructor, so a bean
 * contributed later in the context lifecycle is picked up rather than missed.
 */
@Component
class MissionStaffing {

    private static final StaffingReadModel NONE = new UnstaffedReadModel();

    private final ObjectProvider<StaffingReadModel> readModel;

    MissionStaffing(ObjectProvider<StaffingReadModel> readModel) {
        this.readModel = readModel;
    }

    /**
     * Accepted counts for a set of requirements, with absent entries filled in as zero so callers
     * never have to reason about the difference between nobody accepted and nothing reported.
     */
    Map<UUID, Integer> acceptedCounts(Collection<UUID> requirementIds) {
        if (requirementIds.isEmpty()) {
            return Map.of();
        }
        return readModel.getIfAvailable(() -> NONE).acceptedCountsByRequirement(requirementIds);
    }

    Set<UUID> missionIdsAssignedTo(UUID crewUserId, UUID organisationId) {
        return readModel.getIfAvailable(() -> NONE).missionIdsAssignedTo(crewUserId, organisationId);
    }
}
