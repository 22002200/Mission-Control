package com.missioncontrol.mission.internal;

import com.missioncontrol.mission.api.MissionStatus;
import com.missioncontrol.mission.api.MissionWindow;
import com.missioncontrol.mission.api.MissionWindows;
import com.missioncontrol.mission.api.RequirementSeat;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The published window lookup: missions and requirement capacity, without asking who is calling.
 *
 * <p>A separate bean from {@link MissionPlanLookup} because the two have different contracts and
 * putting both on one class would make it very easy to call the wrong one. That one reads
 * {@code CurrentUser} and refuses callers; this one takes an organisation and refuses nobody. The
 * reasoning for that is on {@link MissionWindows} itself.
 *
 * <p>Empty inputs never reach the database. A crew member with no assignments should not cost a
 * query to establish that they are also on no missions, and the same shape is already used by
 * {@code MissionStaffing} and {@code CrewLoad}.
 *
 * <p>{@code Transactional(readOnly = true)} on the reads matters more than it looks:
 * {@code open-in-view} is off, so everything handed back has to be materialised before the method
 * returns. Mapping to records here rather than returning entities is what guarantees that, and it
 * is the same rule architecture.md states as entities never leaving a module.
 */
@Component
class MissionWindowLookup implements MissionWindows {

    private final MissionRepository missions;

    MissionWindowLookup(MissionRepository missions) {
        this.missions = missions;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, MissionWindow> findByIds(Collection<UUID> missionIds, UUID organisationId) {
        if (missionIds.isEmpty()) {
            return Map.of();
        }
        return missions.findWindows(missionIds, organisationId).stream()
                .map(MissionWindowLookup::toWindow)
                .collect(Collectors.toMap(MissionWindow::id, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, RequirementSeat> findRequirements(Collection<UUID> requirementIds,
                                                       UUID organisationId) {
        if (requirementIds.isEmpty()) {
            return Map.of();
        }
        return missions.findRequirementSeats(requirementIds, organisationId).stream()
                .collect(Collectors.toMap(RequirementSeat::id, Function.identity()));
    }

    /**
     * Locks the mission row, then maps it.
     *
     * <p>One query, unlike {@code MissionLoader.visibleForUpdate}, which needs a second to attach
     * the requirements PostgreSQL will not let it fetch-join under {@code for update}. Nothing here
     * wants the requirements, so the lock query is also the read.
     *
     * <p>Answers the module's own {@link MissionNotFoundException} for an absent or another
     * tenant's mission, so a caller in another module gets the same 404 body as one asking a
     * mission endpoint directly.
     */
    @Override
    @Transactional
    public MissionWindow lockForUpdate(UUID missionId, UUID organisationId) {
        return toWindow(missions.lockByIdAndOrganisationId(missionId, organisationId)
                .orElseThrow(MissionNotFoundException::new));
    }

    private static MissionWindow toWindow(MissionEntity mission) {
        return new MissionWindow(
                mission.getId(),
                mission.getOrganisationId(),
                mission.getName(),
                mission.getStatus(),
                mission.getMissionLeadId(),
                mission.getStartsAt(),
                mission.getEndsAt(),
                // Both halves, not just the reason. A close reason is only meaningful once the
                // mission is closed - invariant M4 - and reading it alone would count a mission
                // still running as history the moment anyone set one by mistake.
                mission.getStatus() == MissionStatus.CLOSED
                        && mission.getCloseReason() == MissionCloseReason.COMPLETED);
    }
}
