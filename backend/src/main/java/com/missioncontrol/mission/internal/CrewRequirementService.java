package com.missioncontrol.mission.internal;

import com.missioncontrol.platform.CurrentUser;
import com.missioncontrol.skill.api.SkillCatalogue;
import com.missioncontrol.skill.api.SkillSummary;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Describing the crew a mission needs.
 *
 * <p>Separate from {@link MissionService} because the two answer different questions and have
 * different rules: a mission can be changed by its lead or by a director and in almost any state,
 * while its requirements can only be changed by the lead and only while the mission is still in
 * {@code PLAN}. Keeping them apart means neither set of guards can be applied to the wrong thing
 * by accident.
 *
 * <p>Skills arrive inline on the request - FR-8 - so an update replaces the requirement whole
 * rather than merging. There is no sensible partial update of a set: removing a skill would need
 * a second syntax invented for it.
 */
@Service
class CrewRequirementService {

    private final MissionRepository missions;
    private final CrewRequirementRepository requirements;
    private final MissionAccess access;
    private final MissionStaffing staffing;
    private final SkillCatalogue skills;
    private final CurrentUser currentUser;
    private final Clock clock;

    CrewRequirementService(MissionRepository missions,
                           CrewRequirementRepository requirements,
                           MissionAccess access,
                           MissionStaffing staffing,
                           SkillCatalogue skills,
                           CurrentUser currentUser,
                           Clock clock) {
        this.missions = missions;
        this.requirements = requirements;
        this.access = access;
        this.staffing = staffing;
        this.skills = skills;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional
    CrewRequirementResponse add(UUID missionId, CrewRequirementRequest request) {
        MissionEntity mission = requireEditableMission(missionId);

        CrewRequirementEntity requirement = CrewRequirementEntity.builder()
                .id(UUID.randomUUID())
                .organisationId(mission.getOrganisationId())
                .title(request.title().strip())
                .requiredCount(request.requiredCount())
                .build();

        requirement.replaceWith(
                request.title().strip(),
                request.description(),
                request.requiredCount(),
                buildSkills(request, mission.getOrganisationId()));

        mission.addRequirement(requirement, clock.instant());
        missions.save(mission);

        return toResponse(requirement, mission.getOrganisationId());
    }

    @Transactional
    CrewRequirementResponse update(UUID missionId, UUID requirementId,
                                   CrewRequirementRequest request) {
        MissionEntity mission = requireEditableMission(missionId);
        CrewRequirementEntity requirement = requireRequirement(mission, requirementId);

        requirement.replaceWith(
                request.title().strip(),
                request.description(),
                request.requiredCount(),
                buildSkills(request, mission.getOrganisationId()));
        mission.touch(clock.instant());

        return toResponse(requirement, mission.getOrganisationId());
    }

    @Transactional
    void delete(UUID missionId, UUID requirementId) {
        MissionEntity mission = requireEditableMission(missionId);
        CrewRequirementEntity requirement = requireRequirement(mission, requirementId);

        mission.removeRequirement(requirement, clock.instant());
    }

    /**
     * The checks every write here shares, in the order their answers may safely be revealed.
     *
     * <p>Visibility first, then ownership, then state. The order is the whole point: a mission
     * lead who does not own this mission cannot see it either, so they must get the same 404 that
     * {@code GET} gives them. Checking ownership first would answer 403 and confirm the mission
     * exists - and the two endpoints disagreeing about that is worse than either answer alone.
     *
     * <p>Asking about state before ownership would leak in the same way, one step further in.
     */
    private MissionEntity requireEditableMission(UUID missionId) {
        MissionEntity mission = missions
                .findDetailByIdAndOrganisationId(missionId, currentUser.organisationId())
                .orElseThrow(MissionNotFoundException::new);

        access.requireVisible(mission);
        access.requireIsOwner(mission);

        // BR-10. Changing what crew a mission needs after it has been approved would invalidate
        // the approval, and unlike a detail edit there is no resubmission path defined for it, so
        // the write is refused rather than quietly sending the mission back to planning.
        if (mission.getStatus() != MissionStatus.PLAN) {
            throw new MissionNotEditableException(mission.getStatus());
        }
        return mission;
    }

    /**
     * Finds the requirement on this mission, having already loaded the mission with its
     * requirements attached, so this costs no extra query.
     */
    private CrewRequirementEntity requireRequirement(MissionEntity mission, UUID requirementId) {
        return mission.getRequirements().stream()
                .filter(requirement -> requirement.getId().equals(requirementId))
                .findFirst()
                .orElseThrow(RequirementNotFoundException::new);
    }

    /**
     * Validates the skills a requirement names, and turns them into rows.
     *
     * <p>Duplicates are caught here rather than left to the composite primary key, so the caller
     * gets a readable 409 instead of a constraint violation surfacing as a 500. The key is still
     * the real guarantee.
     *
     * <p>Unknown, retired and other-tenant skills all answer the same way. One bulk lookup covers
     * every skill on the requirement, because a lookup per skill is the N+1 that NFR-1 rules out.
     */
    private List<RequiredSkillEntity> buildSkills(CrewRequirementRequest request,
                                                  UUID organisationId) {
        List<RequiredSkillRequest> requested = request.skillsOrEmpty();

        Set<UUID> distinct = new HashSet<>();
        requested.forEach(skill -> {
            if (!distinct.add(skill.skillId())) {
                throw new DuplicateSkillException();
            }
        });

        Map<UUID, SkillSummary> found = skills.findByIds(distinct, organisationId);
        boolean allUsable = distinct.stream()
                .map(found::get)
                .allMatch(skill -> skill != null && skill.active());
        if (!allUsable) {
            throw new InvalidSkillException();
        }

        return requested.stream()
                .map(skill -> RequiredSkillEntity.builder()
                        // The requirement half of the key is filled in on attach, once the owning
                        // requirement is known.
                        .id(new RequiredSkillId(null, skill.skillId()))
                        // Narrowed to match the SMALLINT column; the request already bounds
                        // it to 1-5, so nothing can be lost here.
                        .minimumProficiency(skill.minimumProficiency().shortValue())
                        .mandatory(skill.mandatory())
                        .weight(skill.weightOrDefault())
                        .build())
                .toList();
    }

    private CrewRequirementResponse toResponse(CrewRequirementEntity requirement,
                                               UUID organisationId) {
        List<UUID> skillIds = requirement.getRequiredSkills().stream()
                .map(RequiredSkillEntity::skillId)
                .toList();

        return MissionMapper.toResponse(
                requirement,
                skills.findByIds(skillIds, organisationId),
                staffing.acceptedCounts(List.of(requirement.getId())));
    }
}
