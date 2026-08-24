package com.missioncontrol.mission.internal;

import com.missioncontrol.identity.api.UserDirectory;
import com.missioncontrol.identity.api.UserSummary;
import com.missioncontrol.platform.CurrentUser;
import com.missioncontrol.skill.api.SkillCatalogue;
import com.missioncontrol.skill.api.SkillSummary;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Turning missions into responses, having first resolved everything they need from elsewhere.
 *
 * <p>Lifted out of {@code MissionService} when feature 05 arrived: submit, approve, reject and
 * replan all answer with the mission detail, and a second copy of this assembly is exactly how
 * NFR-1 gets broken by accident - the per-row lookup is always the easy thing to write.
 *
 * <p>Three bulk lookups per page or per mission, never per row: skill names from {@code skill},
 * lead names from {@code identity}, and staffing from whatever implements the assignment read
 * model. That is the whole reason {@link MissionMapper} is static and takes maps - it cannot
 * quietly query.
 */
@Component
class MissionDetailAssembler {

    private final MissionRepository missions;
    private final MissionStaffing staffing;
    private final SkillCatalogue skills;
    private final UserDirectory users;
    private final CurrentUser currentUser;

    MissionDetailAssembler(MissionRepository missions,
                           MissionStaffing staffing,
                           SkillCatalogue skills,
                           UserDirectory users,
                           CurrentUser currentUser) {
        this.missions = missions;
        this.staffing = staffing;
        this.skills = skills;
        this.users = users;
        this.currentUser = currentUser;
    }

    /** One mission, with its requirements, their skill names and their staffing counts. */
    MissionResponse detail(MissionEntity mission) {
        List<UUID> requirementIds = mission.getRequirements().stream()
                .map(CrewRequirementEntity::getId)
                .toList();

        List<UUID> skillIds = mission.getRequirements().stream()
                .flatMap(requirement -> requirement.getRequiredSkills().stream())
                .map(RequiredSkillEntity::skillId)
                .distinct()
                .toList();

        Map<UUID, SkillSummary> skillNames =
                skills.findByIds(skillIds, mission.getOrganisationId());
        Map<UUID, UserSummary> leadNames =
                users.findByIds(List.of(mission.getMissionLeadId()), mission.getOrganisationId());

        return MissionMapper.toResponse(mission, skillNames, leadNames,
                staffing.acceptedCounts(requirementIds));
    }

    /** One page of summaries. */
    MissionPage page(Page<MissionEntity> found) {
        return MissionPage.from(found.map(summariser(found.getContent())));
    }

    /**
     * Builds the summariser for one page, having already resolved every lookup that page needs.
     *
     * <p>Three queries for the whole page rather than three per row: the requirement totals, the
     * staffing counts keyed on those requirements, and the lead names. That is NFR-1, and it is
     * why this returns a function instead of being called per mission.
     */
    private Function<MissionEntity, MissionSummaryResponse> summariser(List<MissionEntity> page) {
        List<UUID> missionIds = page.stream().map(MissionEntity::getId).toList();
        if (missionIds.isEmpty()) {
            return mission -> MissionMapper.toSummary(mission, Map.of(), 0, 0);
        }

        List<RequirementTotals> totals = missions.findRequirementTotals(missionIds);
        Map<UUID, Integer> accepted = staffing.acceptedCounts(
                totals.stream().map(RequirementTotals::requirementId).toList());

        Map<UUID, Integer> requiredByMission = totals.stream().collect(Collectors.groupingBy(
                RequirementTotals::missionId,
                Collectors.summingInt(RequirementTotals::requiredCount)));

        // Each line contributes at most what it asked for, so an over-filled requirement cannot
        // mask a short one in the mission total. Two lines of two, with four acceptances all on
        // the first, must not read as fully staffed.
        Map<UUID, Integer> acceptedByMission = totals.stream().collect(Collectors.groupingBy(
                RequirementTotals::missionId,
                Collectors.summingInt(total -> Math.min(
                        accepted.getOrDefault(total.requirementId(), 0), total.requiredCount()))));

        Map<UUID, UserSummary> leads = users.findByIds(
                page.stream().map(MissionEntity::getMissionLeadId).distinct().toList(),
                currentUser.organisationId());

        return mission -> MissionMapper.toSummary(
                mission,
                leads,
                requiredByMission.getOrDefault(mission.getId(), 0),
                acceptedByMission.getOrDefault(mission.getId(), 0));
    }

    /** One requirement on its own, for the requirement endpoints. */
    CrewRequirementResponse requirement(CrewRequirementEntity requirement, UUID organisationId) {
        List<UUID> skillIds = requirement.getRequiredSkills().stream()
                .map(RequiredSkillEntity::skillId)
                .toList();

        return MissionMapper.toResponse(
                requirement,
                skills.findByIds(skillIds, organisationId),
                staffing.acceptedCounts(List.of(requirement.getId())));
    }

    /**
     * A mission's approval history, with every name resolved in one lookup.
     *
     * <p>Both roles resolved in the same call, and de-duplicated first, so a lead who submitted
     * five cycles is looked up once. {@code UserDirectory} has no single-id method precisely so
     * that the N+1 version of this is not the easy one to write.
     *
     * <p>The order is the query's - see {@code MissionApprovalRepository.findHistory} - and is not
     * re-imposed here. Sorting again would be a second statement of the same rule, and the kind
     * that stops agreeing with the first.
     */
    List<MissionApprovalResponse> approvals(List<MissionApprovalEntity> cycles,
                                            UUID organisationId) {
        List<UUID> userIds = cycles.stream()
                .flatMap(cycle -> Stream.of(cycle.getSubmittedBy(), cycle.getDecidedBy()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, UserSummary> names = users.findByIds(userIds, organisationId);
        return cycles.stream().map(cycle -> MissionMapper.toResponse(cycle, names)).toList();
    }
}
