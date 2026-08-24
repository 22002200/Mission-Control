package com.missioncontrol.mission.internal;

import com.missioncontrol.mission.api.StaffingReadModel;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What staffing looks like before the assignment module exists: nothing is filled and nobody is
 * assigned.
 *
 * <p>Not a bean. {@code MissionStaffing} resolves the real implementation through an
 * {@code ObjectProvider} and falls back to this, which is the same shape {@code SecurityConfig}
 * uses for its optional CORS source. A conditional bean would be the obvious alternative, but
 * {@code ConditionalOnMissingBean} is only reliable inside auto-configuration - in an ordinary
 * configuration class it depends on which definition Spring happens to register first, and a
 * feature-07 bean losing that race would fail silently and look like a data bug.
 *
 * <p>Answering zero is honest rather than convenient. It means a mission with requirements reads
 * as unstaffed and refuses to start, which is correct: there is no way to crew one yet.
 */
class UnstaffedReadModel implements StaffingReadModel {

    @Override
    public Map<UUID, Integer> acceptedCountsByRequirement(Collection<UUID> requirementIds) {
        return Map.of();
    }

    /**
     * Nothing has been offered either, which is what makes every seat on every requirement read as
     * open. Feature 06 then suggests a full crew for a mission before feature 07 exists, and that
     * is the right answer rather than a special case: with no assignment module there is nobody to
     * have offered anyone anything.
     */
    @Override
    public Map<UUID, Integer> offeredCountsByRequirement(Collection<UUID> requirementIds) {
        return Map.of();
    }

    @Override
    public Set<UUID> missionIdsAssignedTo(UUID crewUserId, UUID organisationId) {
        return Set.of();
    }
}
