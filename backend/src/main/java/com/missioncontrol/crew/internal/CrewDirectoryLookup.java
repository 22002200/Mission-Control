package com.missioncontrol.crew.internal;

import com.missioncontrol.crew.api.CrewDirectory;
import com.missioncontrol.crew.api.CrewProfile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The roster as other modules see it, mapped out of the entities before the transaction ends.
 *
 * <p>A separate bean from the repository rather than a second role for it, following
 * {@code SkillCatalogueLookup}: the repository speaks in entities and this speaks in published
 * records, and merging the two puts a public contract on a type whose other methods are internal.
 *
 * <p>{@code Transactional(readOnly = true)} matters more here than it looks. {@code open-in-view}
 * is off, so a lazy collection touched after this method returned would raise rather than quietly
 * issuing another query - which is the intent. Everything handed back is already materialised.
 *
 * <p>The proficiency widens from {@code short} to {@code Integer} on the way out. The narrow type
 * exists only to match a {@code SMALLINT} column; publishing it would push a persistence detail
 * into every caller's arithmetic, and matching does arithmetic with these on every candidate.
 */
@Component
class CrewDirectoryLookup implements CrewDirectory {

    private final CrewMemberRepository crewMembers;

    CrewDirectoryLookup(CrewMemberRepository crewMembers) {
        this.crewMembers = crewMembers;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CrewProfile> rosterOf(UUID organisationId) {
        return crewMembers.findRoster(organisationId).stream()
                .map(CrewDirectoryLookup::toProfile)
                .toList();
    }

    private static CrewProfile toProfile(CrewMemberEntity crewMember) {
        Map<UUID, Integer> ratings = new HashMap<>();
        // A plain loop rather than a collector: invariant C2 makes a duplicate key impossible, and
        // toMap would throw on one anyway, so the collector's merge argument would be dead code
        // guarding a state the primary key has already ruled out.
        crewMember.getSkills().forEach(
                skill -> ratings.put(skill.skillId(), (int) skill.getProficiency()));

        return new CrewProfile(crewMember.getId(), crewMember.getUserId(), Map.copyOf(ratings));
    }
}
