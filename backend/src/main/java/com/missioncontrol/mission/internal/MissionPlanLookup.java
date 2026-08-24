package com.missioncontrol.mission.internal;

import com.missioncontrol.mission.api.MissionPlan;
import com.missioncontrol.mission.api.MissionPlans;
import com.missioncontrol.mission.api.RequiredSkillSpec;
import com.missioncontrol.mission.api.RequirementPlan;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The published staffing view, assembled from beans that already exist.
 *
 * <p>Nothing here decides anything new. {@link MissionLoader} performs the tenant-scoped read and
 * the visibility check, {@link MissionAccess} narrows that to the owning lead or a director, and
 * {@link MissionStaffing} supplies the counts. Reimplementing any of the three would put a second
 * opinion next to the first, and {@code MissionAccess} exists precisely because that happened once
 * already - a lead reading another lead's mission got 404 while adding a requirement to it got
 * 403, which told them the mission was real.
 *
 * <p>{@code visibleDetail} rather than {@code visibleForUpdate}: this is a read, so it takes no
 * row lock. The lock exists to serialise status transitions and taking one for a suggestion would
 * make matching block every command on the mission it is matching for.
 *
 * <p>Two calls to {@code MissionStaffing} rather than one combined figure. Accepted and offered
 * mean different things - one is what M11 measures, the other is half of what A2 caps - and the
 * caller needs both separately to explain a full requirement to a mission lead.
 */
@Component
class MissionPlanLookup implements MissionPlans {

    private final MissionLoader missions;
    private final MissionAccess access;
    private final MissionStaffing staffing;

    MissionPlanLookup(MissionLoader missions, MissionAccess access, MissionStaffing staffing) {
        this.missions = missions;
        this.access = access;
        this.staffing = staffing;
    }

    @Override
    @Transactional(readOnly = true)
    public MissionPlan forStaffing(UUID missionId) {
        MissionEntity mission = missions.visibleDetail(missionId);
        access.requireCanModify(mission);

        List<CrewRequirementEntity> requirements = List.copyOf(mission.getRequirements());
        List<UUID> requirementIds = requirements.stream().map(CrewRequirementEntity::getId).toList();

        Map<UUID, Integer> accepted = staffing.acceptedCounts(requirementIds);
        Map<UUID, Integer> offered = staffing.offeredCounts(requirementIds);

        return new MissionPlan(
                mission.getId(),
                mission.getOrganisationId(),
                mission.getStartsAt(),
                mission.getEndsAt(),
                requirements.stream()
                        .map(requirement -> toPlan(requirement, accepted, offered))
                        .toList());
    }

    private static RequirementPlan toPlan(CrewRequirementEntity requirement,
                                          Map<UUID, Integer> accepted,
                                          Map<UUID, Integer> offered) {
        return new RequirementPlan(
                requirement.getId(),
                requirement.getTitle(),
                requirement.getRequiredCount(),
                accepted.getOrDefault(requirement.getId(), 0),
                offered.getOrDefault(requirement.getId(), 0),
                requirement.getRequiredSkills().stream()
                        .map(skill -> new RequiredSkillSpec(
                                skill.skillId(),
                                skill.getMinimumProficiency(),
                                skill.isMandatory(),
                                skill.getWeight()))
                        // Sorted by skill id so the same requirement always presents its skills in
                        // the same order. The entity holds them in a set, whose iteration order
                        // Hibernate does not promise, and feature 06's NFR-1 wants two identical
                        // requests to come back identical down to the response body.
                        .sorted((left, right) -> left.skillId().compareTo(right.skillId()))
                        .toList());
    }
}
