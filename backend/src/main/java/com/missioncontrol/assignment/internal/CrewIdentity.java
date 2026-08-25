package com.missioncontrol.assignment.internal;

import com.missioncontrol.crew.api.CrewDirectory;
import com.missioncontrol.identity.api.UserDirectory;
import com.missioncontrol.identity.api.UserSummary;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * This module's single point of contact with who people are.
 *
 * <p>Assignments are stored against a {@code crewMemberId}, requests arrive authenticated as a
 * {@code userId}, and responses have to print a name - which lives in a third module again. Three
 * identities for one person, and every endpoint here needs at least two of them. Putting the
 * crossing in one bean means the two directories are called the same way everywhere, rather than
 * each service growing its own slightly different pair of lookups.
 *
 * <p><strong>Always in bulk, never in a loop.</strong> {@link #namesFor} makes exactly two calls
 * whatever the size of the page - crew profiles to accounts, accounts to names - which is feature
 * 07's NFR-4. Neither published directory offers a single-id variant of those, precisely so the
 * N+1 is not the easy thing to write; the one single-id method that does exist,
 * {@code crewMemberIdOf}, answers who the caller is and cannot sit inside a loop.
 */
@Component
class CrewIdentity {

    /** Shown where a crew member exists but their account has since gone. */
    private static final String UNKNOWN_NAME = "Unknown crew member";

    private final CrewDirectory crew;
    private final UserDirectory users;

    CrewIdentity(CrewDirectory crew, UserDirectory users) {
        this.crew = crew;
        this.users = users;
    }

    /**
     * The caller's own crew profile.
     *
     * <p>Empty for a director or a mission lead, who have accounts but no crew profile. Callers
     * turn that into a 403 or an empty list rather than an error: it means the caller is not crew,
     * which is a legitimate thing to be.
     */
    Optional<UUID> crewMemberIdOf(UUID userId, UUID organisationId) {
        return crew.crewMemberIdOf(userId, organisationId);
    }

    /**
     * Whether this crew profile belongs to the caller's organisation - invariant T2 on the way in.
     *
     * <p>An offer names a crew member the caller supplied, so this is the check that stops one
     * organisation staffing its missions with another's people. It answers with a boolean rather
     * than a profile because the caller needs 404 and nothing else; returning the profile would
     * invite somebody to render a name from it and skip {@link #namesFor}.
     */
    boolean existsInOrganisation(UUID crewMemberId, UUID organisationId) {
        return crew.userIdsByCrewMemberId(List.of(crewMemberId), organisationId)
                .containsKey(crewMemberId);
    }

    /**
     * Names for a set of crew profiles, keyed by crew profile id.
     *
     * <p>Two queries regardless of how many are asked for. A crew member whose account has been
     * removed still gets a row rather than vanishing from the response - their assignment is real
     * and a missing name is a smaller problem than a place on a mission that nobody appears to
     * hold.
     */
    Map<UUID, CrewMemberRef> namesFor(Collection<UUID> crewMemberIds, UUID organisationId) {
        if (crewMemberIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, UUID> accounts = crew.userIdsByCrewMemberId(crewMemberIds, organisationId);
        Map<UUID, UserSummary> names = users.findByIds(accounts.values(), organisationId);

        Map<UUID, CrewMemberRef> refs = new HashMap<>();
        accounts.forEach((crewMemberId, userId) -> {
            UserSummary user = names.get(userId);
            refs.put(crewMemberId, new CrewMemberRef(
                    crewMemberId, user == null ? UNKNOWN_NAME : user.fullName()));
        });
        return refs;
    }

    /** One crew member's name, for the single-assignment command responses. */
    CrewMemberRef nameFor(UUID crewMemberId, UUID organisationId) {
        return namesFor(List.of(crewMemberId), organisationId)
                .getOrDefault(crewMemberId, new CrewMemberRef(crewMemberId, UNKNOWN_NAME));
    }
}
