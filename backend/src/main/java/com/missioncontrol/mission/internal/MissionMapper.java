package com.missioncontrol.mission.internal;

import com.missioncontrol.identity.api.UserSummary;
import com.missioncontrol.skill.api.SkillSummary;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns entities plus the three things this module has to look up elsewhere - skill names, user
 * names and staffing counts - into responses.
 *
 * <p>Static, and takes everything it needs as arguments. That is what forces the callers to have
 * already resolved those lookups in bulk: a mapper holding a repository would make a per-row
 * query the easy thing to write, which is exactly the N+1 that NFR-1 rules out.
 *
 * <p>A missing name is rendered as a placeholder rather than throwing. Referential integrity
 * across a module boundary is not enforced by the database - that is the trade architecture.md
 * accepts for keeping the boundary - so a dangling id is possible, and a mission list that cannot
 * be read at all is a worse failure than one row reading as unknown.
 */
final class MissionMapper {

    private static final String UNKNOWN_USER = "Unknown user";
    private static final String UNKNOWN_SKILL = "Unknown skill";

    private MissionMapper() {
    }

    static MissionResponse toResponse(MissionEntity mission,
                                      Map<UUID, SkillSummary> skills,
                                      Map<UUID, UserSummary> users,
                                      Map<UUID, Integer> acceptedCounts) {

        List<CrewRequirementResponse> requirements = mission.getRequirements().stream()
                .sorted(Comparator.comparing(CrewRequirementEntity::getTitle,
                        String.CASE_INSENSITIVE_ORDER))
                .map(requirement -> toResponse(requirement, skills, acceptedCounts))
                .toList();

        return new MissionResponse(
                mission.getId(),
                mission.getName(),
                mission.getDescription(),
                mission.getStatus(),
                mission.getCloseReason(),
                mission.getCloseComment(),
                mission.getStartsAt(),
                mission.getEndsAt(),
                userRef(mission.getMissionLeadId(), users),
                isFullyStaffed(requirements),
                requirements);
    }

    static CrewRequirementResponse toResponse(CrewRequirementEntity requirement,
                                              Map<UUID, SkillSummary> skills,
                                              Map<UUID, Integer> acceptedCounts) {

        List<RequiredSkillResponse> required = requirement.getRequiredSkills().stream()
                .map(skill -> new RequiredSkillResponse(
                        skill.skillId(),
                        skillName(skill.skillId(), skills),
                        skill.getMinimumProficiency(),
                        skill.isMandatory(),
                        skill.getWeight()))
                .sorted(Comparator.comparing(RequiredSkillResponse::skillName,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new CrewRequirementResponse(
                requirement.getId(),
                requirement.getTitle(),
                requirement.getDescription(),
                requirement.getRequiredCount(),
                acceptedCounts.getOrDefault(requirement.getId(), 0),
                required);
    }

    static MissionSummaryResponse toSummary(MissionEntity mission,
                                            Map<UUID, UserSummary> users,
                                            int requiredCount,
                                            int acceptedCount) {
        return new MissionSummaryResponse(
                mission.getId(),
                mission.getName(),
                mission.getStatus(),
                mission.getCloseReason(),
                mission.getStartsAt(),
                mission.getEndsAt(),
                userRef(mission.getMissionLeadId(), users),
                acceptedCount,
                requiredCount,
                requiredCount > 0 && acceptedCount >= requiredCount);
    }

    /**
     * A mission with no requirements is not fully staffed.
     *
     * <p>An empty list is vacuously satisfied by any 'all of' test, which would report a blank
     * mission as ready to fly. Invariant M12 closes the same hole at the point of submission; this
     * keeps the flag itself from being misleading in the meantime.
     */
    private static boolean isFullyStaffed(List<CrewRequirementResponse> requirements) {
        return !requirements.isEmpty() && requirements.stream()
                .allMatch(requirement -> requirement.acceptedCount() >= requirement.requiredCount());
    }

    private static UserRef userRef(UUID userId, Map<UUID, UserSummary> users) {
        UserSummary user = users.get(userId);
        return new UserRef(userId, user == null ? UNKNOWN_USER : user.fullName());
    }

    private static String skillName(UUID skillId, Map<UUID, SkillSummary> skills) {
        SkillSummary skill = skills.get(skillId);
        return skill == null ? UNKNOWN_SKILL : skill.name();
    }
}
