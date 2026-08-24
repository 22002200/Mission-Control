package com.missioncontrol.crew.api;

import java.util.Map;
import java.util.UUID;

/**
 * One crew member as another module sees them: an identity, and what they are rated at.
 *
 * <p>No name. Names belong to {@code identity}, and this record carries {@code userId} so a caller
 * can resolve one in bulk through {@code UserDirectory} rather than making this module depend on a
 * module it otherwise has no use for.
 *
 * <p>The ratings are a map rather than a list because every caller so far looks a skill up by id -
 * matching walks a requirement's required skills and asks what this person has. A list would make
 * each of those a scan, and the scan would sit inside a loop over candidates.
 *
 * <p>A skill the crew member holds no rating for is simply absent. Callers read that as not held,
 * which for scoring is a zero, and for a mandatory skill is an exclusion.
 *
 * @param crewMemberId           the crew-domain profile
 * @param userId                 the {@code identity} account this profile belongs to
 * @param proficiencyBySkillId   1 to 5 per rated skill; unrated skills are absent
 */
public record CrewProfile(UUID crewMemberId, UUID userId, Map<UUID, Integer> proficiencyBySkillId) {

    /** The rating for one skill, or zero when this crew member holds no rating for it. */
    public int proficiencyIn(UUID skillId) {
        return proficiencyBySkillId.getOrDefault(skillId, 0);
    }
}
